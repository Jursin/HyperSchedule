package com.hyperschedule.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.hyperschedule.app.theme.ReminderSettings
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

internal object ReminderScheduler {

    const val ACTION_REMINDER = "com.hyperschedule.app.action.REMINDER"
    const val EXTRA_TYPE = "type"
    const val EXTRA_DAY = "day"
    const val EXTRA_COURSE_ID = "course_id"

    const val TYPE_PRE_CLASS = 1

    /** 上课，课中通知与勿扰的开始 */
    const val TYPE_CLASS_START = 2

    /** 下课，课中通知与勿扰的结束 */
    const val TYPE_CLASS_END = 3

    const val TYPE_DAY_ROLLOVER = 4

    /** 实时动态按分钟对账，进度条与「还剩几分钟」都靠它走 */
    const val TYPE_LIVE_TICK = 5

    /** 一天最多排这么多节课，超出的不管（正常课表不会到这个量级） */
    private const val MAX_COURSES_PER_DAY = 32

    private const val ROLLOVER_REQUEST_CODE = 900_000
    private const val LIVE_TICK_REQUEST_CODE = 900_001
    private const val ROLLOVER_HOUR = 0
    private const val ROLLOVER_MINUTE = 5

    /** 对账间隔：allow-while-idle 闹钟同一应用约一分钟只放一次，留出余量免得被系统一路推迟 */
    private const val LIVE_TICK_INTERVAL_SECONDS = 65L

    /** 重排请求合并窗口，连点开关、连续保存课程时只跑最后一次 */
    private const val RESYNC_DEBOUNCE_MILLIS = 300L

    /** 已登记闹钟的存放处，进程被杀后也知道该撤哪些 */
    private const val PREFS_ALARMS = "reminder_alarms"
    private const val KEY_CODES = "scheduled_codes"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    @Volatile
    private var pending: Job? = null

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
        ShizukuAccess.init(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        cancelScheduled(context, alarmManager)
        val data = ReminderData.load(context)
        // 提醒关掉（或还没有可用课表）时，先把已发的通知与勿扰状态收干净
        if (data == null || !data.reminder.enabled) {
            ReminderRuntime.reset(context)
            return
        }
        ReminderNotifier.ensureChannels(context)
        val now = LocalDateTime.now()
        val today = data.coursesOn(now.toLocalDate())
        ReminderCache.put(now.toLocalDate(), today, data)
        val codes = mutableSetOf<Int>()
        scheduleDay(context, alarmManager, data.reminder, 0, today, now, codes)
        scheduleDay(
            context = context,
            alarmManager = alarmManager,
            reminder = data.reminder,
            dayOffset = 1,
            courses = data.coursesOn(now.toLocalDate().plusDays(1)),
            now = now,
            codes = codes,
        )
        setAlarm(
            context = context,
            alarmManager = alarmManager,
            type = TYPE_DAY_ROLLOVER,
            requestCode = ROLLOVER_REQUEST_CODE,
            at = now.toLocalDate().plusDays(1).atTime(ROLLOVER_HOUR, ROLLOVER_MINUTE),
            day = now.toLocalDate().plusDays(1),
            course = null,
        )?.let { codes += it }
        saveCodes(context, codes)
        // 应用是被用户打开或闹钟触发的，此刻可能正上着课，顺手对齐一次课中状态
        ReminderRuntime.sync(context, data.reminder, today, now)
    }

    /** 拆出来给测试断言闹钟时刻，正式路径走 [resyncNow] */
    internal fun scheduleDay(
        context: Context,
        alarmManager: AlarmManager?,
        reminder: ReminderSettings,
        dayOffset: Int,
        courses: List<DayCourse>,
        now: LocalDateTime,
        codes: MutableSet<Int>,
    ) {
        // 上一节的下课时刻，课前提醒不能比它更早，否则会响在上一节课里
        var previousEnd: LocalDateTime? = null
        courses.take(MAX_COURSES_PER_DAY).forEachIndexed { index, course ->
            val day = course.start.toLocalDate()
            preClassTime(course, previousEnd, reminder.preMinutes)?.takeIf { it.isAfter(now) }?.let { at ->
                setAlarm(
                    context,
                    alarmManager,
                    TYPE_PRE_CLASS,
                    requestCode(dayOffset, TYPE_PRE_CLASS, index),
                    at,
                    day,
                    course,
                )?.let { codes += it }
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
                )?.let { codes += it }
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
                )?.let { codes += it }
            }
            if (end != null) {
                val lastEnd = previousEnd
                previousEnd = if (lastEnd == null || end.isAfter(lastEnd)) end else lastEnd
            }
        }
    }

    /** 课前提醒的时刻，提前量落在上一节课里就压到上一节下课那一刻，连堂课没有课间就不提醒 */
    private fun preClassTime(course: DayCourse, previousEnd: LocalDateTime?, minutes: Int): LocalDateTime? {
        if (minutes <= 0) return null
        val target = course.start.minusMinutes(minutes.toLong())
        if (previousEnd == null || !target.isBefore(previousEnd)) return target
        return previousEnd.takeIf { it.isBefore(course.start) }
    }

    /** 请求码按「第几天 × 类型 × 序号」编码，登记下来，下次重排只撤真正排过的那些 */
    private fun requestCode(dayOffset: Int, type: Int, index: Int): Int =
        dayOffset * 100_000 + type * 1_000 + index

    /** 排下一次实时动态对账，从上一次落点往后推，收起时由 [cancelLiveTick] 撤掉 */
    fun scheduleLiveTick(context: Context, now: LocalDateTime) {
        setAlarm(
            context = context,
            alarmManager = context.getSystemService(AlarmManager::class.java),
            type = TYPE_LIVE_TICK,
            requestCode = LIVE_TICK_REQUEST_CODE,
            at = now.plusSeconds(LIVE_TICK_INTERVAL_SECONDS),
            day = now.toLocalDate(),
            course = null,
        )
    }

    fun cancelLiveTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancel(context, alarmManager, LIVE_TICK_REQUEST_CODE)
    }

    /** 上次重排登记下来的请求码，只为这些建过 PendingIntent */
    private fun loadCodes(context: Context): Set<Int> =
        context.getSharedPreferences(PREFS_ALARMS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CODES, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

    private fun saveCodes(context: Context, codes: Set<Int>) {
        context.getSharedPreferences(PREFS_ALARMS, Context.MODE_PRIVATE)
            .edit { putStringSet(KEY_CODES, codes.map { it.toString() }.toSet()) }
    }

    /** 只撤上次登记过的闹钟 */
    private fun cancelScheduled(context: Context, alarmManager: AlarmManager?) {
        alarmManager ?: return
        loadCodes(context).forEach { cancel(context, alarmManager, it) }
        cancel(context, alarmManager, LIVE_TICK_REQUEST_CODE)
        saveCodes(context, emptySet())
    }

    /** 取消只针对已经存在的闹钟，FLAG_NO_CREATE 既不会顺路新建，也不会抹掉上一次带上的附加信息 */
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
    ): Int? {
        alarmManager ?: return null
        val intent =
            reminderIntent(context)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_DAY, day.toString())
                .apply { if (course != null) putExtra(EXTRA_COURSE_ID, course.id) }
        val pending = pendingIntent(context, requestCode, PendingIntent.FLAG_UPDATE_CURRENT, intent) ?: return null
        // 没有精确闹钟权限时退回允许休眠的普通闹钟，可能晚几分钟，但不至于完全不响
        runCatching {
            if (ReminderPermissions.canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMillis(), pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMillis(), pending)
            }
        }
        return requestCode
    }

    /** 取消与注册共用同一个 Intent，PendingIntent 只按 action + 目标类匹配，附加信息不参与 */
    private fun reminderIntent(context: Context): Intent =
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER)

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        flags: Int,
        intent: Intent = reminderIntent(context),
    ): PendingIntent? = PendingIntent.getBroadcast(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)
}
