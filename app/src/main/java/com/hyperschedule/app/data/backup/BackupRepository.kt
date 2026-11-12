package com.hyperschedule.app.data.backup

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.hyperschedule.app.data.db.AppDatabase
import com.hyperschedule.app.data.db.entity.CourseTimeBinding
import com.hyperschedule.app.data.importer.edu.EduRepoStore
import com.hyperschedule.app.data.local.SettingsStore
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.getAppVersion
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val BACKUP_EXTENSION = ".json"

/** 文件名里的时间戳格式，本地与云端共用 */
internal val backupStampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)

/** 备份文件名规则，本地与云端都只认这一种 */
internal val BACKUP_FILE_NAME = Regex("^(\\d{8}_\\d{6})\\.json$")

private const val PREFS_SETTINGS = "settings"
private const val PREFS_HOLIDAY = "holiday_settings"
private const val PREFS_EDU = "edu_repo"

/** 备份/恢复失败，message 直接给用户看 */
class BackupException(message: String) : Exception(message)

data class BackupFile(
    val name: String,
    val time: LocalDateTime,
    val size: Long,
)

/** 恢复结果 */
data class RestoreResult(
    val tableCount: Int,
    val courseCount: Int,
)

/** 网络异常转成能直接显示的提示 */
internal fun networkMessage(error: Throwable): String =
    if (error is IOException) "连接失败：${error.message ?: "网络不可用"}" else (error.message ?: "连接失败")

/** 远端备份的公共动作，WebDAV 与 S3 各按自家协议实现 */
interface RemoteBackupClient {
    /** 测试连接，返回可以直接 toast 的说明 */
    suspend fun testConnection(): String

    /** 备份路径里的备份文件，按时间从新到旧 */
    suspend fun list(): List<BackupFile>

    /** 上传一份备份，同名覆盖 */
    suspend fun upload(name: String, text: String)

    suspend fun download(name: String): String

    /** 删除一份备份，已经不在了也算成功 */
    suspend fun delete(name: String)
}

/** 备份与恢复，本地目录与 WebDAV 共用一套规则 */
class BackupRepository(
    context: Context,
    private val db: AppDatabase,
) {
    private val appContext = context.applicationContext

    /** 不写默认值与 null */
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = false
            explicitNulls = false
        }

    suspend fun build(): BackupPayload =
        withContext(Dispatchers.IO) {
            val dao = db.backupDao()
            val weeksByCourse = dao.weeks().groupBy({ it.courseId }, { it.weekNumber })
            val configsByTable = dao.configs().associateBy { it.courseTableId }
            val bindingsByTable = dao.bindings().associateBy { it.courseTableId }
            val coursesByTable = dao.courses().groupBy { it.courseTableId }
            val slotsByTable = dao.slots().groupBy { it.timeTableId }
            val specialsByTable = dao.specials().groupBy { it.timeTableId }
            val rulesByCombo = dao.comboRules().groupBy { it.comboId }
            BackupPayload(
                format = BACKUP_FORMAT,
                formatVersion = BACKUP_FORMAT_VERSION,
                appVersion = getAppVersion(appContext),
                createdAt = LocalDateTime.now().format(isoFormatter),
                tables =
                    dao.tables().map { table ->
                        TableRow.of(
                            row = table,
                            config = configsByTable[table.id],
                            binding = bindingsByTable[table.id],
                            courses =
                                coursesByTable[table.id]
                                    .orEmpty()
                                    .map { course -> CourseRow.of(course, weeksByCourse[course.id].orEmpty()) },
                        )
                    },
                timeTables =
                    dao.timeTables().map { table ->
                        TimeTableRow.of(
                            row = table,
                            slots = slotsByTable[table.id].orEmpty().map(SlotRow::of),
                            specials = specialsByTable[table.id].orEmpty().map(SpecialRow::of),
                        )
                    },
                combos = dao.combos().map { combo -> ComboRow.of(combo, rulesByCombo[combo.id].orEmpty().map(RuleRow::of)) },
                prefs = collectPrefs(),
            )
        }

    fun encode(payload: BackupPayload): String = json.encodeToString(payload)

    /** 解析并做格式门禁 */
    fun parse(text: String): BackupPayload {
        val payload =
            runCatching { json.decodeFromString<BackupPayload>(text) }
                .getOrElse { throw BackupException("不是有效的备份文件") }
        if (payload.format != BACKUP_FORMAT) throw BackupException("这不是本应用导出的备份")
        if (payload.formatVersion > BACKUP_FORMAT_VERSION) throw BackupException("备份由更新版本的应用创建，请先升级")
        if (payload.formatVersion <= 0) throw BackupException("备份文件的格式版本无效")
        return payload
    }

    /** 内容指纹，用于「内容没变就不重复备份」 */
    fun fingerprint(payload: BackupPayload): String = sha256(encode(payload.contentKey()))

    /** 由快照自身的时间生成文件名，本地与云端同名 */
    fun fileName(payload: BackupPayload): String {
        val time =
            runCatching { LocalDateTime.parse(payload.createdAt, isoFormatter) }.getOrElse { LocalDateTime.now() }
        return "${time.format(backupStampFormatter)}$BACKUP_EXTENSION"
    }

    // 本地

    /** 写一份备份并按保留份数清理，返回文件名 */
    suspend fun save(dir: BackupDir, payload: BackupPayload, keep: Int): String =
        withContext(Dispatchers.IO) {
            val retained = keep.coerceAtLeast(1)
            val name = fileName(payload)
            dir.write(name, encode(payload))
            prune(dir, retained)
            name
        }

    suspend fun list(dir: BackupDir): List<BackupFile> =
        withContext(Dispatchers.IO) {
            dir.list().mapNotNull { entry -> backupFileOf(entry.name, entry.size) }.sortedByDescending { it.time }
        }

    suspend fun hasBackup(dir: BackupDir): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { dir.list().any { BACKUP_FILE_NAME.matches(it.name) } }.getOrDefault(false)
        }

    suspend fun read(dir: BackupDir, name: String): BackupPayload =
        withContext(Dispatchers.IO) {
            val text =
                runCatching { dir.read(name) }.getOrElse { throw BackupException("读取备份失败：${it.message}") }
            parse(text)
        }

    suspend fun delete(dir: BackupDir, name: String) =
        withContext(Dispatchers.IO) {
            runCatching { dir.delete(name) }
                .getOrElse { throw BackupException("删除备份失败：${it.message ?: "无法访问存储目录"}") }
        }

    /** 换目录时搬运备份，先写新的再删旧的 */
    suspend fun migrate(from: BackupDir, to: BackupDir, keep: Int): Int =
        withContext(Dispatchers.IO) {
            if (from.location == to.location) return@withContext 0
            val files = from.list().filter { BACKUP_FILE_NAME.matches(it.name) }
            val moved =
                files.count { entry ->
                    runCatching {
                            to.write(entry.name, from.read(entry.name))
                            from.delete(entry.name)
                        }
                        .isSuccess
                }
            if (moved > 0) prune(to, keep)
            moved
        }

    // 云端

    /** 上传一份备份并用上传前的列表清理旧文件，返回文件名 */
    suspend fun saveTo(
        client: RemoteBackupClient,
        payload: BackupPayload,
        keep: Int,
        existing: List<BackupFile>,
    ): String {
        val retained = keep.coerceAtLeast(1)
        val name = fileName(payload)
        client.upload(name, encode(payload))
        // 新文件最新，旧列表留 keep-1 份就够
        val failures =
            existing
                .drop((retained - 1).coerceAtLeast(0))
                .mapNotNull { file ->
                    runCatching { client.delete(file.name) }.exceptionOrNull()?.let { file.name to it }
                }
        if (failures.isNotEmpty()) {
            throw BackupException("备份已上传，但清理旧备份失败：${failures.joinToString { it.first }}")
        }
        return name
    }

    suspend fun readFrom(client: RemoteBackupClient, name: String): BackupPayload = parse(client.download(name))

    // 恢复

    /** 覆盖式恢复，整体在一个事务里 */
    suspend fun restore(payload: BackupPayload): RestoreResult =
        withContext(Dispatchers.IO) {
            if (payload.tables.isEmpty()) throw BackupException("备份里没有课表，未做改动")
            val dao = db.backupDao()
            val tableIds = payload.tables.mapTo(HashSet()) { it.id }
            val timeTableIds = payload.timeTables.mapTo(HashSet()) { it.id }
            val comboIds = payload.combos.mapTo(HashSet()) { it.id }

            // 组合的基准作息丢了就回落到课表专属作息，规则指向不存在的作息就丢掉
            val combos =
                payload.combos.map { combo ->
                    val entity = combo.toEntity()
                    if (entity.baseTimeTableId == null || entity.baseTimeTableId in timeTableIds) {
                        entity
                    } else {
                        entity.copy(baseTimeTableId = null)
                    }
                }
            val rules =
                payload.combos.flatMap { combo ->
                    combo.rules.filter { it.targetTimeTableId in timeTableIds }.map { it.toEntity(combo.id) }
                }
            // 存不存在都不报错的绑定要自己筛，指向的目标不在备份里就跳过
            val bindings =
                payload.tables
                    .mapNotNull { table -> table.binding?.toEntity(table.id) }
                    .filter { binding ->
                        if (binding.targetType == CourseTimeBinding.TARGET_COMBO) {
                            binding.targetId in comboIds
                        } else {
                            binding.targetId in timeTableIds
                        }
                    }
            val courses = payload.tables.flatMap { table -> table.courses.map { it.toEntity(table.id) } }
            val weeks = payload.tables.flatMap { table -> table.courses.flatMap { it.toWeeks() } }

            db.withTransaction {
                // 先按「引用方 → 被引用方」清空，再反向插入，外键约束立即生效
                dao.clearComboRules()
                dao.clearCombos()
                dao.clearBindings()
                dao.clearWeeks()
                dao.clearConfigs()
                dao.clearCourses()
                dao.clearSpecials()
                dao.clearSlots()
                dao.clearTables()
                dao.clearTimeTables()

                dao.insertTables(payload.tables.map { it.toEntity() })
                dao.insertConfigs(payload.tables.map { it.toConfig() })
                dao.insertCourses(courses)
                dao.insertWeeks(weeks)
                dao.insertTimeTables(payload.timeTables.map { it.toEntity() })
                dao.insertSlots(payload.timeTables.flatMap { table -> table.slots.map { it.toEntity(table.id) } })
                dao.insertSpecials(payload.timeTables.flatMap { table -> table.specials.map { it.toEntity(table.id) } })
                dao.insertCombos(combos)
                dao.insertComboRules(rules)
                dao.insertBindings(bindings)
            }

            restorePrefs(payload.prefs, tableIds)
            RestoreResult(tableCount = payload.tableCount, courseCount = payload.courseCount)
        }

    // 设置项

    private fun collectPrefs(): Map<String, PrefsSnapshot> =
        buildMap {
            put(PREFS_SETTINGS, SettingsStore.get(appContext).snapshot())
            put(PREFS_HOLIDAY, prefsSnapshotOf(PREFS_HOLIDAY))
            put(PREFS_EDU, prefsSnapshotOf(PREFS_EDU))
        }

    private fun prefsSnapshotOf(name: String): PrefsSnapshot =
        PrefsSnapshot.of(appContext.getSharedPreferences(name, Context.MODE_PRIVATE).all)

    private fun restorePrefs(prefs: Map<String, PrefsSnapshot>, tableIds: Set<String>) {
        val store = SettingsStore.get(appContext)
        prefs[PREFS_SETTINGS]?.let(store::restore)
        writePrefsFile(PREFS_HOLIDAY, prefs[PREFS_HOLIDAY])
        writePrefsFile(PREFS_EDU, prefs[PREFS_EDU])
        HolidayRepository.get(appContext).reload()
        EduRepoStore.get(appContext).reload()

        // 当前课表指向的表不在这次备份里时清空，课表页会回退到最早一张
        if (store.state.currentTableId !in tableIds) {
            store.update { it.copy(currentTableId = null) }
        }
    }

    private fun writePrefsFile(name: String, snapshot: PrefsSnapshot?) {
        if (snapshot == null) return
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit { putAll(snapshot) }
    }

    /** 只删本应用命名的文件，目录里的其它东西一概不碰 */
    private fun prune(dir: BackupDir, keep: Int) {
        val failures =
            dir.list()
            .filter { BACKUP_FILE_NAME.matches(it.name) }
            .sortedByDescending { it.name }
            .drop(keep.coerceAtLeast(1))
            .mapNotNull { entry ->
                runCatching { dir.delete(entry.name) }.exceptionOrNull()?.let { entry.name to it }
            }
        if (failures.isNotEmpty()) {
            throw BackupException("备份已保存，但清理旧备份失败：${failures.joinToString { it.first }}")
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** 文件里记的时间，解析时也用它 */
        val isoFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
    }
}

/** 文件名转 BackupFile，不符合命名规则时返回 null */
internal fun backupFileOf(name: String, size: Long): BackupFile? {
    val stamp = BACKUP_FILE_NAME.matchEntire(name)?.groupValues?.get(1) ?: return null
    val time = runCatching { LocalDateTime.parse(stamp, backupStampFormatter) }.getOrNull() ?: return null
    return BackupFile(name = name, time = time, size = size)
}

/** 备份时间的展示格式，设置里也直接存这个字符串 */
val backupTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

/** 列表标题就是文件名去掉扩展名 */
fun BackupFile.title(): String = name.removeSuffix(BACKUP_EXTENSION)

/** 列表里显示的文件大小 */
fun BackupFile.sizeLabel(): String =
    when {
        size >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", size / 1024.0 / 1024.0)
        size >= 1024 -> String.format(Locale.ROOT, "%.1f KB", size / 1024.0)
        else -> "$size B"
    }
