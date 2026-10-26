package com.miuix.hyperschedule.data.exporter

import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.HolidayEntry
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.schedule.HolidayPlan
import com.miuix.hyperschedule.schedule.parseMinutes
import com.miuix.hyperschedule.schedule.weekDatesOf
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

/** 逐周展开成 VEVENT：放假那天不写，节次时间取当天生效的作息；[alarmMinutes] 为空时不加提醒 */
fun buildIcs(data: TableExportData, entries: List<HolidayEntry>, alarmMinutes: Int?): String {
    val startDate = data.startDate ?: error("请先在课表设置里填写开学日期")
    val lastWeek = data.lastWeek
    val plan = HolidayPlan.build(startDate, lastWeek, entries)
    val coursesByCell = HashMap<Int, MutableList<CourseWithWeeks>>()
    data.courses.forEach { group ->
        group.weeks.forEach { courseWeek ->
            coursesByCell.getOrPut(courseWeek.weekNumber * 10 + group.course.day) { mutableListOf() } += group
        }
    }
    val stamp = IcsStampFormatter.format(ZonedDateTime.now(ZoneId.of("UTC")))

    return buildString {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("PRODID:-//HyperSchedule//Course Schedule//CN\r\n")
        append("CALSCALE:GREGORIAN\r\n")
        append("METHOD:PUBLISH\r\n")
        appendTimeZone()
        for (week in 1..lastWeek) {
            val dates = weekDatesOf(startDate, week)
            for (day in 1..7) {
                if (plan.of(week, day).holiday) continue
                val date = dates[day - 1]
                val slots = data.slotsFor(date).associateBy { it.number }
                coursesByCell[week * 10 + day].orEmpty().forEach { entry ->
                    val course = entry.course
                    val span = course.timeSpanOn(date, slots) ?: return@forEach
                    appendEvent(
                        uid = "${course.id}-$week-${IcsDayFormatter.format(date)}@hyperschedule",
                        stamp = stamp,
                        start = span.first,
                        end = span.second,
                        summary = course.name,
                        location = course.position,
                        description = describe(week, course),
                        alarmMinutes = alarmMinutes,
                    )
                }
            }
        }
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

/** 课程在这一天的时间区间；缺节次时间或自定义时间不合法时为 null */
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

/** 当天第几分钟 → 时刻；越界（如 24:00）夹到 23:59，不做静默进位 */
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
    uid: String,
    stamp: String,
    start: LocalDateTime,
    end: LocalDateTime,
    summary: String,
    location: String,
    description: String,
    alarmMinutes: Int?,
) {
    append("BEGIN:VEVENT\r\n")
    append("UID:").append(uid).append("\r\n")
    append("DTSTAMP:").append(stamp).append("\r\n")
    append("DTSTART;TZID=").append(IcsTimeZone).append(':').append(IcsLocalFormatter.format(start)).append("\r\n")
    append("DTEND;TZID=").append(IcsTimeZone).append(':').append(IcsLocalFormatter.format(end)).append("\r\n")
    append("SUMMARY:").append(escapeIcs(summary)).append("\r\n")
    if (location.isNotBlank()) append("LOCATION:").append(escapeIcs(location)).append("\r\n")
    if (description.isNotBlank()) append("DESCRIPTION:").append(escapeIcs(description)).append("\r\n")
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
