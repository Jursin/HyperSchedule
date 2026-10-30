package com.miuix.hyperschedule.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.miuix.hyperschedule.data.backup.PrefsSnapshot
import com.miuix.hyperschedule.data.backup.putAll
import com.miuix.hyperschedule.schedule.cjkDigitGap
import com.miuix.hyperschedule.update.UpdateSource
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
    default: Boolean,
    get: (SettingsState) -> Boolean,
    backedUp: Boolean = true,
    set: (SettingsState, Boolean) -> SettingsState,
) = Setting(
    key = key,
    backedUp = backedUp,
    write = { editor, state -> editor.putBoolean(key, get(state)) },
    read = { prefs, state -> set(state, prefs.getBoolean(key, default)) },
)

private fun intSetting(
    key: String,
    default: Int,
    get: (SettingsState) -> Int,
    coerce: (Int) -> Int = { it },
    backedUp: Boolean = true,
    set: (SettingsState, Int) -> SettingsState,
) = Setting(
    key = key,
    backedUp = backedUp,
    write = { editor, state -> editor.putInt(key, get(state)) },
    read = { prefs, state -> set(state, coerce(prefs.getInt(key, default))) },
)

private fun stringSetting(
    key: String,
    default: String?,
    get: (SettingsState) -> String?,
    backedUp: Boolean = true,
    secret: Boolean = false,
    set: (SettingsState, String?) -> SettingsState,
) = Setting(
    key = key,
    backedUp = backedUp,
    secret = secret,
    write = { editor, state -> editor.putString(key, get(state)) },
    read = { prefs, state -> set(state, prefs.getString(key, default)) },
)

private inline fun <reified T : Enum<T>> enumSetting(
    key: String,
    default: T,
    crossinline get: (SettingsState) -> T,
    crossinline set: (SettingsState, T) -> SettingsState,
) = Setting(
    key = key,
    write = { editor, state -> editor.putString(key, get(state).name) },
    read = { prefs, state ->
        val saved = prefs.getString(key, null)
        set(state, saved?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default)
    },
)

/** 全部设置项，保存与加载都按这张表走 */
private val Prefs =
    listOf(
        enumSetting("themeMode", ThemeMode.System, { it.themeMode }) { s, v -> s.copy(themeMode = v) },
        boolSetting("blur", true, { it.blur }) { s, v -> s.copy(blur = v) },
        boolSetting("predictiveBack", true, { it.predictiveBack }) { s, v -> s.copy(predictiveBack = v) },
        boolSetting("customColor", false, { it.customColor }) { s, v -> s.copy(customColor = v) },
        boolSetting("dynamicColor", true, { it.dynamicColor }) { s, v -> s.copy(dynamicColor = v) },
        boolSetting("floatingNav", false, { it.floatingNav }) { s, v -> s.copy(floatingNav = v) },
        intSetting("launchPage", 0, { it.launchPage }, coerce = { it.coerceIn(0, 1) }) { s, v ->
            s.copy(launchPage = v)
        },
        intSetting("seedColor", 0xFF6750A4.toInt(), { it.seedColor }) { s, v -> s.copy(seedColor = v) },
        boolSetting("checkUpdateOnStart", true, { it.checkUpdateOnStart }) { s, v -> s.copy(checkUpdateOnStart = v) },
        enumSetting("updateSource", UpdateSource.GitHub, { it.updateSource }) { s, v -> s.copy(updateSource = v) },
        boolSetting("acceptPrerelease", false, { it.acceptPrerelease }) { s, v -> s.copy(acceptPrerelease = v) },
        stringSetting("lastUpdateCheck", "", { it.lastUpdateCheck }, backedUp = false) { s, v ->
            s.copy(lastUpdateCheck = v.orEmpty())
        },
        stringSetting("githubProxyUrl", "", { it.githubProxyUrl }) { s, v -> s.copy(githubProxyUrl = v.orEmpty()) },
        enumSetting("paletteStyle", PaletteStyle.TonalSpot, { it.paletteStyle }) { s, v -> s.copy(paletteStyle = v) },
        boolSetting("showNonCurrentWeek", true, { it.showNonCurrentWeek }) { s, v -> s.copy(showNonCurrentWeek = v) },
        stringSetting("currentTableId", null, { it.currentTableId }) { s, v -> s.copy(currentTableId = v) },
        boolSetting("gridLines", false, { it.style.gridLines }) { s, v -> s.copy(style = s.style.copy(gridLines = v)) },
        intSetting("cardHeight", ScheduleStyle.Default.cardHeight, { it.style.cardHeight }) { s, v ->
            s.copy(style = s.style.copy(cardHeight = v))
        },
        intSetting("cardCorner", ScheduleStyle.Default.cardCorner, { it.style.cardCorner }) { s, v ->
            s.copy(style = s.style.copy(cardCorner = v))
        },
        boolSetting("cardBorder", false, { it.style.cardBorder }) { s, v -> s.copy(style = s.style.copy(cardBorder = v)) },
        boolSetting("contentCenter", true, { it.style.contentCenter }) { s, v ->
            s.copy(style = s.style.copy(contentCenter = v))
        },
        Setting(
            key = "cardTextScale",
            write = { editor, state -> editor.putFloat("cardTextScale", state.style.textScale) },
            read = { prefs, state ->
                val scale = ScheduleStyle.snapTextScale(prefs.getFloat("cardTextScale", 1f))
                state.copy(style = state.style.copy(textScale = scale))
            },
        ),
        boolSetting("showPosition", true, { it.style.showPosition }) { s, v ->
            s.copy(style = s.style.copy(showPosition = v))
        },
        boolSetting("showTeacher", true, { it.style.showTeacher }) { s, v ->
            s.copy(style = s.style.copy(showTeacher = v))
        },
        boolSetting("positionPrefix", true, { it.style.positionPrefix }) { s, v ->
            s.copy(style = s.style.copy(positionPrefix = v))
        },
        boolSetting("breakDividers", true, { it.style.breakDividers }) { s, v ->
            s.copy(style = s.style.copy(breakDividers = v))
        },
        boolSetting("todayShowSection", true, { it.todayStyle.showSection }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showSection = v))
        },
        boolSetting("todayShowPosition", true, { it.todayStyle.showPosition }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showPosition = v))
        },
        boolSetting("todayShowTeacher", true, { it.todayStyle.showTeacher }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showTeacher = v))
        },
        boolSetting("todayShowCredit", true, { it.todayStyle.showCredit }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showCredit = v))
        },
        boolSetting("todayShowRemark", true, { it.todayStyle.showRemark }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showRemark = v))
        },
        boolSetting("todayShowPeriods", true, { it.todayStyle.showPeriods }) { s, v ->
            s.copy(todayStyle = s.todayStyle.copy(showPeriods = v))
        },
        boolSetting("reminderEnabled", false, { it.reminder.enabled }) { s, v ->
            s.copy(reminder = s.reminder.copy(enabled = v))
        },
        intSetting(
            "reminderPreMinutes",
            ReminderSettings.Default.preMinutes,
            { it.reminder.preMinutes },
            coerce = { ReminderSettings.snapPreMinutes(it) },
        ) { s, v -> s.copy(reminder = s.reminder.copy(preMinutes = v)) },
        boolSetting("reminderInClass", false, { it.reminder.inClass }) { s, v ->
            s.copy(reminder = s.reminder.copy(inClass = v))
        },
        boolSetting("reminderAutoDnd", false, { it.reminder.autoDnd }) { s, v ->
            s.copy(reminder = s.reminder.copy(autoDnd = v))
        },
        intSetting(
            "reminderDndMode",
            ReminderSettings.Default.dndMode,
            { it.reminder.dndMode },
            coerce = { it.coerceIn(ReminderSettings.DND_MODE_LABELS.indices) },
        ) { s, v -> s.copy(reminder = s.reminder.copy(dndMode = v)) },
        // 这几项都是本机状态，不进备份
        boolSetting("backupAuto", false, { it.backup.autoBackup }) { s, v ->
            s.copy(backup = s.backup.copy(autoBackup = v))
        },
        intSetting(
            "backupKeep",
            BackupSettings.Default.keepCount,
            { it.backup.keepCount },
            coerce = { BackupSettings.coerceKeep(it) },
            backedUp = false,
        ) { s, v -> s.copy(backup = s.backup.copy(keepCount = v)) },
        stringSetting("backupLocation", "", { it.backup.location }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(location = v.orEmpty()))
        },
        stringSetting("backupLocationLabel", "", { it.backup.locationLabel }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(locationLabel = v.orEmpty()))
        },
        stringSetting("backupLastHash", "", { it.backup.lastHash }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(lastHash = v.orEmpty()))
        },
        stringSetting("backupLastTime", "", { it.backup.lastTime }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(lastTime = v.orEmpty()))
        },
        stringSetting("backupLastError", "", { it.backup.lastError }, backedUp = false) { s, v ->
            s.copy(backup = s.backup.copy(lastError = v.orEmpty()))
        },
        // WebDAV 配置全部是本机状态，账号密码绝不能进备份
        boolSetting("webdavAuto", false, { it.webDav.autoBackup }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(autoBackup = v))
        },
        intSetting(
            "webdavKeep",
            WebDavSettings.DEFAULT_KEEP,
            { it.webDav.keepCount },
            coerce = { WebDavSettings.coerceKeep(it) },
            backedUp = false,
        ) { s, v -> s.copy(webDav = s.webDav.copy(keepCount = v)) },
        stringSetting("webdavServer", "", { it.webDav.serverUrl }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(serverUrl = v.orEmpty()))
        },
        stringSetting("webdavUser", "", { it.webDav.username }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(username = v.orEmpty()))
        },
        stringSetting("webdavPassword", "", { it.webDav.password }, backedUp = false, secret = true) { s, v ->
            s.copy(webDav = s.webDav.copy(password = v.orEmpty()))
        },
        stringSetting("webdavLastHash", "", { it.webDav.lastHash }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(lastHash = v.orEmpty()))
        },
        stringSetting("webdavLastTime", "", { it.webDav.lastTime }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(lastTime = v.orEmpty()))
        },
        stringSetting("webdavLastError", "", { it.webDav.lastError }, backedUp = false) { s, v ->
            s.copy(webDav = s.webDav.copy(lastError = v.orEmpty()))
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

    /** 只改内存状态，用于滑块拖动等高频改动；手势结束时再调用 [save] 落盘 */
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

    /** 恢复设置；快照里没有的键保持当前值 */
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

/** 设置项；默认值同时作为读写缺失时的兜底 */
data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.System,
    val blur: Boolean = true,
    val predictiveBack: Boolean = true,
    val customColor: Boolean = false,
    val dynamicColor: Boolean = true,
    val floatingNav: Boolean = false,
    val launchPage: Int = 0,
    val checkUpdateOnStart: Boolean = true,
    val updateSource: UpdateSource = UpdateSource.GitHub,
    val acceptPrerelease: Boolean = false,
    val lastUpdateCheck: String = "", // 上次静默检查更新的日期，用于每日节流
    val githubProxyUrl: String = "",
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val seedColor: Int = 0xFF6750A4.toInt(),
    val showNonCurrentWeek: Boolean = true, // 课表里是否显示本周不上（灰显）的课
    val currentTableId: String? = null, // 当前使用的课表，空或失效时回退到最早创建的一张
    val style: ScheduleStyle = ScheduleStyle(), // 课程表外观
    val todayStyle: TodayStyle = TodayStyle(), // 今日课表外观
    val reminder: ReminderSettings = ReminderSettings(), // 课程提醒
    val backup: BackupSettings = BackupSettings(), // 备份与恢复
    val webDav: WebDavSettings = WebDavSettings(), // WebDAV 云端备份
)

/** 备份设置，只有 [autoBackup] 跟着备份走，其余都是本机状态 */
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

        val Default = BackupSettings()

        /** 保留份数候选，下拉直接用它 */
        val KEEP_OPTIONS = listOf(3, 5, 10)

        fun coerceKeep(value: Int): Int = if (value in KEEP_OPTIONS) value else DEFAULT_KEEP

        fun keepIndexOf(value: Int): Int = KEEP_OPTIONS.indexOf(coerceKeep(value))
    }
}

/** WebDAV 备份设置，全部是本机状态，账号密码绝不进备份文件 */
data class WebDavSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val autoBackup: Boolean = false,
    val keepCount: Int = DEFAULT_KEEP,
    val lastHash: String = "",
    val lastTime: String = "",
    val lastError: String = "",
) {
    /** 账号信息填全了才有得谈连接 */
    val configured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    companion object {
        const val DEFAULT_KEEP = 5

        val Default = WebDavSettings()

        fun coerceKeep(value: Int): Int = BackupSettings.coerceKeep(value)

        fun keepIndexOf(value: Int): Int = BackupSettings.keepIndexOf(value)
    }
}

/** 课程提醒设置 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val preMinutes: Int = 10,
    val inClass: Boolean = false,
    val autoDnd: Boolean = false,
    val dndMode: Int = 0,
) {
    /** ICS 导出用的提醒分钟数：总开关关闭时不写提醒，0 表示准时 */
    val icsAlarmMinutes: Int?
        get() = if (enabled) preMinutes else null

    companion object {
        val Default = ReminderSettings()

        /** 课前提醒档位，5 分钟一跳；0 表示准时 */
        val PRE_MINUTE_OPTIONS = (0..30 step 5).toList()

        /** 自动勿扰的档位，下标即 [dndMode] */
        val DND_MODE_LABELS = listOf("完全勿扰", "静音模式", "勿扰模式")

        fun preMinuteLabel(minutes: Int): String = if (minutes == 0) "准时" else "${minutes}分钟".cjkDigitGap()

        /** 把存下来的分钟数收敛到最近的档位，避免落在一个滚轮里没有的值 */
        fun snapPreMinutes(minutes: Int): Int = PRE_MINUTE_OPTIONS.minBy { abs(it - minutes) }

        /** 分钟数在 [PRE_MINUTE_OPTIONS] 里的下标，滚轮直接用它当值 */
        fun preMinuteIndexOf(minutes: Int): Int = PRE_MINUTE_OPTIONS.indexOf(snapPreMinutes(minutes))
    }
}

/** 课程表外观：抽屉里能改的都在这里，改动即时生效 */
data class ScheduleStyle(
    val gridLines: Boolean = false,
    val cardHeight: Int = 92,
    val cardCorner: Int = 10,
    val cardBorder: Boolean = false,
    val contentCenter: Boolean = true,
    val textScale: Float = 1f,
    val showPosition: Boolean = true,
    val showTeacher: Boolean = true,
    val positionPrefix: Boolean = true,
    val breakDividers: Boolean = true,
) {
    companion object {
        /** 各滑块的关键点（默认值）就取这里 */
        val Default = ScheduleStyle()
        const val MIN_CARD_HEIGHT = 62
        const val MAX_CARD_HEIGHT = 100
        const val MAX_CARD_CORNER = 16
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 1.2f
        const val TEXT_SCALE_STEP = 0.05f

        fun snapTextScale(value: Float): Float {
            // 用整数档位换算，避免 step 累加出的浮点尾数
            val stepCount = 1f / TEXT_SCALE_STEP
            return ((value * stepCount).roundToInt() / stepCount)
                .coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        }
    }
}

/** 今日课表外观：课程卡信息行里显示哪些字段，改动即时生效 */
data class TodayStyle(
    val showSection: Boolean = true,
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