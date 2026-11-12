package com.hyperschedule.app.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hyperschedule.app.data.local.ReminderSettings
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** 闹钟排点，只排还没到点的时刻，课前提醒按设置的提前量前移，每节课三个点各一个闹钟 */
@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val day: LocalDate = LocalDate.of(2026, 11, 3)
    private val noon: LocalDateTime = day.atTime(12, 0)
    private val remindTenMinutesEarly = ReminderSettings(preMinutes = 10)

    private val scheduled get() = shadowOf(alarmManager).scheduledAlarms

    private fun course(start: LocalDateTime, end: LocalDateTime?) =
        DayCourse(id = "c1", name = "高等数学", position = "A101", teacher = "张老师", start = start, end = end)

    private fun at(hour: Int, minute: Int): Long = day.atTime(hour, minute).toEpochMillis()

    private fun scheduleDay(courses: List<DayCourse>, now: LocalDateTime): Set<Int> {
        val codes = mutableSetOf<Int>()
        ReminderScheduler.scheduleDay(context, alarmManager, remindTenMinutesEarly, 0, courses, now, codes)
        return codes
    }

    @Test
    fun upcomingCourseGetsPreStartAndEndAlarms() {
        val codes = scheduleDay(listOf(course(day.atTime(14, 0), day.atTime(15, 40))), noon)

        // 请求码按「第几天 × 类型 × 序号」编码，课前、上课、下课各一个
        assertEquals(setOf(1000, 2000, 3000), codes)
        assertEquals(listOf(at(13, 50), at(14, 0), at(15, 40)), scheduled.map { it.triggerAtMs })
        assertTrue(scheduled.all { it.getType() == AlarmManager.RTC_WAKEUP && it.isAllowWhileIdle })
    }

    @Test
    fun startedCourseOnlyKeepsEndAlarm() {
        val codes = scheduleDay(listOf(course(day.atTime(11, 0), day.atTime(13, 0))), noon)

        assertEquals(setOf(3000), codes)
        assertEquals(listOf(at(13, 0)), scheduled.map { it.triggerAtMs })
    }

    @Test
    fun preClassAlarmSkipsWhenItsWindowAlreadyPassed() {
        val codes = scheduleDay(listOf(course(day.atTime(12, 5), day.atTime(13, 0))), noon)

        // 课前那个点（11:55）已经过去，只剩上课与下课
        assertEquals(setOf(2000, 3000), codes)
        assertEquals(listOf(at(12, 5), at(13, 0)), scheduled.map { it.triggerAtMs })
    }

    @Test
    fun courseWithoutEndSkipsEndAlarm() {
        val codes = scheduleDay(listOf(course(day.atTime(14, 0), null)), noon)

        // 算不出时长（跨零点或脏数据）时没有下课那一刻
        assertEquals(setOf(1000, 2000), codes)
        assertEquals(listOf(at(13, 50), at(14, 0)), scheduled.map { it.triggerAtMs })
    }

    @Test
    fun eachCourseOfTheDayGetsItsOwnRequestCodes() {
        val codes =
            scheduleDay(
                listOf(
                    course(day.atTime(14, 0), day.atTime(15, 0)),
                    course(day.atTime(16, 0), day.atTime(17, 0)),
                ),
                noon,
            )

        // 序号落在请求码末位，同一天两节课不会互相覆盖
        assertEquals(setOf(1000, 2000, 3000, 1001, 2001, 3001), codes)
        assertEquals(6, scheduled.size)
    }

    @Test
    fun preClassAlarmIsPushedToPreviousBreak() {
        val codes =
            scheduleDay(
                listOf(
                    course(day.atTime(13, 0), day.atTime(13, 55)),
                    course(day.atTime(14, 0), day.atTime(15, 40)),
                ),
                noon,
            )

        // 14:00 这节的课前点本该在 13:50，那时上一节还没下课，压到 13:55 打铃那一刻
        assertEquals(setOf(1000, 2000, 3000, 1001, 2001, 3001), codes)
        assertEquals(
            listOf(at(12, 50), at(13, 0), at(13, 55), at(13, 55), at(14, 0), at(15, 40)),
            scheduled.map { it.triggerAtMs }.sorted(),
        )
    }

    @Test
    fun backToBackCoursesSkipPreClassAlarm() {
        val codes =
            scheduleDay(
                listOf(
                    course(day.atTime(13, 0), day.atTime(14, 0)),
                    course(day.atTime(14, 0), day.atTime(15, 40)),
                ),
                noon,
            )

        // 连堂课没有课间，课前提醒让位给上课闹钟，只留下课那一刻
        assertEquals(setOf(1000, 2000, 3000, 2001, 3001), codes)
        assertEquals(
            listOf(at(12, 50), at(13, 0), at(14, 0), at(14, 0), at(15, 40)),
            scheduled.map { it.triggerAtMs },
        )
    }

    @Test
    fun liveTickSchedulesAndCancelsTheSameAlarm() {
        ReminderScheduler.scheduleLiveTick(context, noon)
        assertEquals(listOf(noon.plusSeconds(65).toEpochMillis()), scheduled.map { it.triggerAtMs })

        // 撤销要能命中同一个 PendingIntent，否则闹钟会残留
        ReminderScheduler.cancelLiveTick(context)
        assertTrue(scheduled.isEmpty())
    }
}
