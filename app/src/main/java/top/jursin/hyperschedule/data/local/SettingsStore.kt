package top.jursin.hyperschedule.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import top.jursin.hyperschedule.data.backup.PrefsSnapshot
import top.jursin.hyperschedule.data.backup.putAll

/** 一条设置，[backedUp] 为 false 的键是本机状态，不进备份 */
private class Setting(
    val key: String,
    val backedUp: Boolean = true,
    val secret: Boolean = false,
    val write: (SharedPreferences.Editor, SettingsState) -> Unit,
    val read: (SharedPreferences, SettingsState) -> SettingsState,
)

private fun boolSetting(
    key: String,
    get: (SettingsState) -> Boolean,
    backedUp: Boolean = true,
    set: (SettingsState, Boolean) -> SettingsState,
) = Setting(
    key = key,
    backedUp = backedUp,
    write = { editor, state -> editor.putBoolean(key, get(state)) },
    // 缺键时回落到 data class 里的默认值
    read = { prefs, state -> set(state, prefs.getBoolean(key, get(state))) },
)

private fun intSetting(
    key: String,
    get: (SettingsState) -> Int,
    coerce: (Int) -> Int = { it },
    backedUp: Boolean = true,
    set: (SettingsState, Int) -> SettingsState,
) = Setting(
    key = key,
    backedUp = backedUp,
    write = { editor, state -> editor.putInt(key, get(state)) },
    read = { prefs, state -> set(state, coerce(prefs.getInt(key, get(state)))) },
)

private fun stringSetting(
    key: String,
    get: (SettingsState) -> String?,
    backedUp: Boolean = true,
    secret: Boolean = false,
    set: (SettingsState, String?) -> SettingsState,
) = Setting(
    key = key,
    backedUp = backedUp,
    secret = secret,
    write = { editor, state -> editor.putString(key, get(state)) },
    read = { prefs, state -> set(state, prefs.getString(key, get(state))) },
)

private inline fun <reified T : Enum<T>> enumSetting(
    key: String,
    crossinline get: (SettingsState) -> T,
    crossinline set: (SettingsState, T) -> SettingsState,
) = Setting(
    key = key,
    write = { editor, state -> editor.putString(key, get(state).name) },
    read = { prefs, state ->
        val saved = prefs.getString(key, null)
        val value = enumValues<T>().firstOrNull { it.name.equals(saved, ignoreCase = true) } ?: get(state)
        if (saved != null && saved != value.name) prefs.edit { putString(key, value.name) }
        set(state, value)
    },
)

private const val UPDATE_MIRRORS_KEY = "updateMirrors"

/** 自定义镜像域名按行拼接成一个键存，域名里不会出现换行 */
private fun updateMirrorsSetting(
    get: (SettingsState) -> List<String>,
    set: (SettingsState, List<String>) -> SettingsState,
) = Setting(
    key = UPDATE_MIRRORS_KEY,
    write = { editor, state -> editor.putString(UPDATE_MIRRORS_KEY, get(state).joinToString("\n")) },
    read = { prefs, state ->
        prefs.getString(UPDATE_MIRRORS_KEY, null)?.let { saved ->
            set(state, saved.split('\n').filter { it.isNotBlank() })
        } ?: state
    },
)

/** 壁纸模糊强度上限（dp），0 表示不模糊 */
const val MAX_WALLPAPER_BLUR = 40

/** 三个备份位置各占同样 5 个键，只有自动备份开关会进备份文件 */
private fun stampSettings(
    prefix: String,
    get: (SettingsState) -> BackupStamp,
    autoBackedUp: Boolean = false,
    set: (SettingsState, BackupStamp) -> SettingsState,
): List<Setting> {
    fun write(state: SettingsState, transform: (BackupStamp) -> BackupStamp): SettingsState =
        set(state, transform(get(state)))
    return listOf(
        boolSetting("${prefix}Auto", { get(it).autoBackup }, backedUp = autoBackedUp) { state, value ->
            write(state) { it.copy(autoBackup = value) }
        },
        intSetting(
            "${prefix}Keep",
            { get(it).keepCount },
            coerce = BackupSettings::coerceKeep,
            backedUp = false,
        ) { state, value -> write(state) { it.copy(keepCount = value) } },
        stringSetting("${prefix}LastHash", { get(it).lastHash }, backedUp = false) { state, value ->
            write(state) { it.copy(lastHash = value.orEmpty()) }
        },
        stringSetting("${prefix}LastTime", { get(it).lastTime }, backedUp = false) { state, value ->
            write(state) { it.copy(lastTime = value.orEmpty()) }
        },
        stringSetting("${prefix}LastError", { get(it).lastError }, backedUp = false) { state, value ->
            write(state) { it.copy(lastError = value.orEmpty()) }
        },
    )
}

/** 壁纸模糊强度的档位间隔 */
const val WALLPAPER_BLUR_STEP = 4

/** 全部设置项 */
private val Prefs =
    listOf(
        enumSetting("themeMode", { it.themeMode }) { s, v -> s.copy(themeMode = v) },
        boolSetting("blur", { it.blur }) { s, v -> s.copy(blur = v) },
        // 壁纸存在应用私有目录，路径与模糊强度都是本机状态
        stringSetting("wallpaper", { it.wallpaper }, backedUp = false) { s, v -> s.copy(wallpaper = v.orEmpty()) },
        intSetting(
            key = "wallpaperBlur",
            get = { it.wallpaperBlur },
            coerce = { it.coerceIn(0, MAX_WALLPAPER_BLUR) },
            backedUp = false,
        ) { s, v -> s.copy(wallpaperBlur = v) },
        boolSetting("predictiveBack", { it.predictiveBack }) { s, v -> s.copy(predictiveBack = v) },
        boolSetting("useMonet", { it.useMonet }) { s, v -> s.copy(useMonet = v) },
        boolSetting("pureBlack", { it.pureBlack }) { s, v -> s.copy(pureBlack = v) },
        intSetting("launchPage", { it.launchPage }, coerce = { it.coerceIn(0, 1) }) { s, v ->
            s.copy(launchPage = v)
        },
        enumSetting("accentColor", { it.accentColor }) { s, v -> s.copy(accentColor = v) },
        boolSetting("checkUpdateOnStart", { it.checkUpdateOnStart }) { s, v -> s.copy(checkUpdateOnStart = v) },
        boolSetting("acceptPrerelease", { it.acceptPrerelease }) { s, v -> s.copy(acceptPrerelease = v) },
        stringSetting("lastUpdateCheck", { it.lastUpdateCheck }, backedUp = false) { s, v ->
            s.copy(lastUpdateCheck = v.orEmpty())
        },
        stringSetting("updateSource", { it.updateSource }) { s, v -> s.copy(updateSource = v.orEmpty()) },
        updateMirrorsSetting({ it.updateMirrors }) { s, v -> s.copy(updateMirrors = v) },
        enumSetting("paletteStyle", { it.paletteStyle }) { s, v -> s.copy(paletteStyle = v) },
        boolSetting("showNonCurrentWeek", { it.showNonCurrentWeek }) { s, v -> s.copy(showNonCurrentWeek = v) },
        stringSetting("currentTableId", { it.currentTableId }) { s, v -> s.copy(currentTableId = v) },
        boolSetting("gridLines", { it.style.gridLines }) { s, v -> s.copy(style = s.style.copy(gridLines = v)) },
        boolSetting("timeline", { it.style.timeline }) { s, v -> s.copy(style = s.style.copy(timeline = v)) },
        intSetting(
            key = "hourHeight",
            get = { it.style.hourHeight },
            coerce = { ScheduleStyle.snapHourHeight(it, ScheduleStyle.HourHeightRange) },
        ) { s, v -> s.copy(style = s.style.copy(hourHeight = v)) },
        intSetting(
            key = "timelineHourHeight",
            get = { it.style.timelineHourHeight },
            coerce = { ScheduleStyle.snapHourHeight(it, ScheduleStyle.TimelineHourHeightRange) },
        ) { s, v -> s.copy(style = s.style.copy(timelineHourHeight = v)) },
        intSetting("cardCorner", { it.style.cardCorner }) { s, v -> s.copy(style = s.style.copy(cardCorner = v)) },
        intSetting(
            key = "cardPadding",
            get = { it.style.cardPadding },
            coerce = { it.coerceIn(ScheduleStyle.MIN_CARD_PADDING, ScheduleStyle.MAX_CARD_PADDING) },
        ) { s, v -> s.copy(style = s.style.copy(cardPadding = v)) },
        intSetting(
            key = "cardMargin",
            get = { it.style.cardMargin },
            coerce = { it.coerceIn(ScheduleStyle.MIN_CARD_MARGIN, ScheduleStyle.MAX_CARD_MARGIN) },
        ) { s, v -> s.copy(style = s.style.copy(cardMargin = v)) },
        boolSetting("cardBorder", { it.style.cardBorder }) { s, v -> s.copy(style = s.style.copy(cardBorder = v)) },
        boolSetting("contentCenter", { it.style.contentCenter }) { s, v ->
            s.copy(style = s.style.copy(contentCenter = v))
        },
        Setting(
            key = "cardTextScale",
            write = { editor, state -> editor.putFloat("cardTextScale", state.style.textScale) },
            read = { prefs, state ->
                val scale = ScheduleStyle.snapTextScale(prefs.getFloat("cardTextScale", state.style.textScale))
                state.copy(style = state.style.copy(textScale = scale))
            },
        ),
        boolSetting("showPosition", { it.style.showPosition }) { s, v ->
            s.copy(style = s.style.copy(showPosition = v))
        },
        boolSetting("showTeacher", { it.style.showTeacher }) { s, v ->
            s.copy(style = s.style.copy(showTeacher = v))
        },
        boolSetting("positionPrefix", { it.style.positionPrefix }) { s, v ->
            s.copy(style = s.style.copy(positionPrefix = v))
        },
        boolSetting("breakDividers", { it.style.breakDividers }) { s, v ->
            s.copy(style = s.style.copy(breakDividers = v))
        },
        boolSetting("todayShowPosition", { it.todayStyle.showPosition }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showPosition = v))
        },
        boolSetting("todayShowTeacher", { it.todayStyle.showTeacher }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showTeacher = v))
        },
        boolSetting("todayShowCredit", { it.todayStyle.showCredit }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showCredit = v))
        },
        boolSetting("todayShowRemark", { it.todayStyle.showRemark }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showRemark = v))
        },
        boolSetting("todayShowPeriods", { it.todayStyle.showPeriods }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showPeriods = v))
        },
        boolSetting("reminderEnabled", { it.reminder.enabled }) { s, v ->
            s.copy(reminder = s.reminder.copy(enabled = v))
        },
        intSetting(
            "reminderPreMinutes",
            { it.reminder.preMinutes },
            coerce = { ReminderSettings.snapPreMinutes(it) },
        ) { s, v -> s.copy(reminder = s.reminder.copy(preMinutes = v)) },
        boolSetting("reminderInClass", { it.reminder.inClass }) { s, v ->
            s.copy(reminder = s.reminder.copy(inClass = v))
        },
        boolSetting("reminderAutoDnd", { it.reminder.autoDnd }) { s, v ->
            s.copy(reminder = s.reminder.copy(autoDnd = v))
        },
        intSetting(
            "reminderDndMode",
            { it.reminder.dndMode },
            coerce = { it.coerceIn(ReminderSettings.DND_MODE_LABELS.indices) },
        ) { s, v -> s.copy(reminder = s.reminder.copy(dndMode = v)) },
        stringSetting("wearPushTableId", { it.wearPush.tableId }) { s, v ->
            s.copy(wearPush = s.wearPush.copy(tableId = v))
        },
        boolSetting("wearPushExcludeHoliday", { it.wearPush.excludeHoliday }) { s, v ->
            s.copy(wearPush = s.wearPush.copy(excludeHoliday = v))
        },
        enumSetting("logLevel", { it.logLevel }) { s, v -> s.copy(logLevel = v) },
        intSetting("logKeep", { it.logKeep }, coerce = LogKeep::coerce) { s, v -> s.copy(logKeep = v) },
        // 这几项都是本机状态，不进备份
        stringSetting("backupLocation", { it.backup.location }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(location = v.orEmpty()))
        },
        stringSetting("backupLocationLabel", { it.backup.locationLabel }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(locationLabel = v.orEmpty()))
        },
        // 账号密码与密钥绝不能进备份
        stringSetting("webdavServer", { it.webDav.serverUrl }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(serverUrl = v.orEmpty()))
        },
        stringSetting("webdavUser", { it.webDav.username }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(username = v.orEmpty()))
        },
        stringSetting("webdavPassword", { it.webDav.password }, backedUp = false, secret = true) { s, v ->
            s.copy(webDav = s.webDav.copy(password = v.orEmpty()))
        },
        stringSetting("webdavFolder", { it.webDav.folder }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(folder = v.orEmpty()))
        },
        stringSetting("s3Endpoint", { it.s3.endpoint }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(endpoint = v.orEmpty()))
        },
        stringSetting("s3AccessKey", { it.s3.accessKeyId }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(accessKeyId = v.orEmpty()))
        },
        stringSetting("s3SecretKey", { it.s3.secretAccessKey }, backedUp = false, secret = true) { s, v ->
            s.copy(s3 = s.s3.copy(secretAccessKey = v.orEmpty()))
        },
        stringSetting("s3Bucket", { it.s3.bucket }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(bucket = v.orEmpty()))
        },
        stringSetting("s3Region", { it.s3.region }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(region = v.orEmpty()))
        },
        boolSetting("s3PathStyle", { it.s3.pathStyle }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(pathStyle = v))
        },
        stringSetting("s3Folder", { it.s3.folder }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(folder = v.orEmpty()))
        },
    ) +
        stampSettings("backup", { it.backup.stamp }, autoBackedUp = true) { s, v ->
            s.copy(backup = s.backup.copy(stamp = v))
        } +
        stampSettings("webdav", { it.webDav.stamp }) { s, v -> s.copy(webDav = s.webDav.copy(stamp = v)) } +
        stampSettings("s3", { it.s3.stamp }) { s, v -> s.copy(s3 = s.s3.copy(stamp = v)) }

class SettingsStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 密码单独存一份，系统自动备份带走整个 sharedpref 时也不会泄露 */
    private val secrets = context.getSharedPreferences("secret", Context.MODE_PRIVATE)

    var state by mutableStateOf(load())
        private set

    fun update(transform: (SettingsState) -> SettingsState) {
        preview(transform)
        save()
    }

    /** 只改内存状态，用于滑块拖动等高频改动，手势结束时再调用 [save] 落盘 */
    fun preview(transform: (SettingsState) -> SettingsState) {
        state = transform(state)
    }

    /** 把当前状态整体写入 SharedPreferences */
    fun save() {
        preferences.edit { Prefs.forEach { if (!it.secret) it.write(this, state) } }
        secrets.edit { Prefs.forEach { if (it.secret) it.write(this, state) } }
    }

    /** 从磁盘重读，恢复备份后调用 */
    fun reload() {
        state = load()
        AppLog.onSettingsChanged()
    }

    /** 导出可备份的设置项，排除本机状态键 */
    fun snapshot(): PrefsSnapshot = PrefsSnapshot.of(preferences.all, backedUpKeys)

    /** 恢复设置，快照里没有的键保持当前值 */
    fun restore(snapshot: PrefsSnapshot) {
        preferences.edit { putAll(snapshot) }
        reload()
    }

    private fun load(): SettingsState =
        Prefs.fold(SettingsState()) { state, setting -> setting.read(if (setting.secret) secrets else preferences, state) }

    companion object {
        /** 允许进备份的键 */
        private val backedUpKeys: Set<String> = Prefs.filter { it.backedUp }.map { it.key }.toSet()

        @Volatile private var instance: SettingsStore? = null

        /** 进程级单例，重复构造会重复解析整张表 */
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
