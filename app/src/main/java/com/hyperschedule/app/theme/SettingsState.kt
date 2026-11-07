package com.hyperschedule.app.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.hyperschedule.app.data.backup.BACKUP_FOLDER
import com.hyperschedule.app.data.backup.PrefsSnapshot
import com.hyperschedule.app.data.backup.S3_DEFAULT_REGION
import com.hyperschedule.app.data.backup.putAll
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.update.UpdateSource
import kotlin.math.abs
import kotlin.math.roundToInt

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
        set(state, saved?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: get(state))
    },
)

/** 壁纸模糊强度上限（dp），0 表示不模糊 */
const val MAX_WALLPAPER_BLUR = 40

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
        boolSetting("customColor", { it.customColor }) { s, v -> s.copy(customColor = v) },
        boolSetting("dynamicColor", { it.dynamicColor }) { s, v -> s.copy(dynamicColor = v) },
        boolSetting("floatingNav", { it.floatingNav }) { s, v -> s.copy(floatingNav = v) },
        intSetting("launchPage", { it.launchPage }, coerce = { it.coerceIn(0, 1) }) { s, v ->
            s.copy(launchPage = v)
        },
        intSetting("seedColor", { it.seedColor }) { s, v -> s.copy(seedColor = v) },
        boolSetting("checkUpdateOnStart", { it.checkUpdateOnStart }) { s, v -> s.copy(checkUpdateOnStart = v) },
        enumSetting("updateSource", { it.updateSource }) { s, v -> s.copy(updateSource = v) },
        boolSetting("acceptPrerelease", { it.acceptPrerelease }) { s, v -> s.copy(acceptPrerelease = v) },
        stringSetting("lastUpdateCheck", { it.lastUpdateCheck }, backedUp = false) { s, v ->
            s.copy(lastUpdateCheck = v.orEmpty())
        },
        stringSetting("githubProxyUrl", { it.githubProxyUrl }) { s, v -> s.copy(githubProxyUrl = v.orEmpty()) },
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
        // 这几项都是本机状态，不进备份
        boolSetting("backupAuto", { it.backup.autoBackup }) { s, v ->
            s.copy(backup = s.backup.copy(autoBackup = v))
        },
        intSetting(
            "backupKeep",
            { it.backup.keepCount },
            coerce = { BackupSettings.coerceKeep(it) },
            backedUp = false,
        ) { s, v -> s.copy(backup = s.backup.copy(keepCount = v)) },
        stringSetting("backupLocation", { it.backup.location }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(location = v.orEmpty()))
        },
        stringSetting("backupLocationLabel", { it.backup.locationLabel }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(locationLabel = v.orEmpty()))
        },
        stringSetting("backupLastHash", { it.backup.lastHash }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(lastHash = v.orEmpty()))
        },
        stringSetting("backupLastTime", { it.backup.lastTime }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(lastTime = v.orEmpty()))
        },
        stringSetting("backupLastError", { it.backup.lastError }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(lastError = v.orEmpty()))
        },
        // WebDAV 配置全部是本机状态，账号密码绝不能进备份
        boolSetting("webdavAuto", { it.webDav.autoBackup }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(autoBackup = v))
        },
        intSetting(
            "webdavKeep",
            { it.webDav.keepCount },
            coerce = { BackupSettings.coerceKeep(it) },
            backedUp = false,
        ) { s, v -> s.copy(webDav = s.webDav.copy(keepCount = v)) },
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
        stringSetting("webdavLastHash", { it.webDav.lastHash }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(lastHash = v.orEmpty()))
        },
        stringSetting("webdavLastTime", { it.webDav.lastTime }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(lastTime = v.orEmpty()))
        },
        stringSetting("webdavLastError", { it.webDav.lastError }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(lastError = v.orEmpty()))
        },
        // S3 配置同样是本机状态，密钥不进备份
        boolSetting("s3Auto", { it.s3.autoBackup }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(autoBackup = v))
        },
        intSetting(
            "s3Keep",
            { it.s3.keepCount },
            coerce = { BackupSettings.coerceKeep(it) },
            backedUp = false,
        ) { s, v -> s.copy(s3 = s.s3.copy(keepCount = v)) },
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
        stringSetting("s3LastHash", { it.s3.lastHash }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(lastHash = v.orEmpty()))
        },
        stringSetting("s3LastTime", { it.s3.lastTime }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(lastTime = v.orEmpty()))
        },
        stringSetting("s3LastError", { it.s3.lastError }, backedUp = false) { s, v ->
            s.copy(s3 = s.s3.copy(lastError = v.orEmpty()))
        },
    )

class SettingsStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 密码单独存一份，系统自动备份带走整个 sharedpref 时也不会泄露 */
    private val secrets = context.getSharedPreferences("secret", Context.MODE_PRIVATE)

    init {
        // 旧版本把密码写在 settings 里，搬到 secret 后清掉
        preferences.getString("webdavPassword", null)?.takeIf { it.isNotEmpty() }?.let { old ->
            if (secrets.getString("webdavPassword", null).isNullOrEmpty()) {
                secrets.edit { putString("webdavPassword", old) }
            }
            preferences.edit { remove("webdavPassword") }
        }
    }

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

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.System,
    val blur: Boolean = true,
    val wallpaper: String = "",
    val wallpaperBlur: Int = 0,
    val predictiveBack: Boolean = true,
    val customColor: Boolean = false,
    val dynamicColor: Boolean = true,
    val floatingNav: Boolean = false,
    val launchPage: Int = 0,
    val checkUpdateOnStart: Boolean = true,
    val updateSource: UpdateSource = UpdateSource.GitHub,
    val acceptPrerelease: Boolean = false,
    val lastUpdateCheck: String = "",
    val githubProxyUrl: String = "",
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val seedColor: Int = 0xFF6750A4.toInt(),
    val showNonCurrentWeek: Boolean = true,
    val currentTableId: String? = null,
    val style: ScheduleStyle = ScheduleStyle(),
    val todayStyle: TodayStyle = TodayStyle(),
    val reminder: ReminderSettings = ReminderSettings(),
    val backup: BackupSettings = BackupSettings(),
    val webDav: WebDavSettings = WebDavSettings(),
    val s3: S3Settings = S3Settings(),
)

data class BackupSettings(
    val autoBackup: Boolean = false,
    val keepCount: Int = DEFAULT_KEEP,
    val location: String = "",
    val locationLabel: String = "",
    val lastHash: String = "",
    val lastTime: String = "",
    val lastError: String = "",
) {
    val configured: Boolean get() = location.isNotBlank()

    companion object {
        const val DEFAULT_KEEP = 5

        /** 保留份数候选，下拉直接用它 */
        val KEEP_OPTIONS = listOf(3, 5, 10)

        fun coerceKeep(value: Int): Int = if (value in KEEP_OPTIONS) value else DEFAULT_KEEP

        fun keepIndexOf(value: Int): Int = KEEP_OPTIONS.indexOf(coerceKeep(value))
    }
}

data class WebDavSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    /** 相对服务器地址的目录，可填多级，留空用默认名 */
    val folder: String = BACKUP_FOLDER,
    val autoBackup: Boolean = false,
    val keepCount: Int = BackupSettings.DEFAULT_KEEP,
    val lastHash: String = "",
    val lastTime: String = "",
    val lastError: String = "",
) {
    val configured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/** 对象存储备份配置，兼容 AWS S3、Cloudflare R2、MinIO 等 */
data class S3Settings(
    val endpoint: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val bucket: String = "",
    /** 区域，R2 这类服务填 auto 即可 */
    val region: String = S3_DEFAULT_REGION,
    /** 路径风格，把桶名放在地址里而不是域名里，第三方服务多数需要 */
    val pathStyle: Boolean = true,
    /** 对象前缀，可填多级，留空用默认名 */
    val folder: String = BACKUP_FOLDER,
    val autoBackup: Boolean = false,
    val keepCount: Int = BackupSettings.DEFAULT_KEEP,
    val lastHash: String = "",
    val lastTime: String = "",
    val lastError: String = "",
) {
    val configured: Boolean
        get() = endpoint.isNotBlank() && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && bucket.isNotBlank()
}

data class ReminderSettings(
    val enabled: Boolean = false,
    val preMinutes: Int = 10,
    val inClass: Boolean = false,
    val autoDnd: Boolean = false,
    val dndMode: Int = 0,
) {
    /** ICS 导出用的提醒分钟数 */
    val icsAlarmMinutes: Int?
        get() = if (enabled) preMinutes else null

    companion object {
        val Default = ReminderSettings()

        val PRE_MINUTE_OPTIONS = (0..30 step 5).toList()

        val DND_MODE_LABELS = listOf("完全勿扰", "静音模式", "勿扰模式")

        fun preMinuteLabel(minutes: Int): String = if (minutes == 0) "准时" else "${minutes}分钟".cjkDigitGap()

        /** 把存下来的分钟数收敛到最近的档位，避免落在一个滚轮里没有的值 */
        fun snapPreMinutes(minutes: Int): Int = PRE_MINUTE_OPTIONS.minBy { abs(it - minutes) }

        /** 分钟数在 [PRE_MINUTE_OPTIONS] 里的下标，滚轮直接用它当值 */
        fun preMinuteIndexOf(minutes: Int): Int = PRE_MINUTE_OPTIONS.indexOf(snapPreMinutes(minutes))
    }
}

data class ScheduleStyle(
    val gridLines: Boolean = true,
    val timeline: Boolean = false,
    /** 网格视图一小时对应的高度 */
    val hourHeight: Int = 60,
    /** 时间轴视图一小时对应的高度 */
    val timelineHourHeight: Int = 120,
    val cardCorner: Int = 10,
    val cardPadding: Int = 5,
    val cardMargin: Int = 1,
    val cardBorder: Boolean = true,
    val contentCenter: Boolean = true,
    val textScale: Float = 1f,
    val showPosition: Boolean = true,
    val showTeacher: Boolean = true,
    val positionPrefix: Boolean = true,
    val breakDividers: Boolean = true,
) {
    companion object {
        /** 各滑块的关键点（默认值） */
        val Default = ScheduleStyle()
        /** 每小时高度，2dp 一档，两个视图各自的取值范围 */
        const val HOUR_HEIGHT_STEP = 2
        val HourHeightRange = 40..80
        val TimelineHourHeightRange = 100..140

        const val MAX_CARD_CORNER = 16

        /** 卡内边距 */
        const val MIN_CARD_PADDING = 2
        const val MAX_CARD_PADDING = 10

        /** 卡外边距 */
        const val MIN_CARD_MARGIN = 0
        const val MAX_CARD_MARGIN = 4
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 1.2f
        const val TEXT_SCALE_STEP = 0.05f

        fun snapTextScale(value: Float): Float {
            // 用整数档位换算，避免 step 累加出的浮点尾数
            val stepCount = 1f / TEXT_SCALE_STEP
            return ((value * stepCount).roundToInt() / stepCount)
                .coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        }

        /** 每小时高度吸附到档位，避免落在滑块没有的值上 */
        fun snapHourHeight(value: Int, range: IntRange): Int {
            val steps = ((value - range.first).toFloat() / HOUR_HEIGHT_STEP).roundToInt()
            return (range.first + steps * HOUR_HEIGHT_STEP).coerceIn(range.first, range.last)
        }
    }
}

data class TodayStyle(
    val showPosition: Boolean = true,
    val showTeacher: Boolean = true,
    val showCredit: Boolean = true,
    val showRemark: Boolean = true,
    val showPeriods: Boolean = true,
) {
    companion object {
        val Default = TodayStyle()
    }
}