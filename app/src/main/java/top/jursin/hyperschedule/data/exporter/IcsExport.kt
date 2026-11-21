package top.jursin.hyperschedule.data.exporter

import top.jursin.hyperschedule.data.db.entity.Course
import top.jursin.hyperschedule.data.db.entity.CourseWithWeeks
import top.jursin.hyperschedule.data.db.entity.TimeSlot
import top.jursin.hyperschedule.data.model.HolidayEntry
import top.jursin.hyperschedule.data.repository.HolidayRepository
import top.jursin.hyperschedule.schedule.HolidayPlan
import top.jursin.hyperschedule.schedule.parseMinutes
import top.jursin.hyperschedule.schedule.weekDatesOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val IcsTimeZone = "Asia/Shanghai"
private val IcsLocalFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
private val IcsStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
private val IcsDayFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

internal data class ExportEvent(
    val uid: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val summary: String,
    val location: String,
    val description: String,
)

/** 逐周展开成日记事，放假不写，同一天按实际上课时刻排 */
internal fun TableExportData.expandEvents(entries: List<HolidayEntry>): List<ExportEvent> {
    val start = startDate ?: error("请先设置开学日期")
    val weeks = lastWeek
    val plan = HolidayPlan.build(start, weeks, entries)
    val coursesByCell = HashMap<Int, MutableList<CourseWithWeeks>>()
    courses.forEach { group ->
        group.weeks.forEach { courseWeek ->
            coursesByCell.getOrPut(courseWeek.weekNumber * 10 + group.course.day) { mutableListOf() } += group
        }
    }
    val events = ArrayList<ExportEvent>()
    for (week in 1..weeks) {
        val dates = weekDatesOf(start, week)
        for (day in 1..7) {
            val cell = plan.of(week, day)
            if (cell.holiday) continue
            val date = dates[day - 1]
            val slots = slotsFor(date).associateBy { it.number }
            // 补课列画的是被调那一天的课，跨周调课时连周次一起换
            coursesByCell[cell.weekOr(week) * 10 + cell.dayOr(day)].orEmpty()
                .mapNotNull { group -> group.course.timeSpanOn(date, slots)?.let { group.course to it } }
                .sortedBy { it.second.first }
                .forEach { (course, span) ->
                    events +=
                        ExportEvent(
                            uid = "${course.id}-$week-${IcsDayFormatter.format(date)}@hyperschedule",
                            start = span.first,
                            end = span.second,
                            summary = course.name,
                            location = course.position,
                            description = describe(week, course),
                        )
                }
        }
    }
    return events
}

/** 查完区间内的假期再展开，ICS 导出与日历同步共用 */
internal suspend fun TableExportData.loadEvents(holidays: HolidayRepository): List<ExportEvent> {
    val (start, end) = holidayRange() ?: error("请先设置开学日期")
    return expandEvents(holidays.entriesIn(start, end))
}

/** 展开后的日程拼成 VEVENT，[alarmMinutes] 为空不加提醒 */
internal fun buildIcs(events: List<ExportEvent>, alarmMinutes: Int?): String {
    val stamp = IcsStampFormatter.format(ZonedDateTime.now(ZoneId.of("UTC")))
    return buildString {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("PRODID:-//HyperSchedule//Course Schedule//CN\r\n")
        append("CALSCALE:GREGORIAN\r\n")
        append("METHOD:PUBLISH\r\n")
        appendTimeZone()
        events.forEach { appendEvent(it, stamp, alarmMinutes) }
        append("END:VCALENDAR\r\n")
    }
}

/** 「第N周」+ 教师 + 备注 */
private fun describe(week: Int, course: Course): String =
    listOfNotNull(
        "第${week}周",
        course.teacher.takeIf { it.isNotBlank() },
        course.remark?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

/** 课程在这一天的时间区间，缺节次时间或自定义时间不合法时为 null */
private fun Course.timeSpanOn(date: LocalDate, slots: Map<Int, TimeSlot>): Pair<LocalDateTime, LocalDateTime>? {
    val startMinutes: Int?
    val endMinutes: Int?
    if (isCustomTime) {
        startMinutes = parseMinutes(customStartTime)
        endMinutes = parseMinutes(customEndTime)
    } else {
        val first = startSection?.let { slots[it] }
        val last = endSection?.let { slots[it] } ?: first
        startMinutes = parseMinutes(first?.startTime)
        endMinutes = parseMinutes(last?.endTime)
    }
    if (startMinutes == null || endMinutes == null || endMinutes <= startMinutes) return null
    return date.atTime(minutesToTime(startMinutes)) to date.atTime(minutesToTime(endMinutes))
}

/** 当天第几分钟 → 时刻，越界（如 24:00）夹到 23:59，不做静默进位 */
private fun minutesToTime(minutes: Int): LocalTime {
    val safe = minutes.coerceIn(0, 23 * 60 + 59)
    return LocalTime.of(safe / 60, safe % 60)
}

private fun StringBuilder.appendTimeZone() {
    append("BEGIN:VTIMEZONE\r\n")
    append("TZID:").append(IcsTimeZone).append("\r\n")
    append("BEGIN:STANDARD\r\n")
    append("DTSTART:19700101T000000Z\r\n")
    append("TZOFFSETFROM:+0800\r\n")
    append("TZOFFSETTO:+0800\r\n")
    append("END:STANDARD\r\n")
    append("END:VTIMEZONE\r\n")
}

private fun StringBuilder.appendEvent(
    event: ExportEvent,
    stamp: String,
    alarmMinutes: Int?,
) {
    append("BEGIN:VEVENT\r\n")
    append("UID:").append(event.uid).append("\r\n")
    append("DTSTAMP:").append(stamp).append("\r\n")
    append("DTSTART;TZID=").append(IcsTimeZone).append(':').append(IcsLocalFormatter.format(event.start)).append("\r\n")
    append("DTEND;TZID=").append(IcsTimeZone).append(':').append(IcsLocalFormatter.format(event.end)).append("\r\n")
    append("SUMMARY:").append(escapeIcs(event.summary)).append("\r\n")
    if (event.location.isNotBlank()) append("LOCATION:").append(escapeIcs(event.location)).append("\r\n")
    if (event.description.isNotBlank()) append("DESCRIPTION:").append(escapeIcs(event.description)).append("\r\n")
    if (alarmMinutes != null) {
        append("BEGIN:VALARM\r\n")
        append("ACTION:DISPLAY\r\n")
        append("DESCRIPTION:课程提醒\r\n")
        append("TRIGGER:-PT").append(alarmMinutes).append("M\r\n")
        append("END:VALARM\r\n")
    }
    append("END:VEVENT\r\n")
}

private fun escapeIcs(text: String): String =
    text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
