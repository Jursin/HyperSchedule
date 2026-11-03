package com.hyperschedule.app.reminder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hyperschedule.app.MainActivity
import com.hyperschedule.app.R
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.schedule.formatMinutes
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/** 提醒只有一条安静常驻的，响铃交给用户在系统里给通道配 */
private const val CHANNEL_LIVE = "class_live"

/** 进度条按百分制给，系统实时动态的样式也认这一套 */
private const val PROGRESS_MAX = 100

internal object ReminderNotifier {

    const val PRE_CLASS_ID = 0x1A11
    const val IN_CLASS_ID = 0x1A12

    /** 实时动态课前倒计时的独立 id */
    private const val LIVE_PRE_CLASS_ID = 0x1A13

    /** 上次真正发出去的内容，内容没变就不再发 */
    private val lastPosted = ConcurrentHashMap<Int, String>()

    fun ensureChannels(context: Context) {
        val manager = notificationManager(context) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, "实时动态", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "课前与课中常驻显示倒计时"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // 上课自动勿扰打开后，用户特意开的提醒不能跟着被一起挡掉
                setBypassDnd(true)
            },
        )
    }

    /** 课前先试岛，失败再走通知 */
    fun notifyPreClass(context: Context, course: DayCourse, now: LocalDateTime) {
        ensureChannels(context)
        cancel(context, IN_CLASS_ID)
        if (SuperIsland.canPost() && SuperIsland.notifyPreClass(context, course, now)) {
            cancel(context, LIVE_PRE_CLASS_ID)
            return
        }
        cancel(context, PRE_CLASS_ID)
        postPreClassCountdown(context, course)
    }

    /** 课中先试岛，失败再走通知 */
    fun notifyInClass(context: Context, course: DayCourse) {
        ensureChannels(context)
        cancel(context, PRE_CLASS_ID)
        cancel(context, LIVE_PRE_CLASS_ID)
        if (SuperIsland.canPost() && SuperIsland.notifyInClass(context, course)) return
        postInClassCountdown(context, course)
    }

    /** 三条一次收干净 */
    fun cancelAll(context: Context) {
        cancel(context, PRE_CLASS_ID)
        cancel(context, LIVE_PRE_CLASS_ID)
        cancel(context, IN_CLASS_ID)
    }

    fun cancel(context: Context, id: Int) {
        lastPosted -= id
        SuperIsland.forget(id)
        runCatching { notificationManager(context)?.cancel(id) }
    }

    /** 课前倒计时指向上课，到上课时刻就让位给课中的那条 */
    private fun postPreClassCountdown(context: Context, course: DayCourse) {
        val startMillis = course.start.toEpochMillis()
        postCountdown(
            context = context,
            id = LIVE_PRE_CLASS_ID,
            course = course,
            targetMillis = startMillis,
            expiresAtMillis = startMillis,
            category = Notification.CATEGORY_REMINDER,
        )
    }

    /** 课中倒计时指向下课 */
    private fun postInClassCountdown(context: Context, course: DayCourse) {
        val endMillis = course.end?.toEpochMillis() ?: return
        if (endMillis <= System.currentTimeMillis()) return
        postCountdown(
            context = context,
            id = IN_CLASS_ID,
            course = course,
            targetMillis = endMillis,
            expiresAtMillis = endMillis,
            category = Notification.CATEGORY_PROGRESS,
            withProgress = true,
        )
    }

    /** 课前与课中共用的倒计时通知，状态条标签交给系统秒表走 */
    @SuppressLint("NewApi")
    private fun postCountdown(
        context: Context,
        id: Int,
        course: DayCourse,
        targetMillis: Long,
        expiresAtMillis: Long,
        category: String,
        withProgress: Boolean = false,
    ) {
        val live = ReminderPermissions.canPostPromotedNotifications(context)
        val progress = if (withProgress) progressPercent(course.start.toEpochMillis(), expiresAtMillis) else 0
        val detail = detailOf(course)
        // 课名与课程 id 都在标题里，改了要重发
        val signature = "${course.id}|${course.name}|$targetMillis|$detail|$live|$progress"
        if (lastPosted[id] == signature) return
        val inClass = category == Notification.CATEGORY_PROGRESS
        // 课前把状态词单独放一行，课中不重复状态词，右边倒计时已经说明在上了
        val lines = liveBodyLines(course, if (inClass) null else "即将上课")
        val builder =
            Notification.Builder(context, CHANNEL_LIVE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openApp(context))
                .setContentTitle(course.name)
                .setContentText(lines.joinToString(" · "))
                .setCategory(category)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                // 系统秒表倒计时到目标时刻
                .setShowWhen(true)
                .setWhen(targetMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter((expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
                .addAction(dndAction(context))
        // 实时动态的卡片按 bigText 显示正文，有两行时靠它折行
        if (lines.size > 1) builder.style = Notification.BigTextStyle().bigText(lines.joinToString("\n"))
        if (live) {
            if (withProgress) builder.style = progressStyle(progress)
            // 申请升格成实时动态，标签里的秒表才会跟着走
            runCatching { builder.setRequestPromotedOngoing(true) }
        }
        val posted = runCatching { notificationManager(context)?.notify(id, builder.build()) }.isSuccess
        if (posted) lastPosted[id] = signature
    }

    private fun progressStyle(progress: Int): Notification.Style {
        if (Build.VERSION.SDK_INT < 36) return Notification.BigTextStyle()
        return Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(PROGRESS_MAX)))
            .setProgress(progress)
    }

    /** 进度按已上课比例算 */
    private fun progressPercent(startMillis: Long, endMillis: Long): Int {
        val totalMillis = endMillis - startMillis
        if (totalMillis <= 0L) return 0
        val elapsed = System.currentTimeMillis() - startMillis
        return ((elapsed.coerceIn(0L, totalMillis) * PROGRESS_MAX) / totalMillis).toInt()
    }

    private fun dndAction(context: Context): Notification.Action =
        Notification.Action.Builder(
            null,
            "上课勿扰",
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ClassDndReceiver::class.java).setAction(ClassDndReceiver.ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()
}

internal fun preClassHead(course: DayCourse): String {
    val minutes = remainOf(course.start.toEpochMillis())
    return if (minutes > 0) "${minutes}分钟后上课".cjkDigitGap() else "现在上课"
}

internal fun inClassHead(endMillis: Long): String = "${remainOf(endMillis)}分钟后下课".cjkDigitGap()

/** 岛上收起状态右侧的「几点下课」 */
internal fun inClassTail(end: LocalDateTime): String =
    "${formatMinutes(end.hour * 60 + end.minute)}下课".cjkDigitGap()

private fun remainOf(endMillis: Long): Int =
    ceil((endMillis - System.currentTimeMillis()) / 60_000.0).toInt().coerceAtLeast(0)

internal fun plainBodyOf(course: DayCourse, head: String): String {
    val detail = detailOf(course)
    return if (detail.isEmpty()) head else "$head · $detail"
}

/** 实时动态的正文按行给 */
internal fun liveBodyLines(course: DayCourse, state: String?): List<String> =
    listOfNotNull(detailOf(course).takeIf { it.isNotEmpty() }, state)

private fun detailOf(course: DayCourse): String =
    listOfNotNull(
        course.position.takeIf { it.isNotBlank() },
        course.teacher.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

internal fun openApp(context: Context): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal fun notificationManager(context: Context): NotificationManager? =
    context.getSystemService(NotificationManager::class.java)
