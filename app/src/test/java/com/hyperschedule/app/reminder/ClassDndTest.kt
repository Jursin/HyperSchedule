package com.hyperschedule.app.reminder

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** 上课勿扰只在系统值仍是自己写进去的那个时才还原，用户课中改过就保留现状 */
@RunWith(RobolectricTestRunner::class)
class ClassDndTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)

    /** 与 [com.hyperschedule.app.theme.ReminderSettings.DND_MODE_LABELS] 的下标对应 */
    private val silentMode = 1
    private val priorityMode = 2

    /** 记录里的下课时刻要在检查之后 */
    private val laterEnd = System.currentTimeMillis() + 3_600_000L

    @Suppress("UsePropertyAccessSyntax")
    @Before
    fun setUp() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        ClassDnd.clear(context)
    }

    @Test
    fun restoreRevertsItsOwnFilterChange() {
        val original = notificationManager.currentInterruptionFilter

        assertTrue(ClassDnd.apply(context, priorityMode, laterEnd))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notificationManager.currentInterruptionFilter)

        ClassDnd.restore(context)
        assertEquals(original, notificationManager.currentInterruptionFilter)
        assertFalse(ClassDnd.isApplied(context))
    }

    @Test
    fun restoreKeepsFilterChangedByUser() {
        ClassDnd.apply(context, priorityMode, laterEnd)
        // 用户课中自己改回完全勿扰，退出时不该被强行改回上课前的档位
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)

        ClassDnd.restore(context)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, notificationManager.currentInterruptionFilter)
        // 记录要清掉，不能带到下一节课
        assertFalse(ClassDnd.isApplied(context))
    }

    @Test
    fun restoreRevertsItsOwnRingerChange() {
        val original = audioManager.ringerMode

        assertTrue(ClassDnd.apply(context, silentMode, laterEnd))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audioManager.ringerMode)

        ClassDnd.restore(context)
        assertEquals(original, audioManager.ringerMode)
    }

    @Test
    fun restoreKeepsRingerChangedByUser() {
        ClassDnd.apply(context, silentMode, laterEnd)
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

        ClassDnd.restore(context)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audioManager.ringerMode)
    }

    @Test
    fun silentModeLeavesInterruptionFilterAlone() {
        val original = notificationManager.currentInterruptionFilter

        ClassDnd.apply(context, silentMode, laterEnd)
        assertEquals(original, notificationManager.currentInterruptionFilter)
    }

    @Test
    fun repeatedApplyKeepsUserChange() {
        ClassDnd.apply(context, priorityMode, laterEnd)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

        // 课中每分钟对账再走一次，也不能把用户关掉的勿扰重新打开
        ClassDnd.apply(context, priorityMode, laterEnd)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, notificationManager.currentInterruptionFilter)
    }

    @Test
    fun repeatedApplyKeepsOriginal() {
        val original = notificationManager.currentInterruptionFilter

        ClassDnd.apply(context, priorityMode, laterEnd)
        // 连堂课续期，仍算同一次接管，原值不能被下课前的勿扰顶掉
        ClassDnd.apply(context, priorityMode, laterEnd + 1_000L)

        ClassDnd.restore(context)
        assertEquals(original, notificationManager.currentInterruptionFilter)
    }

    @Test
    fun toggleIsRestoredAfterClass() {
        val original = notificationManager.currentInterruptionFilter

        // 课前点开算手动接管，接管中再点一次也还是同一次接管
        assertTrue(ClassDnd.toggle(context, priorityMode))
        assertTrue(ClassDnd.isManual(context))
        assertTrue(ClassDnd.toggle(context, priorityMode))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notificationManager.currentInterruptionFilter)

        ClassDnd.restore(context)
        assertEquals(original, notificationManager.currentInterruptionFilter)
    }
}
