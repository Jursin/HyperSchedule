package com.hyperschedule.app.reminder

import android.content.Context
import com.hyperschedule.app.data.db.AppDatabase
import com.hyperschedule.app.data.db.entity.CourseWithWeeks
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.local.ReminderSettings
import com.hyperschedule.app.data.local.SettingsStore
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.data.repository.ScheduleRepository
import com.hyperschedule.app.schedule.HolidayPlan
import com.hyperschedule.app.schedule.currentWeekOf
import com.hyperschedule.app.schedule.parseDateOrNull
import com.hyperschedule.app.schedule.parseMinutes
import com.hyperschedule.app.schedule.semesterStartMonday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 提醒要的一节课，当天绝对起止时间 + 通知里要显示的内容 */
internal data class DayCourse(
    val id: String,
    val name: String,
    val position: String,
    val teacher: String,
    val start: LocalDateTime,
    /** 只有起止时间都在、且结束晚于开始才算得出一节课的时长 */
    val end: LocalDateTime?,
) {
    fun isOngoing(now: LocalDateTime): Boolean = end != null && now >= start && now < end
}

/** 排课要的数据一次读齐，设置、作息、课程、节假日，调度与触发共用同一套解析 */
internal class ReminderData(
    val reminder: ReminderSettings,
    private val startDate: LocalDate,
    private val slotMap: Map<Int, TimeSlot>,
    private val courses: List<CourseWithWeeks>,
    private val plan: HolidayPlan,
) {
    fun coursesOn(date: LocalDate): List<DayCourse> = plan.coursesOn(date, startDate, courses, slotMap)

    companion object {
        /** 课表或学期日期没设好时返回 null —— 没有可提醒的东西，也不该在这里替用户建表 */
        suspend fun load(context: Context): ReminderData? {
            val state = SettingsStore.get(context).state
            val repository = ScheduleRepository(AppDatabase.get(context))
            val tableId = repository.existingTableIdOnce(state.currentTableId) ?: return null
            val config = repository.configOnce(tableId) ?: return null
            val startDate = config.semesterStartDate?.let(::parseDateOrNull) ?: return null
            val totalWeeks = config.semesterTotalWeeks.coerceAtLeast(1)
            val semesterEnd = semesterStartMonday(startDate).plusWeeks(totalWeeks.toLong()).minusDays(1)
            val holidays = HolidayRepository.get(context).entriesIn(startDate, semesterEnd)
            return ReminderData(
                reminder = state.reminder,
                startDate = startDate,
                slotMap = repository.slotsOnce(tableId).associateBy { it.number },
                courses = repository.coursesOnce(tableId),
                plan = HolidayPlan.build(startDate, totalWeeks, holidays),
            )
        }
    }
}

/** 当天的课程快照，课中每分钟对账直接用它 */
internal data class DaySnapshot(
    val date: LocalDate,
    val courses: List<DayCourse>,
    val data: ReminderData,
)

internal object ReminderCache {

    @Volatile
    private var snapshot: DaySnapshot? = null

    fun put(date: LocalDate, courses: List<DayCourse>, data: ReminderData) {
        snapshot = DaySnapshot(date, courses, data)
    }

    fun get(date: LocalDate): DaySnapshot? = snapshot?.takeIf { it.date == date }
}

/** 某一天实际上哪些课 */
internal fun HolidayPlan.coursesOn(
    date: LocalDate,
    startDate: LocalDate,
    courses: List<CourseWithWeeks>,
    slotMap: Map<Int, TimeSlot>,
): List<DayCourse> {
    val week = currentWeekOf(startDate, date)
    // 未开学（第 0 周及以前）当天没有任何课
    if (week < 1) return emptyList()
    val plan = of(week, date.dayOfWeek.value)
    if (plan.holiday) return emptyList()
    val weekNumber = plan.weekOr(week)
    val dayOfWeek = plan.dayOr(date.dayOfWeek.value)
    return courses
        .asSequence()
        .filter { it.course.day == dayOfWeek && it.weeks.any { item -> item.weekNumber == weekNumber } }
        .mapNotNull { it.toDayCourse(date, slotMap) }
        .sortedBy { it.start }
        .toList()
}

private fun CourseWithWeeks.toDayCourse(
    date: LocalDate,
    slotMap: Map<Int, TimeSlot>,
): DayCourse? {
    val course = course
    val startText =
        if (course.isCustomTime) {
            course.customStartTime
        } else {
            course.startSection?.let { slotMap[it]?.startTime }
        }
    val endText =
        if (course.isCustomTime) {
            course.customEndTime
        } else {
            course.endSection?.let { slotMap[it]?.endTime }
        }
    val midnight = date.atStartOfDay()
    val start = parseMinutes(startText)?.let { midnight.plusMinutes(it.toLong()) } ?: return null
    // 结束时间早于开始（跨零点或脏数据）时不认，提醒按「算不出时长」处理
    val end = parseMinutes(endText)?.let { midnight.plusMinutes(it.toLong()) }?.takeIf { it > start }
    return DayCourse(course.id, course.name, course.position, course.teacher, start, end)
}

/** 本地时间转绝对毫秒，闹钟、通知、岛上的倒计时都按它对齐 */
internal fun LocalDateTime.toEpochMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
