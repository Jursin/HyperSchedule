package com.hyperschedule.app.reminder

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 什么时候该提醒，课前窗口的边界，以及冲突课程里挑哪一门 */
class ReminderRuntimeTest {

    private val day: LocalDate = LocalDate.of(2026, 11, 3)

    private fun course(name: String, start: LocalDateTime, end: LocalDateTime?) =
        DayCourse(id = name, name = name, position = "", teacher = "", start = start, end = end)

    @Test
    fun preClassWindowIncludesItsOpeningMoment() {
        val soon = course("高等数学", day.atTime(14, 0), day.atTime(15, 0))

        // 窗口是 [上课-10分钟, 上课)，边界那一刻还得提醒，上课之后交给课中那条
        assertEquals(soon, latestPreClass(listOf(soon), day.atTime(13, 50), 10))
        assertNull(latestPreClass(listOf(soon), day.atTime(13, 49, 59), 10))
        assertNull(latestPreClass(listOf(soon), day.atTime(14, 0), 10))
    }

    @Test
    fun preClassReminderIsOffWhenWindowIsZero() {
        val soon = course("高等数学", day.atTime(14, 0), day.atTime(15, 0))

        assertNull(latestPreClass(listOf(soon), day.atTime(13, 50), 0))
    }

    @Test
    fun preClassPicksTheCourseStartingLast() {
        val early = course("早课", day.atTime(14, 0), day.atTime(15, 0))
        val late = course("晚课", day.atTime(14, 5), day.atTime(15, 5))

        assertEquals(late, latestPreClass(listOf(early, late), day.atTime(13, 58), 10))
    }

    @Test
    fun ongoingCourseIsChosenByItsEnd() {
        val short = course("短课", day.atTime(14, 0), day.atTime(14, 30))
        val long = course("长课", day.atTime(13, 30), day.atTime(15, 0))

        // 冲突课里认下课最晚的那门，而不是开课最晚的
        assertEquals(long, listOf(short, long).latest())
    }

    @Test
    fun courseWithoutEndSortsByItsStart() {
        val morning = course("上午", day.atTime(9, 0), null)
        val noon = course("中午", day.atTime(10, 0), null)

        assertEquals(noon, listOf(morning, noon).latest())
        assertNull(emptyList<DayCourse>().latest())
    }
}
