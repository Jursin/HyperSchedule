package com.miuix.hyperschedule.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 学期总周数默认值（设置页可改） */
const val DEFAULT_TOTAL_WEEKS = 20

/** 学期总周数上限 */
const val MAX_TOTAL_WEEKS = 60

/** 节次数量范围 */
val SECTION_COUNT_RANGE = 6..20

/** 上午 / 下午 / 晚上 */
enum class SectionPeriod { MORNING, AFTERNOON, EVENING }

/** 默认作息：8 节（产品指定） */
val DEFAULT_SECTION_TIMES: List<Pair<String, String>> = listOf(
    "08:00" to "09:35",
    "10:05" to "11:40",
    "12:00" to "13:35",
    "14:00" to "15:35",
    "16:05" to "17:40",
    "17:45" to "18:30",
    "19:00" to "20:35",
    "20:45" to "22:20",
)

/** 开学日所在周的周一 */
fun semesterStartMonday(startDate: LocalDate): LocalDate =
    startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())

/** 今天所在教学周（不夹取：未开学 ≤0、超出总周数 >N）；用 floorDiv，开学前一天要落到第 0 周 */
fun currentWeekOf(startDate: LocalDate, today: LocalDate): Int =
    Math.floorDiv(ChronoUnit.DAYS.between(semesterStartMonday(startDate), today), 7L).toInt() + 1

/** 第 [week] 周的 7 个日期（周一~周日） */
fun weekDatesOf(startDate: LocalDate, week: Int): List<LocalDate> {
    val monday = semesterStartMonday(startDate).plusWeeks((week - 1).toLong())
    return List(7) { monday.plusDays(it.toLong()) }
}

/** 当前时间落在第几节（1..N）；按传入的作息表计算（课表作息可能被导入覆盖），不在任何节次内返回 -1 */
fun currentSectionAt(times: List<Pair<String, String>>, now: LocalTime): Int {
    val index =
        times.indexOfFirst { (start, end) ->
            val startTime = runCatching { LocalTime.parse(start) }.getOrNull()
            val endTime = runCatching { LocalTime.parse(end) }.getOrNull()
            startTime != null && endTime != null && now >= startTime && now < endTime
        }
    return if (index >= 0) index + 1 else -1
}

/** "HH:mm" → 当天第几分钟；格式不合法返回 null */
fun parseMinutes(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return hour * 60 + minute
}

/** 当天第几分钟 → "HH:mm" */
fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

fun parseDateOrNull(text: String): LocalDate? =
    runCatching { LocalDate.parse(text) }.getOrNull()

/** LocalDate → yyyy/MM/dd */
fun formatDate(date: LocalDate): String = "%04d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth)

/** 年月日任一变化后收敛到合法日期（1 月 31 日切到 2 月不会越界） */
fun adjustDate(
    date: LocalDate,
    year: Int = date.year,
    month: Int = date.monthValue,
    day: Int = date.dayOfMonth,
): LocalDate {
    val safeMonth = month.coerceIn(1, 12)
    val maxDay = YearMonth.of(year, safeMonth).lengthOfMonth()
    return LocalDate.of(year, safeMonth, day.coerceIn(1, maxDay))
}

/** 汉字与数字之间的固定间距 */
private const val DIGIT_GAP = "\u200A"

/** 只匹配汉字与数字的交界 */
private val DIGIT_BOUNDARY = Regex("(?<=[\\u4e00-\\u9fff])(?=[0-9])|(?<=[0-9])(?=[\\u4e00-\\u9fff])")

/** 汉字与数字相邻处补窄间距 */
internal fun String.cjkDigitGap(): String = replace(DIGIT_BOUNDARY, DIGIT_GAP)
