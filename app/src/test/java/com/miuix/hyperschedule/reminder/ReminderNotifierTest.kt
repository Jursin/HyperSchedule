package com.miuix.hyperschedule.reminder

import com.miuix.hyperschedule.schedule.cjkDigitGap
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 通知正文的拼法：地点与教师都可能没填，缺哪项都不能留下多余的分隔符 */
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
    }

    @Test
    fun bodiesHoldOnlyHeadWithoutDetail() {
        val bare = course.copy(position = "", teacher = "")
        assertEquals("10分钟后上课", plainBodyOf(bare, "10分钟后上课"))
        assertEquals("10分钟后上课", liveBodyOf(bare, "10分钟后上课"))
    }

    @Test
    fun liveBodySeparatesDetailAndHead() {
        assertEquals("A101 · 张老师\n10分钟后上课", liveBodyOf(course, "10分钟后上课"))
    }

    @Test
    fun preClassHeadRoundsUpRemainingMinutes() {
        val soon = course.copy(start = LocalDateTime.now().plusMinutes(9).plusSeconds(30))
        assertEquals("10分钟后上课".cjkDigitGap(), preClassHead(soon))
        assertEquals("现在上课", preClassHead(course.copy(start = LocalDateTime.now().minusMinutes(1))))
    }
}
