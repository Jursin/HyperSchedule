package com.miuix.hyperschedule.reminder

import android.content.Context
import com.miuix.hyperschedule.theme.ReminderSettings
import java.time.LocalDateTime

/** 按「此刻是否有课」对齐通知、每分钟对账与勿扰 */
internal object ReminderRuntime {

    fun sync(
        context: Context,
        reminder: ReminderSettings,
        today: List<DayCourse>,
        now: LocalDateTime,
    ) {
        val ongoing = today.filter { it.isOngoing(now) }.maxByOrNull { it.end ?: it.start }
        val preClassWindow = inPreClassWindow(today, now, reminder.preMinutes)
        val island = SuperIsland.canPost()

        if (ongoing != null && reminder.inClass) {
            // 课前那条用的是另一个 id，上课时换成下课倒计时
            ReminderNotifier.notifyInClass(context, ongoing)
        } else if (!preClassWindow) {
            // 课前窗口内留着那条倒计时；出了窗口（下课、课被删）才收
            ReminderNotifier.cancel(context, ReminderNotifier.IN_CLASS_ID)
        }
        // 授权没了以后残留下来的那条岛通知：实时动态接不了课前那条固定 id
        if (!island) ReminderNotifier.cancel(context, ReminderNotifier.PRE_CLASS_ID)

        // 岛上的倒计时由系统自刷，只有实时动态那条需要应用每分钟重推一次进度
        val ticking =
            !island &&
                ReminderPermissions.canPostPromotedNotifications(context) &&
                (preClassWindow || (ongoing != null && reminder.inClass))
        if (ticking) {
            ReminderScheduler.scheduleLiveTick(context, now)
        } else {
            ReminderScheduler.cancelLiveTick(context)
        }

        // 用户按过「上课勿扰」时，开与关都由他自己决定，对账不插手
        if (ClassDnd.isManual(context)) return
        if (ongoing != null && reminder.autoDnd) {
            ClassDnd.apply(context, reminder.dndMode)
        } else {
            ClassDnd.restore(context)
        }
    }

    /** 关掉提醒时收拾现场：撤掉两条通知与对账闹钟，把勿扰还回去 */
    fun reset(context: Context) {
        ReminderNotifier.cancelPreClass(context)
        ReminderScheduler.cancelLiveTick(context)
        ClassDnd.restore(context)
    }

    private fun inPreClassWindow(today: List<DayCourse>, now: LocalDateTime, minutes: Int): Boolean {
        return minutes > 0 && today.any { now < it.start && !now.isBefore(it.start.minusMinutes(minutes.toLong())) }
    }
}
