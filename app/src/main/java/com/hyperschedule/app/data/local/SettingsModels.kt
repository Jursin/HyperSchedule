package com.hyperschedule.app.data.local

import com.hyperschedule.app.data.backup.BACKUP_FOLDER
import com.hyperschedule.app.data.backup.S3_DEFAULT_REGION
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.update.UpdateSource
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val wearPush: WearPushSettings = WearPushSettings(),
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
        val KEEP_OPTIONS = listOf(3, 5, 10)

        fun coerceKeep(value: Int): Int = if (value in KEEP_OPTIONS) value else DEFAULT_KEEP

        fun keepIndexOf(value: Int): Int = KEEP_OPTIONS.indexOf(coerceKeep(value))
    }
}

data class WebDavSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
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

data class S3Settings(
    val endpoint: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val bucket: String = "",
    val region: String = S3_DEFAULT_REGION,
    val pathStyle: Boolean = true,
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
    val icsAlarmMinutes: Int?
        get() = if (enabled) preMinutes else null

    companion object {
        val Default = ReminderSettings()
        val PRE_MINUTE_OPTIONS = (0..30 step 5).toList()
        val DND_MODE_LABELS = listOf("完全勿扰", "静音模式", "勿扰模式")

        fun preMinuteLabel(minutes: Int): String = if (minutes == 0) "准时" else "${minutes}分钟".cjkDigitGap()

        fun snapPreMinutes(minutes: Int): Int = PRE_MINUTE_OPTIONS.minBy { abs(it - minutes) }

        fun preMinuteIndexOf(minutes: Int): Int = PRE_MINUTE_OPTIONS.indexOf(snapPreMinutes(minutes))
    }
}

data class WearPushSettings(
    val tableId: String? = null,
    val excludeHoliday: Boolean = true,
)

data class ScheduleStyle(
    val gridLines: Boolean = true,
    val timeline: Boolean = false,
    val hourHeight: Int = 60,
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
        val Default = ScheduleStyle()
        const val HOUR_HEIGHT_STEP = 2
        val HourHeightRange = 40..80
        val TimelineHourHeightRange = 100..140
        const val MAX_CARD_CORNER = 16
        const val MIN_CARD_PADDING = 2
        const val MAX_CARD_PADDING = 10
        const val MIN_CARD_MARGIN = 0
        const val MAX_CARD_MARGIN = 4
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 1.2f
        const val TEXT_SCALE_STEP = 0.05f

        fun snapTextScale(value: Float): Float {
            val stepCount = 1f / TEXT_SCALE_STEP
            return ((value * stepCount).roundToInt() / stepCount)
                .coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        }

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

enum class ThemeMode(val title: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

enum class PaletteStyle(val title: String) {
    TonalSpot("TonalSpot"),
    Neutral("Neutral"),
    Vibrant("Vibrant"),
    Expressive("Expressive"),
}
