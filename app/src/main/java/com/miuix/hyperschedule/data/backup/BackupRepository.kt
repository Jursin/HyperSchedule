package com.miuix.hyperschedule.data.backup

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.miuix.hyperschedule.data.db.AppDatabase
import com.miuix.hyperschedule.data.importer.edu.EduRepoStore
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.getAppVersion
import com.miuix.hyperschedule.getAppVersionCode
import com.miuix.hyperschedule.theme.SettingsStore
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 备份文件扩展名 */
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

/** 目录里的一份备份 */
data class BackupFile(
    val name: String,
    val time: LocalDateTime,
    val size: Long,
)

/** 恢复结果，含被丢弃的条数 */
data class RestoreResult(
    val tableCount: Int,
    val courseCount: Int,
    val droppedRows: Int,
)

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

    /** 当前数据的完整快照 */
    suspend fun build(): BackupPayload =
        withContext(Dispatchers.IO) {
            val dao = db.backupDao()
            BackupPayload(
                format = BACKUP_FORMAT,
                formatVersion = BACKUP_FORMAT_VERSION,
                appVersion = getAppVersion(appContext),
                appVersionCode = getAppVersionCode(appContext).toLongOrNull() ?: 0L,
                createdAt = LocalDateTime.now().format(isoFormatter),
                tables = dao.tables().map(TableRow::of),
                configs = dao.configs().map(ConfigRow::of),
                courses = dao.courses().map(CourseRow::of),
                courseWeeks = dao.weeks().map(WeekRow::of),
                timeTables = dao.timeTables().map(TimeTableRow::of),
                timeSlots = dao.slots().map(SlotRow::of),
                specialBlocks = dao.specials().map(SpecialRow::of),
                bindings = dao.bindings().map(BindingRow::of),
                combos = dao.combos().map(ComboRow::of),
                comboRules = dao.comboRules().map(RuleRow::of),
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

    /** 由快照自身的时间生成文件名，本地与云端一致 */
    fun fileName(payload: BackupPayload): String {
        val time =
            runCatching { LocalDateTime.parse(payload.createdAt, isoFormatter) }.getOrElse { LocalDateTime.now() }
        return "${time.format(backupStampFormatter)}$BACKUP_EXTENSION"
    }

    // 本地

    /** 写一份备份并按保留份数清理，返回文件名 */
    suspend fun save(dir: BackupDir, payload: BackupPayload, keep: Int): String =
        withContext(Dispatchers.IO) {
            val name = fileName(payload)
            dir.write(name, encode(payload))
            prune(dir, keep)
            name
        }

    suspend fun list(dir: BackupDir): List<BackupFile> =
        withContext(Dispatchers.IO) {
            dir.list().mapNotNull { entry -> backupFileOf(entry.name, entry.size) }.sortedByDescending { it.time }
        }

    /** 目录里是否已经有备份 */
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

    fun delete(dir: BackupDir, name: String) {
        runCatching { dir.delete(name) }
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
    suspend fun saveTo(client: WebDavClient, payload: BackupPayload, keep: Int, existing: List<BackupFile>): String {
        val name = fileName(payload)
        client.upload(name, encode(payload))
        // 新文件最新，旧列表留 keep-1 份就够
        existing.drop(keep - 1).forEach { runCatching { client.delete(it.name) } }
        return name
    }

    suspend fun readFrom(client: WebDavClient, name: String): BackupPayload = parse(client.download(name))

    // 恢复

    /** 覆盖式恢复，整体在一个事务里 */
    suspend fun restore(payload: BackupPayload): RestoreResult =
        withContext(Dispatchers.IO) {
            if (payload.tables.isEmpty()) throw BackupException("备份里没有课表，未做改动")
            val dao = db.backupDao()
            val tables = payload.tables.map { it.toEntity() }
            val tableIds = tables.mapTo(HashSet()) { it.id }
            val timeTables = payload.timeTables.map { it.toEntity() }
            val timeTableIds = timeTables.mapTo(HashSet()) { it.id }
            val combos = payload.combos.map { it.toEntity() }
            val comboIds = combos.mapTo(HashSet()) { it.id }

            var dropped = 0
            fun <T> keep(rows: List<T>, predicate: (T) -> Boolean): List<T> {
                val kept = rows.filter(predicate)
                dropped += rows.size - kept.size
                return kept
            }

            val configs = keep(payload.configs) { it.courseTableId in tableIds }
            val courses = keep(payload.courses) { it.courseTableId in tableIds }
            val courseIds = courses.mapTo(HashSet()) { it.id }
            val weeks = keep(payload.courseWeeks) { it.courseId in courseIds }
            val slots = keep(payload.timeSlots) { it.timeTableId in timeTableIds }
            val specials = keep(payload.specialBlocks) { it.timeTableId in timeTableIds }
            val rules = keep(payload.comboRules) { it.comboId in comboIds && it.targetTimeTableId in timeTableIds }
            val bindings =
                keep(payload.bindings) { row ->
                    row.courseTableId in tableIds &&
                        if (row.targetType == CourseTimeBinding.TARGET_COMBO) {
                            row.targetId in comboIds
                        } else {
                            row.targetId in timeTableIds
                        }
                }
            // 基准作息被删掉的组合回落到课表专属作息，不留悬空引用
            val fixedCombos =
                combos.map { combo ->
                    if (combo.baseTimeTableId != null && combo.baseTimeTableId !in timeTableIds) {
                        combo.copy(baseTimeTableId = null)
                    } else {
                        combo
                    }
                }

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

                dao.insertTables(tables)
                dao.insertConfigs(configs.map { it.toEntity() })
                dao.insertCourses(courses.map { it.toEntity() })
                dao.insertWeeks(weeks.map { it.toEntity() })
                dao.insertTimeTables(timeTables)
                dao.insertSlots(slots.map { it.toEntity() })
                dao.insertSpecials(specials.map { it.toEntity() })
                dao.insertCombos(fixedCombos)
                dao.insertComboRules(rules.map { it.toEntity() })
                dao.insertBindings(bindings.map { it.toEntity() })
            }

            restorePrefs(payload.prefs, tableIds)
            RestoreResult(tableCount = tables.size, courseCount = courses.size, droppedRows = dropped)
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
        dir.list()
            .filter { BACKUP_FILE_NAME.matches(it.name) }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { runCatching { dir.delete(it.name) } }
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
