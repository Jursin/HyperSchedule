package top.jursin.hyperschedule.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import top.jursin.hyperschedule.data.db.AppDatabase
import top.jursin.hyperschedule.data.db.entity.CourseTable
import top.jursin.hyperschedule.data.local.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 备份格式的读写与恢复：块版本门禁、老格式兼容、只恢复设置时不动课表 */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db: AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repository = BackupRepository(context, db)

    @After
    fun tearDown() = db.close()

    @Test
    fun encodeThenParseKeepsPayload() {
        val payload = sample()

        val parsed = repository.parse(repository.encode(payload))

        assertEquals(payload, parsed.payload)
        assertTrue(parsed.hasData)
        assertTrue(parsed.hasPrefs)
    }

    /** 老备份没有块版本字段，缺省按 v1 处理 */
    @Test
    fun legacyBackupWithoutBlockVersionsIsReadable() {
        val parsed = repository.parse(LEGACY_JSON)

        assertTrue(parsed.hasData)
        assertTrue(parsed.hasPrefs)
        assertEquals("语文", parsed.payload.tables.single().courses.single().name)
        assertFalse(parsed.payload.prefs.getValue("settings").booleans.getValue("blur"))
    }

    @Test
    fun newerDataBlockOnlyKeepsPrefs() {
        val text = repository.encode(sample().copy(dataVersion = BACKUP_DATA_VERSION + 1))

        val parsed = repository.parse(text)

        assertFalse(parsed.hasData)
        assertTrue(parsed.hasPrefs)
    }

    @Test
    fun newerBlocksRejectWholeFile() {
        val text = repository.encode(sample().copy(dataVersion = BACKUP_DATA_VERSION + 1, prefsVersion = BACKUP_PREFS_VERSION + 1))

        val error = runCatching { repository.parse(text) }.exceptionOrNull()

        assertTrue(error is BackupException)
    }

    @Test
    fun foreignOrBrokenFileRejected() {
        assertTrue(runCatching { repository.parse("""{"format":"other"}""") }.exceptionOrNull() is BackupException)
        assertTrue(runCatching { repository.parse("{oops") }.exceptionOrNull() is BackupException)
    }

    /** 指纹只看内容，时间与版本号不参与 */
    @Test
    fun fingerprintIgnoresTimeAndAppVersion() {
        val payload = sample()

        assertEquals(
            repository.fingerprint(payload),
            repository.fingerprint(payload.copy(appVersion = "9.9.9", createdAt = "2030-01-01T00:00:00")),
        )
        assertNotEquals(
            repository.fingerprint(payload),
            repository.fingerprint(payload.copy(tables = listOf(payload.tables.single().copy(name = "另一张")))),
        )
    }

    /** 课表块过新时只写设置，库里原有课表与当前课表引用都不动 */
    @Test
    fun restoreWithNewerDataBlockKeepsTables() {
        runBlocking {
            db.backupDao().insertTables(listOf(CourseTable(id = "t1", name = "旧课表", createdAt = 1L)))
            val store = SettingsStore.get(context)
            store.update { it.copy(currentTableId = "t1", blur = true) }
            val prefs = mapOf("settings" to store.snapshot())
            store.update { it.copy(blur = false) }

            val parsed = repository.parse(repository.encode(sample().copy(dataVersion = BACKUP_DATA_VERSION + 1, prefs = prefs)))
            val result = repository.restore(parsed)

            assertEquals(RestoreResult(tableCount = 0, courseCount = 0), result)
            assertEquals(listOf("旧课表"), db.backupDao().tables().map { it.name })
            assertEquals("t1", store.state.currentTableId)
            assertTrue(store.state.blur)
        }
    }

    private fun sample() =
        BackupPayload(
            format = BACKUP_FORMAT,
            formatVersion = BACKUP_FORMAT_VERSION,
            dataVersion = BACKUP_DATA_VERSION,
            prefsVersion = BACKUP_PREFS_VERSION,
            appVersion = "1.0.0",
            createdAt = "2026-11-15T12:00:00",
            tables =
                listOf(
                    TableRow(
                        id = "t1",
                        name = "课表",
                        createdAt = 1L,
                        courses =
                            listOf(
                                CourseRow(id = "c1", name = "数学", day = 1, colorInt = 0xFF112233.toInt(), weeks = listOf(1, 2)),
                            ),
                    ),
                ),
        )

    private companion object {
        val LEGACY_JSON =
            """
            {
              "format": "hyperschedule-backup",
              "formatVersion": 1,
              "appVersion": "0.9.0",
              "createdAt": "2025-01-01T08:00:00",
              "tables": [
                {
                  "id": "t1",
                  "name": "旧课表",
                  "createdAt": 1,
                  "courses": [{ "id": "c1", "name": "语文", "day": 2, "colorInt": 255, "weeks": [1] }]
                }
              ],
              "prefs": { "settings": { "booleans": { "blur": false } } }
            }
            """.trimIndent()
    }
}
