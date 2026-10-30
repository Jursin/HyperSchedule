package com.miuix.hyperschedule.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.miuix.hyperschedule.R
import com.miuix.hyperschedule.reminder.notificationManager
import com.miuix.hyperschedule.reminder.openApp

/** 下载进度与完成通知 */
internal object UpdateNotification {
    const val ID = 0x1A20

    private const val ID_DONE = 0x1A21

    private const val CHANNEL = "app_update"

    /** 通道建过一次就够；下载每 1% 都查一次是白跑的系统调用 */
    @Volatile private var channelReady = false

    fun show(context: Context, progress: Float) {
        ensureChannel(context)
        val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
        val notification =
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("正在下载 · $percent%")
                .setContentIntent(openApp(context))
                .setProgress(100, percent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(cancelAction(context))
                .build()
        UpdateForegroundService.show(context, notification)
    }

    /** 后台下载完成，点通知回前台接续安装 */
    fun showDone(context: Context, version: String) {
        ensureChannel(context)
        // 停掉前台服务时系统会连带移除它挂的进度通知
        UpdateForegroundService.stop(context)
        notificationManager(context)
            ?.notify(
                ID_DONE,
                Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(if (version.isBlank()) "下载完成" else "下载完成 · v$version")
                    .setContentText("点击安装")
                    .setContentIntent(openApp(context))
                    .setAutoCancel(true)
                    .build(),
            )
    }

    fun cancel(context: Context) {
        UpdateForegroundService.stop(context)
        val manager = notificationManager(context) ?: return
        manager.cancel(ID)
        manager.cancel(ID_DONE)
    }

    private fun cancelAction(context: Context): Notification.Action {
        val intent =
            Intent(context, UpdateForegroundService::class.java)
                .setAction(UpdateForegroundService.ACTION_CANCEL)
        val pending =
            PendingIntent.getService(
                context,
                1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val icon = Icon.createWithResource(context, R.drawable.ic_notification)
        return Notification.Action.Builder(icon, "取消下载", pending).build()
    }

    private fun ensureChannel(context: Context) {
        if (channelReady) return
        val manager = notificationManager(context) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "应用更新", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "下载更新期间显示下载进度"
                    setShowBadge(false)
                },
            )
        }
        channelReady = true
    }
}

/** 下载期间维持前台服务与 WakeLock，避免后台被冻结 */
class UpdateForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())

    private val renewWakeLock =
        object : Runnable {
            override fun run() {
                acquireWakeLock()
                handler.postDelayed(this, WAKE_LOCK_RENEW)
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            onCancel?.invoke()
            return START_NOT_STICKY
        }
        val notification =
            intent?.let {
                IntentCompat.getParcelableExtra(it, EXTRA_NOTIFICATION, Notification::class.java)
            }
        if (notification == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(renewWakeLock)
        renewWakeLock.run()
        ServiceCompat.startForeground(this, UpdateNotification.ID, notification, foregroundType())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(renewWakeLock)
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    private fun acquireWakeLock() {
        val lock =
            wakeLock
                ?: getSystemService(PowerManager::class.java)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HyperSchedule:update")
                    ?.apply { setReferenceCounted(false) }
                ?: return
        wakeLock = lock
        runCatching { lock.acquire(WAKE_LOCK_TIMEOUT) }
    }

    companion object {
        /** 通知栏「取消下载」按钮的动作 */
        const val ACTION_CANCEL = "com.miuix.hyperschedule.action.CANCEL_UPDATE"

        /** 由 UpdateController 注册，收到取消动作时回调 */
        @Volatile
        var onCancel: (() -> Unit)? = null

        private const val EXTRA_NOTIFICATION = "com.miuix.hyperschedule.extra.UPDATE_NOTIFICATION"
        private const val WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L
        private const val WAKE_LOCK_RENEW = 9 * 60 * 1000L

        /** 下载中启动或刷新前台服务 */
        fun show(context: Context, notification: Notification) {
            val intent =
                Intent(context, UpdateForegroundService::class.java)
                    .putExtra(EXTRA_NOTIFICATION, notification)
            ContextCompat.startForegroundService(context, intent)
        }

        /** 下载结束或停止时关闭前台服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, UpdateForegroundService::class.java))
        }
    }
}
