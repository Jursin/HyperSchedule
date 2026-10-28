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
        periodic: Boolean = false,
    ) {
        val ongoing = today.filter { it.isOngoing(now) }.maxByOrNull { it.end ?: it.start }
        val preClassWindow = inPreClassWindow(today, now, reminder.preMinutes)
        val island = SuperIsland.canPost()
        val inClass = if (reminder.inClass) ongoing else null

        if (inClass != null) {
            // 岛自己走字，对账不重发
            if (!periodic || !island) ReminderNotifier.notifyInClass(context, inClass)
        } else if (preClassWindow) {
            // 课前窗口只收课中那条
            ReminderNotifier.cancel(context, ReminderNotifier.IN_CLASS_ID)
        } else {
            // 下课或课被删，三条一起收
            ReminderNotifier.cancelAll(context)
        }
        // 授权没了残留的岛
        if (!island) ReminderNotifier.cancel(context, ReminderNotifier.PRE_CLASS_ID)

        // 有东西可显示就每分钟对账
        if (preClassWindow || inClass != null) {
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

    /** 关掉提醒时收拾现场 */
    fun reset(context: Context) {
        ReminderNotifier.cancelAll(context)
        ReminderScheduler.cancelLiveTick(context)
        ClassDnd.restore(context)
    }

    private fun inPreClassWindow(today: List<DayCourse>, now: LocalDateTime, minutes: Int): Boolean {
        return minutes > 0 && today.any { now < it.start && !now.isBefore(it.start.minusMinutes(minutes.toLong())) }
    }
}
