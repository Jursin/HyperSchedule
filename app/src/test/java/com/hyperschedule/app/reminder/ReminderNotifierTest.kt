package com.hyperschedule.app.reminder

import com.hyperschedule.app.schedule.cjkDigitGap
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 通知正文的拼法，地点与教师都可能没填，缺哪项都不能留下多余的分隔符 */
class ReminderNotifierTest {

    private val course =
        DayCourse(
            id = "c1",
            name = "高等数学",
            position = "A101",
            teacher = "张老师",
            start = LocalDate.of(2026, 10, 27).atTime(15, 0),
            end = LocalDate.of(2026, 10, 27).atTime(17, 40),
        )

    @Test
    fun plainBodyPutsDetailAfterHead() {
        assertEquals("10分钟后上课 · A101 · 张老师", plainBodyOf(course, "10分钟后上课"))
        assertEquals("10分钟后上课 · A101", plainBodyOf(course.copy(teacher = ""), "10分钟后上课"))
        assertEquals("10分钟后上课 · 张老师", plainBodyOf(course.copy(position = ""), "10分钟后上课"))
        assertEquals("10分钟后上课", plainBodyOf(course.copy(position = "", teacher = ""), "10分钟后上课"))
    }

    @Test
    fun liveBodyKeepsStateOnItsOwnLine() {
        // 地点与教师在上，状态词另起一行；课中不传状态词，只留地点与教师
        assertEquals(listOf("A101 · 张老师", "即将上课"), liveBodyLines(course, "即将上课"))
        assertEquals(listOf("即将上课"), liveBodyLines(course.copy(position = "", teacher = ""), "即将上课"))
        assertEquals(listOf("A101 · 张老师"), liveBodyLines(course, null))
        assertEquals(emptyList<String>(), liveBodyLines(course.copy(position = "", teacher = ""), null))
    }

    @Test
    fun preClassHeadRoundsUpRemainingMinutes() {
        val soon = course.copy(start = LocalDateTime.now().plusMinutes(9).plusSeconds(30))
        assertEquals("10分钟后上课".cjkDigitGap(), preClassHead(soon))
        assertEquals("现在上课", preClassHead(course.copy(start = LocalDateTime.now().minusMinutes(1))))
    }

    @Test
    fun inClassTailShowsEndClockTime() {
        assertEquals("17:40下课".cjkDigitGap(), inClassTail(course.end!!))
    }
}
