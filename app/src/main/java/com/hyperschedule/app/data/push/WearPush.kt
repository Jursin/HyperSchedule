package com.hyperschedule.app.data.push

import android.content.Context
import android.util.Log
import com.hyperschedule.app.data.db.entity.Course
import com.hyperschedule.app.data.db.entity.CourseWithWeeks
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.db.entity.TimeTable
import com.hyperschedule.app.data.importer.normalizePosition
import com.hyperschedule.app.data.model.HolidayEntry
import com.hyperschedule.app.schedule.HolidayPlan
import com.hyperschedule.app.schedule.formatMinutes
import com.hyperschedule.app.schedule.parseMinutes
import com.hyperschedule.app.schedule.semesterStartMonday
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.exception.AppNotInstalledException
import com.xiaomi.xms.wearable.exception.DeviceDisconnectedException
import com.xiaomi.xms.wearable.exception.PermissionDeniedException
import com.xiaomi.xms.wearable.exception.SignatureVerifyFailedException
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.tasks.Task
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "WearPush"

/** 设备可识别的课表结构，字段即发送白名单 */
@Serializable
data class WearSchedule(
    val courses: List<WearCourse>,
    val timeSlots: List<WearTimeSlot>,
    val config: WearConfig? = null,
)

@Serializable
data class WearCourse(
    val name: String,
    val day: Int,
    val isCustomTime: Boolean,
    val teacher: String? = null,
    val position: String? = null,
    val remark: String? = null,
    val weeks: List<Int>? = null,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val startSection: Int? = null,
    val endSection: Int? = null,
)

@Serializable
data class WearTimeSlot(
    val number: Int,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class WearConfig(
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int? = null,
    val morningAfternoonSplit: String? = null,
    val afternoonEveningSplit: String? = null,
    val preClassMinutes: Int? = null,
)

/** 空字段不写进 JSON，设备按缺失处理 */
private val WearJson =
    Json {
        encodeDefaults = false
        explicitNulls = false
    }

fun encodeWearSchedule(schedule: WearSchedule): String = WearJson.encodeToString(WearSchedule.serializer(), schedule)

/** 统一格式并把非法值当作未设置 */
private fun String?.normalizedClock(): String? {
    val minutes = parseMinutes(this?.trim()) ?: return null
    return if (minutes in 0..23 * 60 + 59) formatMinutes(minutes) else null
}

fun buildWearSchedule(
    courses: List<CourseWithWeeks>,
    slots: List<TimeSlot>,
    timeTable: TimeTable?,
    startDate: LocalDate?,
    lastWeek: Int,
    preClassMinutes: Int,
    entries: List<HolidayEntry> = emptyList(),
): WearSchedule {
    val plan =
        if (startDate != null && entries.isNotEmpty()) {
            HolidayPlan.build(startDate, lastWeek, entries)
        } else {
            HolidayPlan.Empty
        }
    // 被调休占用的格子换成别的星期甚至别的周的课，先算出源到目标的映射
    val targets = if (plan === HolidayPlan.Empty) null else holidayTargets(plan, lastWeek)

    val result = mutableListOf<WearCourse>()
    courses.forEach { group ->
        val weeks = group.weeks.map { it.weekNumber }.filter { it > 0 }
        if (weeks.isEmpty()) return@forEach
        // 同一门课被调休拆到多个星期时，按星期分组各发一条
        val byDay = LinkedHashMap<Int, MutableSet<Int>>()
        weeks.forEach { week ->
            val cells = targets?.get(week * 10 + group.course.day) ?: listOf(week to group.course.day)
            cells.forEach { (targetWeek, targetDay) ->
                byDay.getOrPut(targetDay) { sortedSetOf() } += targetWeek
            }
        }
        byDay.forEach { (day, weeksOfDay) ->
            group.course.toWearCourse(day, weeksOfDay.toList())?.let { result += it }
        }
    }
    return WearSchedule(
        courses = result,
        timeSlots =
            slots
                .mapNotNull { slot ->
                    if (slot.number <= 0) return@mapNotNull null
                    val start = slot.startTime.normalizedClock() ?: return@mapNotNull null
                    val end = slot.endTime.normalizedClock() ?: return@mapNotNull null
                    WearTimeSlot(slot.number, start, end)
                }
                .sortedBy { it.number },
        config =
            WearConfig(
                // 手表从开学日期起每满 7 天算一周，归一成周一才与手机一致
                semesterStartDate = startDate?.let { semesterStartMonday(it).toString() },
                semesterTotalWeeks = lastWeek,
                // 时段分隔随作息表下发
                morningAfternoonSplit =
                    timeTable?.morningAfternoonSplit.normalizedClock() ?: TimeTable.DEFAULT_MORNING_SPLIT,
                afternoonEveningSplit =
                    timeTable?.afternoonEveningSplit.normalizedClock() ?: TimeTable.DEFAULT_EVENING_SPLIT,
                // 课前倒计时的窗口随提醒设置下发
                preClassMinutes = preClassMinutes.coerceAtLeast(0),
            ),
    )
}

private fun holidayTargets(
    plan: HolidayPlan,
    lastWeek: Int,
): Map<Int, List<Pair<Int, Int>>> {
    val targets = HashMap<Int, MutableList<Pair<Int, Int>>>()
    for (week in 1..lastWeek) {
        for (day in 1..7) {
            val cell = plan.of(week, day)
            val key = week * 10 + day
            if (cell.holiday) {
                targets.getOrPut(key) { mutableListOf() }
                continue
            }
            val source = if (cell.remapped) cell.displayWeek * 10 + cell.displayDay else key
            if (source != key) targets.getOrPut(key) { mutableListOf() }
            targets.getOrPut(source) { mutableListOf() } += week to day
        }
    }
    return targets
}

/** 星期或上课时间不完整的课程不发 */
private fun Course.toWearCourse(
    day: Int,
    weeks: List<Int>,
): WearCourse? {
    val courseName = name.trim()
    if (courseName.isEmpty() || day !in 1..7 || weeks.isEmpty()) return null
    val teacherText = teacher.trim().takeIf { it.isNotEmpty() }
    val positionText = position.normalizePosition().takeIf { it.isNotEmpty() }
    val remarkText = remark?.trim()?.takeIf { it.isNotEmpty() }
    if (isCustomTime) {
        val start = customStartTime.normalizedClock() ?: return null
        val end = customEndTime.normalizedClock() ?: return null
        return WearCourse(
            name = courseName,
            day = day,
            isCustomTime = true,
            teacher = teacherText,
            position = positionText,
            remark = remarkText,
            weeks = weeks,
            customStartTime = start,
            customEndTime = end,
        )
    }
    val start = startSection?.takeIf { it > 0 } ?: return null
    val end = endSection?.takeIf { it >= start } ?: return null
    return WearCourse(
        name = courseName,
        day = day,
        isCustomTime = false,
        teacher = teacherText,
        position = positionText,
        remark = remarkText,
        weeks = weeks,
        startSection = start,
        endSection = end,
    )
}

/** 连接查询、权限申请与消息发送 */
class WearSync(context: Context) {
    private val appContext = context.applicationContext
    private val nodeApi by lazy { Wearable.getNodeApi(appContext) }
    private val messageApi by lazy { Wearable.getMessageApi(appContext) }
    private val authApi by lazy { Wearable.getAuthApi(appContext) }

    /** 当前连接的第一台设备，未连接时为 null */
    suspend fun connectedNode(): Node? = nodeApi.connectedNodes.await()?.firstOrNull()

    /** 权限已授予直接返回 true，否则申请一次；查询失败就当没授权去申请 */
    suspend fun ensurePermission(nodeId: String): Boolean =
        runCatching { authApi.checkPermission(nodeId, Permission.DEVICE_MANAGER).await() == true }
            .getOrDefault(false) ||
            authApi.requestPermission(nodeId, Permission.DEVICE_MANAGER).await().orEmpty().isNotEmpty()

    /** 设备是否装了对应快应用 */
    suspend fun isWearAppInstalled(nodeId: String): Boolean = nodeApi.isWearAppInstalled(nodeId).await() == true

    /** 拉起快应用，uri 对应快应用 router 里的页面 */
    suspend fun launchWearApp(
        nodeId: String,
        uri: String = "pages/index",
    ) {
        nodeApi.launchWearApp(nodeId, uri).await()
    }

    /** 把 JSON 发给设备，失败抛异常 */
    suspend fun send(
        nodeId: String,
        payload: String,
    ) {
        messageApi.sendMessage(nodeId, payload.toByteArray()).await()
    }
}

/** 挂起等待穿戴 SDK 的回调，失败按原异常抛出，成功结果可能为空 */
private suspend fun <T : Any> Task<T>.await(): T? =
    suspendCancellableCoroutine { continuation ->
        // 协程取消后回调仍可能到来，此时 resume 会抛异常
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }

/** 把穿戴 SDK 抛出的异常翻译成可读原因，原始信息同时打到 logcat */
fun describeWearError(error: Throwable): String {
    Log.w(TAG, "穿戴接口失败：${error.javaClass.simpleName} ${error.message}", error)
    return when (error) {
        is SignatureVerifyFailedException -> "快应用签名与本应用不一致"
        is AppNotInstalledException -> "未安装快应用"
        is DeviceDisconnectedException -> "已断开连接"
        is PermissionDeniedException -> "未授权本应用"
        // 超时、中断等状态没有专属异常类，SDK 会原样抛回英文文案
        else -> error.message?.takeIf { it.isNotBlank() } ?: "未知错误（${error.javaClass.simpleName}）"
    }
}
