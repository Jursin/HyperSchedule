package com.miuix.hyperschedule.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.miuix.hyperschedule.theme.ReminderSettings
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** 课程提醒的闹钟调度 */
internal object ReminderScheduler {

    const val ACTION_REMINDER = "com.miuix.hyperschedule.action.REMINDER"
    const val EXTRA_TYPE = "type"
    const val EXTRA_DAY = "day"
    const val EXTRA_COURSE_ID = "course_id"

    /** 课前提醒 */
    const val TYPE_PRE_CLASS = 1

    /** 上课：课中通知与勿扰的开始 */
    const val TYPE_CLASS_START = 2

    /** 下课：课中通知与勿扰的结束 */
    const val TYPE_CLASS_END = 3

    /** 跨零点重排 */
    const val TYPE_DAY_ROLLOVER = 4

    /** 实时动态按分钟对账：进度条与「还剩几分钟」都靠它走 */
    const val TYPE_LIVE_TICK = 5

    /** 一天最多排这么多节课，超出的不管（正常课表不会到这个量级） */
    private const val MAX_COURSES_PER_DAY = 32

    private const val ROLLOVER_REQUEST_CODE = 900_000
    private const val LIVE_TICK_REQUEST_CODE = 900_001
    private const val ROLLOVER_HOUR = 0
    private const val ROLLOVER_MINUTE = 5

    /** 重排请求合并窗口：连点开关、连续保存课程时只跑最后一次 */
    private const val RESYNC_DEBOUNCE_MILLIS = 300L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    @Volatile
    private var pending: Job? = null

    /** 请求重排 */
    fun resync(context: Context) {
        val app = context.applicationContext
        pending?.cancel()
        pending =
            scope.launch {
                delay(RESYNC_DEBOUNCE_MILLIS.milliseconds)
                lock.withLock { withContext(NonCancellable) { resyncNow(app) } }
            }
    }

    suspend fun resyncNow(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        cancelAll(context, alarmManager)
        val data = ReminderData.load(context)
        // 提醒关掉（或还没有可用课表）时，先把已发的通知与勿扰状态收干净
        if (data == null || !data.reminder.enabled) {
            ReminderRuntime.reset(context)
            return
        }
        ReminderNotifier.ensureChannels(context)
        val now = LocalDateTime.now()
        val today = data.coursesOn(now.toLocalDate())
        scheduleDay(context, alarmManager, data.reminder, 0, today, now)
        scheduleDay(context, alarmManager, data.reminder, 1, data.coursesOn(now.toLocalDate().plusDays(1)), now)
        setAlarm(
            context = context,
            alarmManager = alarmManager,
            type = TYPE_DAY_ROLLOVER,
            requestCode = ROLLOVER_REQUEST_CODE,
            at = now.toLocalDate().plusDays(1).atTime(ROLLOVER_HOUR, ROLLOVER_MINUTE),
            day = now.toLocalDate().plusDays(1),
            course = null,
        )
        // 应用是被用户打开或闹钟触发的，此刻可能正上着课：顺手对齐一次课中状态
        ReminderRuntime.sync(context, data.reminder, today, now)
    }

    private fun scheduleDay(
        context: Context,
        alarmManager: AlarmManager?,
        reminder: ReminderSettings,
        dayOffset: Int,
        courses: List<DayCourse>,
        now: LocalDateTime,
    ) {
        courses.take(MAX_COURSES_PER_DAY).forEachIndexed { index, course ->
            val day = course.start.toLocalDate()
            val remindAt = course.start.minusMinutes(reminder.preMinutes.toLong())
            if (remindAt.isAfter(now)) {
                setAlarm(
                    context,
                    alarmManager,
                    TYPE_PRE_CLASS,
                    requestCode(dayOffset, TYPE_PRE_CLASS, index),
                    remindAt,
                    day,
                    course,
                )
            }
            if (course.start.isAfter(now)) {
                setAlarm(
                    context,
                    alarmManager,
                    TYPE_CLASS_START,
                    requestCode(dayOffset, TYPE_CLASS_START, index),
                    course.start,
                    day,
                    course,
                )
            }
            val end = course.end
            if (end != null && end.isAfter(now)) {
                setAlarm(
                    context,
                    alarmManager,
                    TYPE_CLASS_END,
                    requestCode(dayOffset, TYPE_CLASS_END, index),
                    end,
                    day,
                    course,
                )
            }
        }
    }

    /** 请求码按「第几天 × 类型 × 序号」编码，取消时按同一套规则全量扫一遍即可 */
    private fun requestCode(dayOffset: Int, type: Int, index: Int): Int =
        dayOffset * 100_000 + type * 1_000 + index

    /** 排下一次实时动态对账：落在下一分钟整点，收起时由 [cancelLiveTick] 撤掉 */
    fun scheduleLiveTick(context: Context, now: LocalDateTime) {
        setAlarm(
            context = context,
            alarmManager = context.getSystemService(AlarmManager::class.java),
            type = TYPE_LIVE_TICK,
            requestCode = LIVE_TICK_REQUEST_CODE,
            at = now.withSecond(0).withNano(0).plusMinutes(1),
            day = now.toLocalDate(),
            course = null,
        )
    }

    fun cancelLiveTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancel(context, alarmManager, LIVE_TICK_REQUEST_CODE)
    }

    private fun cancelAll(context: Context, alarmManager: AlarmManager?) {
        alarmManager ?: return
        for (dayOffset in 0..1) {
            for (type in TYPE_PRE_CLASS..TYPE_CLASS_END) {
                for (index in 0 until MAX_COURSES_PER_DAY) {
                    cancel(context, alarmManager, requestCode(dayOffset, type, index))
                }
            }
        }
        cancel(context, alarmManager, ROLLOVER_REQUEST_CODE)
        cancel(context, alarmManager, LIVE_TICK_REQUEST_CODE)
    }

    /** 取消只针对已经存在的闹钟：FLAG_NO_CREATE 既不会顺路新建，也不会抹掉上一次带上的附加信息 */
    private fun cancel(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val pending = pendingIntent(context, requestCode, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager?,
        type: Int,
        requestCode: Int,
        at: LocalDateTime,
        day: LocalDate,
        course: DayCourse?,
    ) {
        alarmManager ?: return
        val intent =
            reminderIntent(context)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_DAY, day.toString())
                .apply { if (course != null) putExtra(EXTRA_COURSE_ID, course.id) }
        val pending = pendingIntent(context, requestCode, PendingIntent.FLAG_UPDATE_CURRENT, intent) ?: return
        // 没有精确闹钟权限时退回允许休眠的普通闹钟：可能晚几分钟，但不至于完全不响
        runCatching {
            if (ReminderPermissions.canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMillis(), pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMillis(), pending)
            }
        }
    }

    /** 取消与注册共用同一个 Intent：PendingIntent 只按 action + 目标类匹配，附加信息不参与 */
    private fun reminderIntent(context: Context): Intent =
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER)

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        flags: Int,
        intent: Intent = reminderIntent(context),
    ): PendingIntent? = PendingIntent.getBroadcast(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)
}
