package com.miuix.hyperschedule.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 学期总周数默认值（设置页可改） */
const val DEFAULT_TOTAL_WEEKS = 20

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

private val SectionTimes = DEFAULT_SECTION_TIMES.map { (start, end) ->
    LocalTime.parse(start) to LocalTime.parse(end)
}

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

/** 当前时间落在第几节（1..N）；不在任何一节课内返回 -1 */
fun currentSectionAt(now: LocalTime): Int {
    val index = SectionTimes.indexOfFirst { (start, end) -> now in start..<end }
    return if (index >= 0) index + 1 else -1
}

fun parseDateOrNull(text: String): LocalDate? =
    runCatching { LocalDate.parse(text) }.getOrNull()
