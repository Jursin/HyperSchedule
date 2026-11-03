package com.hyperschedule.app.reminder

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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

    @Suppress("UsePropertyAccessSyntax")
    @Before
    fun setUp() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        ClassDnd.clear(context)
    }

    @Test
    fun restoreRevertsItsOwnFilterChange() {
        val original = notificationManager.currentInterruptionFilter

        assertTrue(ClassDnd.apply(context, priorityMode))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notificationManager.currentInterruptionFilter)

        ClassDnd.restore(context)
        assertEquals(original, notificationManager.currentInterruptionFilter)
        assertEquals(false, ClassDnd.isApplied(context))
    }

    @Test
    fun restoreKeepsFilterChangedByUser() {
        ClassDnd.apply(context, priorityMode)
        // 用户课中自己改回完全勿扰，退出时不该被强行改回上课前的档位
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)

        ClassDnd.restore(context)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, notificationManager.currentInterruptionFilter)
    }

    @Test
    fun restoreRevertsItsOwnRingerChange() {
        val original = audioManager.ringerMode

        assertTrue(ClassDnd.apply(context, silentMode))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audioManager.ringerMode)

        ClassDnd.restore(context)
        assertEquals(original, audioManager.ringerMode)
    }

    @Test
    fun restoreKeepsRingerChangedByUser() {
        ClassDnd.apply(context, silentMode)
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

        ClassDnd.restore(context)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audioManager.ringerMode)
    }
}
