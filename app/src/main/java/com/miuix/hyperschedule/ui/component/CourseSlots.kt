package com.miuix.hyperschedule.ui.component

import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.schedule.parseMinutes

/** 同一格：同一天且起始节次相同（与网格分格方式一致）；自定义时间课比对起止时间 */
internal fun Course.isSameSlotAs(other: Course): Boolean {
    if (day != other.day) return false
    if (isCustomTime || other.isCustomTime) {
        return isCustomTime && other.isCustomTime &&
            customStartTime == other.customStartTime &&
            customEndTime == other.customEndTime
    }
    return startSection != null && startSection == other.startSection
}

/** 该课程是否落在第 [section] 节：标准课看节次范围，自定义时间课看时间重叠 */
internal fun Course.covers(section: Int, sectionTimes: List<Pair<String, String>>): Boolean =
    if (isCustomTime) {
        section in overlappedSections(this, sectionTimes)
    } else {
        val start = startSection ?: return false
        val end = (endSection ?: start).coerceAtLeast(start)
        section in start..end
    }

/** 某页周次下的一门课：active = 本页周次生效；hiddenCount = 同格还有几门没画出来 */
internal data class WeekCourse(
    val course: Course,
    val active: Boolean,
    val hiddenCount: Int = 0,
)

/** 槽位必须带星期，否则不同天的同一节会被并成一组、互相吞掉 */
private fun courseSlotKey(course: Course): String =
    if (course.isCustomTime) {
        "custom|${course.day}|${course.customStartTime}|${course.customEndTime}"
    } else {
        "section|${course.day}|${course.startSection}"
    }

/** 本页周次生效的照常显示；某格全都不上时取最近的一门课灰显（可在设置里关闭） */
internal fun resolveWeekCourses(
    all: List<CourseWithWeeks>,
    week: Int,
    showNonCurrent: Boolean,
): List<WeekCourse> =
    all.groupBy { courseSlotKey(it.course) }.flatMap { (_, items) ->
        val active = items.filter { item -> item.weeks.any { it.weekNumber == week } }
        when {
            active.isNotEmpty() ->
                active.map { WeekCourse(it.course, true, hiddenCount = items.size - active.size) }
            !showNonCurrent -> emptyList()
            else -> {
                val ended = items.filter { item -> (item.weeks.maxOfOrNull { it.weekNumber } ?: 0) < week }
                val upcoming =
                    items.filter { item ->
                        (item.weeks.minOfOrNull { it.weekNumber } ?: Int.MAX_VALUE) > week
                    }
                val picked =
                    ended.maxByOrNull { item -> item.weeks.maxOfOrNull { it.weekNumber } ?: 0 }
                        ?: upcoming.minByOrNull { item -> item.weeks.minOfOrNull { it.weekNumber } ?: Int.MAX_VALUE }
                        ?: items.first()
                listOf(WeekCourse(picked.course, false, hiddenCount = items.size - 1))
            }
        }
    }

/** 自定义时间课占用的节次：与节次时间段有交集即算占用 */
internal fun overlappedSections(course: Course, sectionTimes: List<Pair<String, String>>): List<Int> {
    val startAt = parseMinutes(course.customStartTime) ?: return emptyList()
    val endAt = parseMinutes(course.customEndTime) ?: return emptyList()
    if (startAt == endAt) return emptyList()
    return sectionTimes.mapIndexedNotNull { index, (start, end) ->
        val sectionStart = parseMinutes(start) ?: return@mapIndexedNotNull null
        val sectionEnd = parseMinutes(end) ?: return@mapIndexedNotNull null
        if (startAt < sectionEnd && endAt > sectionStart) index + 1 else null
    }
}
