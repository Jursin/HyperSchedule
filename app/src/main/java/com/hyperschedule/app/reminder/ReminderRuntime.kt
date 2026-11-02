package com.hyperschedule.app.reminder

import android.content.Context
import com.hyperschedule.app.theme.ReminderSettings
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
        // 冲突课程里只认最后一门
        val ongoing = today.filter { it.isOngoing(now) }.latest()
        // 有课在上时课前那条让位
        val preClass = if (ongoing == null) latestPreClass(today, now, reminder.preMinutes) else null
        val inClass = if (reminder.inClass) ongoing else null
        val island = SuperIsland.canPost()

        when {
            inClass != null -> {
                // 岛自己走字，对账不重发
                if (!periodic || !island) ReminderNotifier.notifyInClass(context, inClass)
            }
            preClass != null -> {
                // 对账只负责换文案，内容没变时通知与岛各自会跳过
                if (!periodic) ReminderNotifier.notifyPreClass(context, preClass, now)
            }
            else -> {
                // 下课或课被删，三条一起收
                ReminderNotifier.cancelAll(context)
            }
        }
        // 授权没了残留的岛
        if (!island) ReminderNotifier.cancel(context, ReminderNotifier.PRE_CLASS_ID)

        // 有东西可显示就每分钟对账
        if (preClass != null || inClass != null) {
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
}

/** 冲突课程里只认最后一门 */
internal fun List<DayCourse>.latest(): DayCourse? =
    sortedWith(compareBy({ it.end ?: it.start }, { it.start })).lastOrNull()

/** 课前窗口里最该提醒的那一门，已经开课的不算，到点提醒的那几门里也只认最后一门 */
internal fun latestPreClass(today: List<DayCourse>, now: LocalDateTime, minutes: Int): DayCourse? {
    if (minutes <= 0) return null
    return today
        .filter { now < it.start && !now.isBefore(it.start.minusMinutes(minutes.toLong())) }
        .latest()
}
