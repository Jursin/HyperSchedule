package com.hyperschedule.app.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** 常驻通知的构建，倒计时对齐下课、正文分行、重复内容不重发，以及通道得能穿过上课勿扰 */
@RunWith(RobolectricTestRunner::class)
class ReminderNotifierAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val now: LocalDateTime = LocalDateTime.now()

    /** 系统里现存的通知 */
    private val posted get() = shadowOf(manager).allNotifications

    /** 正在上的一节课，课中通知只在还没下课时才发 */
    private val live = DayCourse("c1", "高等数学", "A101", "张老师", now.minusMinutes(20), now.plusMinutes(40))

    @Before
    fun clearPosted() = ReminderNotifier.cancelAll(context)

    @Test
    fun inClassNotificationCountsDownToTheEnd() {
        ReminderNotifier.notifyInClass(context, live)

        val notification = shadowOf(manager).getNotification(ReminderNotifier.IN_CLASS_ID)!!
        assertEquals("class_live", notification.channelId)
        assertEquals("高等数学", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        // 课中不重复「即将上课」，正文只剩地点与教师
        assertEquals("A101 · 张老师", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(live.end!!.toEpochMillis(), notification.`when`)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        // 系统秒表倒计时，key 没有公开常量
        assertTrue(notification.extras.getBoolean("android.showChronometer"))
        assertTrue(notification.extras.getBoolean("android.chronometerCountDown"))
        assertEquals(listOf("上课勿扰"), notification.actions?.map { it.title.toString() })
    }

    @Test
    fun preClassNotificationPutsStateOnItsOwnLine() {
        ReminderNotifier.notifyPreClass(context, live, now)

        val notification = posted.single()
        // 地点、教师与状态词各自成行，BigTextStyle 才能折出两行
        assertEquals("A101 · 张老师 · 即将上课", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)!!.contains("\n"))
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        // 倒计时指向上课那一刻
        assertEquals(live.start.toEpochMillis(), notification.`when`)
    }

    @Test
    fun endedCoursePostsNothing() {
        ReminderNotifier.notifyInClass(context, live.copy(start = now.minusHours(2), end = now.minusHours(1)))

        assertNull(shadowOf(manager).getNotification(ReminderNotifier.IN_CLASS_ID))
    }

    @Test
    fun sameContentIsNotPostedTwice() {
        ReminderNotifier.notifyPreClass(context, live, now)
        val first = posted.single()

        ReminderNotifier.notifyPreClass(context, live, now)
        // 内容签名没变就不再走 notify，系统里还是同一个对象
        assertSame(first, posted.single())

        ReminderNotifier.notifyPreClass(context, live.copy(name = "线性代数"), now)
        assertNotSame(first, posted.single())
    }

    @Test
    fun cancelAllClearsPostedNotifications() {
        ReminderNotifier.notifyInClass(context, live)
        assertEquals(1, posted.size)

        ReminderNotifier.cancelAll(context)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun liveChannelStaysQuietAndBypassesDnd() {
        ReminderNotifier.ensureChannels(context)

        val channel = manager.getNotificationChannel("class_live")!!
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        // 响铃交给用户自己配，但上课自动开的勿扰不能把它一起挡掉
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.canShowBadge())
        assertTrue(channel.canBypassDnd())
    }
}
