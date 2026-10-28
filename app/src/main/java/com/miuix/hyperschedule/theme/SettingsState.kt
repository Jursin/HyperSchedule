package com.miuix.hyperschedule.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.miuix.hyperschedule.schedule.cjkDigitGap
import kotlin.math.abs
import kotlin.math.roundToInt

/** 一条设置：读写各自知道键与默认值，[Prefs] 是唯一的键清单 */
private class Setting(
    val write: (SharedPreferences.Editor, SettingsState) -> Unit,
    val read: (SharedPreferences, SettingsState) -> SettingsState,
)

private fun boolSetting(
    key: String,
    default: Boolean,
    get: (SettingsState) -> Boolean,
    set: (SettingsState, Boolean) -> SettingsState,
) = Setting(
    write = { editor, state -> editor.putBoolean(key, get(state)) },
    read = { prefs, state -> set(state, prefs.getBoolean(key, default)) },
)

private fun intSetting(
    key: String,
    default: Int,
    get: (SettingsState) -> Int,
    coerce: (Int) -> Int = { it },
    set: (SettingsState, Int) -> SettingsState,
) = Setting(
    write = { editor, state -> editor.putInt(key, get(state)) },
    read = { prefs, state -> set(state, coerce(prefs.getInt(key, default))) },
)

private fun stringSetting(
    key: String,
    default: String?,
    get: (SettingsState) -> String?,
    set: (SettingsState, String?) -> SettingsState,
) = Setting(
    write = { editor, state -> editor.putString(key, get(state)) },
    read = { prefs, state -> set(state, prefs.getString(key, default)) },
)

private inline fun <reified T : Enum<T>> enumSetting(
    key: String,
    default: T,
    crossinline get: (SettingsState) -> T,
    crossinline set: (SettingsState, T) -> SettingsState,
) = Setting(
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
    )

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

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
        preferences.edit { Prefs.forEach { it.write(this, state) } }
    }

    private fun load(): SettingsState =
        Prefs.fold(SettingsState()) { state, setting -> setting.read(preferences, state) }
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
    val githubProxyUrl: String = "",
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val seedColor: Int = 0xFF6750A4.toInt(),
    val showNonCurrentWeek: Boolean = true, // 课表里是否显示本周不上（灰显）的课
    val currentTableId: String? = null, // 当前使用的课表，空或失效时回退到最早创建的一张
    val style: ScheduleStyle = ScheduleStyle(), // 课程表外观
    val todayStyle: TodayStyle = TodayStyle(), // 今日课表外观
    val reminder: ReminderSettings = ReminderSettings(), // 课程提醒
)

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