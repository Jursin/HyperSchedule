package com.miuix.hyperschedule.reminder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import com.miuix.hyperschedule.MainActivity
import com.miuix.hyperschedule.R
import com.miuix.hyperschedule.schedule.cjkDigitGap
import java.time.LocalDateTime
import kotlin.math.ceil

/** 提醒只有一条安静常驻的，响铃交给用户在系统里给通道配 */
private const val CHANNEL_LIVE = "class_live"

/** 进度条按百分制给，系统实时动态的样式也认这一套 */
private const val PROGRESS_MAX = 100

/** 通知的展示与取消 */
internal object ReminderNotifier {

    /** 课前与课中各一条固定 id：同一时间只会有一节课在讲 */
    const val PRE_CLASS_ID = 0x1A11
    const val IN_CLASS_ID = 0x1A12

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

    /** 课前只有一条：先试岛，发不出去再挂实时动态 / 普通通知 */
    fun notifyPreClass(context: Context, course: DayCourse, now: LocalDateTime) {
        ensureChannels(context)
        if (SuperIsland.canPost() && SuperIsland.notifyPreClass(context, course, now)) return
        // 岛没被系统收下：撤掉那条空壳，改走实时动态
        cancel(context, PRE_CLASS_ID)
        postPreClassCountdown(context, course)
    }

    /** 课中：岛能挂就挂岛，其余设备换成下课倒计时 */
    fun notifyInClass(context: Context, course: DayCourse) {
        ensureChannels(context)
        if (SuperIsland.canPost() && SuperIsland.notifyInClass(context, course)) return
        cancel(context, IN_CLASS_ID)
        postInClassCountdown(context, course)
    }

    /** 上课那一刻收起课前那条：岛与实时动态用的是各自的固定 id */
    fun cancelPreClass(context: Context) {
        cancel(context, PRE_CLASS_ID)
        cancel(context, IN_CLASS_ID)
    }

    fun cancel(context: Context, id: Int) {
        runCatching { notificationManager(context)?.cancel(id) }
    }

    /** 课前倒计时挂到下课为止，倒计时指向上课 */
    private fun postPreClassCountdown(context: Context, course: DayCourse) {
        val startMillis = course.start.toEpochMillis()
        postCountdown(
            context = context,
            id = PRE_CLASS_ID,
            course = course,
            head = preClassHead(course),
            targetMillis = startMillis,
            expiresAtMillis = (course.end ?: course.start).toEpochMillis(),
            category = Notification.CATEGORY_REMINDER,
        )
    }

    /** 课中倒计时指向下课，实时动态再多一条进度 */
    private fun postInClassCountdown(context: Context, course: DayCourse) {
        val endMillis = course.end?.toEpochMillis() ?: return
        if (endMillis <= System.currentTimeMillis()) return
        postCountdown(
            context = context,
            id = IN_CLASS_ID,
            course = course,
            head = inClassHead(endMillis),
            targetMillis = endMillis,
            expiresAtMillis = endMillis,
            category = Notification.CATEGORY_PROGRESS,
            withProgress = true,
        )
    }

    /**
     * 课前与课中共用的倒计时通知。
     * 两个提升 API 分别是 36 与 36.1 起才有，老 36.0 上直接跳过，通知照常按实时动态显示。
     */
    @SuppressLint("NewApi")
    private fun postCountdown(
        context: Context,
        id: Int,
        course: DayCourse,
        head: String,
        targetMillis: Long,
        expiresAtMillis: Long,
        category: String,
        withProgress: Boolean = false,
    ) {
        val live = ReminderPermissions.canPostPromotedNotifications(context)
        val body = if (live) liveBodyOf(course, head) else plainBodyOf(course, head)
        val builder =
            Notification.Builder(context, CHANNEL_LIVE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openApp(context))
                .setContentTitle(course.name)
                // 折叠态只放倒计时那行：地点与教师留在展开态，缺失时也不会只剩标题
                .setContentText(if (live) head else body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(category)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setTimeoutAfter((expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
                .addAction(dndAction(context))
        if (live) {
            if (withProgress) {
                val startMillis = course.start.toEpochMillis()
                builder.style = progressStyle(context, progressPercent(startMillis, expiresAtMillis))
            }
            runCatching {
                builder.setShortCriticalText(course.name)
                builder.setRequestPromotedOngoing(true)
            }
        } else {
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
            builder.setShowWhen(true)
            builder.setWhen(targetMillis)
        }
        runCatching { notificationManager(context)?.notify(id, builder.build()) }
    }

    /** 系统的实时动态样式：单段百分制进度 + 应用图标当进度头 */
    private fun progressStyle(context: Context, progress: Int): Notification.Style {
        if (Build.VERSION.SDK_INT < 36) return Notification.BigTextStyle()
        return Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(PROGRESS_MAX)))
            .setProgress(progress)
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_notification))
    }

    /** 进度按已上课的比例算，已过一半时间也不会超出两端 */
    private fun progressPercent(startMillis: Long, endMillis: Long): Int {
        val totalMillis = endMillis - startMillis
        if (totalMillis <= 0L) return 0
        val elapsed = System.currentTimeMillis() - startMillis
        return ((elapsed.coerceIn(0L, totalMillis) * PROGRESS_MAX) / totalMillis).toInt()
    }

    /** 两条通知都只留这一个按钮：上课勿扰，再点一下还给用户 */
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

private fun remainOf(endMillis: Long): Int =
    ceil((endMillis - System.currentTimeMillis()) / 60_000.0).toInt().coerceAtLeast(0)

internal fun liveBodyOf(course: DayCourse, head: String): String {
    val detail = detailOf(course)
    return if (detail.isEmpty()) head else "$detail\n$head"
}

internal fun plainBodyOf(course: DayCourse, head: String): String {
    val detail = detailOf(course)
    return if (detail.isEmpty()) head else "$head · $detail"
}

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
