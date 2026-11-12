package com.hyperschedule.app.reminder

import android.content.Context
import com.hyperschedule.app.data.local.ReminderSettings
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

        // 先对齐勿扰再发提醒，课中才被唤醒时也不漏
        syncDnd(context, reminder, ongoing, preClass)

        when {
            inClass != null -> {
                if (!periodic || !island) ReminderNotifier.notifyInClass(context, inClass)
            }
            preClass != null -> {
                if (!periodic) ReminderNotifier.notifyPreClass(context, preClass, now)
            }
            else -> {
                ReminderNotifier.cancelAll(context)
            }
        }

        if (preClass != null || inClass != null) {
            ReminderScheduler.scheduleLiveTick(context, now)
        } else {
            ReminderScheduler.cancelLiveTick(context)
        }
    }

    /** 上课时按档位补一次，下课才比对还原 */
    private fun syncDnd(
        context: Context,
        reminder: ReminderSettings,
        ongoing: DayCourse?,
        preClass: DayCourse?,
    ) {
        // 课前点开的挂到下一节课，课上的一直挂到下课
        val anchor = ongoing ?: preClass
        val end = anchor?.end?.toEpochMillis() ?: 0L
        when {
            ongoing != null && reminder.autoDnd -> ClassDnd.apply(context, reminder.dndMode, end)
            ClassDnd.isManual(context) && anchor != null -> ClassDnd.extend(context, end)
            else -> ClassDnd.restore(context)
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

/** 课前窗口里最近提醒的那一门 */
internal fun latestPreClass(today: List<DayCourse>, now: LocalDateTime, minutes: Int): DayCourse? {
    if (minutes <= 0) return null
    return today
        .filter { now < it.start && !now.isBefore(it.start.minusMinutes(minutes.toLong())) }
        .latest()
}
