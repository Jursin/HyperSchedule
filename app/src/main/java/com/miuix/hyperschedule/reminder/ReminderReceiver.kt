package com.miuix.hyperschedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.miuix.hyperschedule.schedule.parseDateOrNull
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "CourseReminder"

/** 闹钟被系统拖后仍照发，超过这个窗口就只当它过期了（可能已经在课中通知里体现） */
private const val LATE_GRACE_MINUTES = 10L

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** 提醒闹钟的落点 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getIntExtra(ReminderScheduler.EXTRA_TYPE, 0)
        val day = intent.getStringExtra(ReminderScheduler.EXTRA_DAY)
        val courseId = intent.getStringExtra(ReminderScheduler.EXTRA_COURSE_ID)
        val app = context.applicationContext
        receiveAsync { handle(app, type, day, courseId) }
    }

    private suspend fun handle(context: Context, type: Int, day: String?, courseId: String?) {
        // 附加信息丢了（系统重建过 PendingIntent）就整表重排，比静默跳过稳妥
        if (type !in ReminderScheduler.TYPE_PRE_CLASS..ReminderScheduler.TYPE_LIVE_TICK) {
            ReminderScheduler.resyncNow(context)
            return
        }
        val data = ReminderData.load(context)
        if (data == null || !data.reminder.enabled) {
            ReminderRuntime.reset(context)
            return
        }
        val date = parseDateOrNull(day.orEmpty()) ?: LocalDateTime.now().toLocalDate()
        val now = LocalDateTime.now()
        val courses = data.coursesOn(date)
        when (type) {
            ReminderScheduler.TYPE_PRE_CLASS -> {
                val course = courses.firstOrNull { it.id == courseId }
                // 课已经不在这一天、或早就上完了（闹钟被系统拖后太多）：不补发，改为重排
                if (course == null || now.isAfter(course.start.plusMinutes(LATE_GRACE_MINUTES))) {
                    ReminderScheduler.resyncNow(context)
                    return
                }
                ReminderNotifier.notifyPreClass(context, course, now)
                ReminderRuntime.sync(context, data.reminder, courses, now)
            }
            ReminderScheduler.TYPE_DAY_ROLLOVER -> ReminderScheduler.resyncNow(context)
            else -> {
                // 上课那一刻先收干净，之后挂哪条交给对账
                if (type == ReminderScheduler.TYPE_CLASS_START) ReminderNotifier.cancelAll(context)
                ReminderRuntime.sync(
                    context = context,
                    reminder = data.reminder,
                    today = courses,
                    now = now,
                    periodic = type == ReminderScheduler.TYPE_LIVE_TICK,
                )
            }
        }
    }
}

/** 系统重排的时机：重启、应用更新会丢掉全部闹钟，改时间/换时区会让已注册的绝对时刻失准 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val app = context.applicationContext
        // 重启与更新后，接管勿扰前的原状态已经无意义
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ClassDnd.clear(app)
        }
        receiveAsync { ReminderScheduler.resyncNow(app) }
    }

    private companion object {
        val HANDLED =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}

/** 解析课表要读数据库，广播里必须异步做完再放行进程 */
private fun BroadcastReceiver.receiveAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            block()
        } catch (error: Throwable) {
            Log.w(TAG, "处理课程提醒失败", error)
        } finally {
            pending.finish()
        }
    }
}
