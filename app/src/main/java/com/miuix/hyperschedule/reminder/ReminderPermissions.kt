package com.miuix.hyperschedule.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** 课程提醒依赖的系统权限：查询状态、跳系统授权页 */
object ReminderPermissions {

    /** 页面展示用的权限快照，进入页面与回到前台各取一次 */
    data class State(
        val notifications: Boolean = false,
        val promoted: Boolean = false,
        val exactAlarm: Boolean = false,
        val batteryUnrestricted: Boolean = false,
        val dnd: Boolean = false,
    )

    fun read(context: Context) =
        State(
            notifications = notificationsEnabled(context),
            promoted = canPostPromotedNotifications(context),
            exactAlarm = canScheduleExactAlarms(context),
            batteryUnrestricted = isIgnoringBatteryOptimizations(context),
            dnd = hasDndAccess(context),
        )

    /** 实况通知（promoted notifications）设置页只有 API 36+ 才有 */
    val supportsPromotedNotifications: Boolean
        get() = Build.VERSION.SDK_INT >= 36

    /** 通知总开关：13 起的运行时权限与系统通知开关任一不满足都发不出通知 */
    fun notificationsEnabled(context: Context): Boolean {
        val runtimeGranted =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 通知是否允许「置顶/实况」展示；低于 36 直接当作不支持 */
    fun canPostPromotedNotifications(context: Context): Boolean {
        if (!supportsPromotedNotifications) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canPostPromotedNotifications()
    }

    /** 精确闹钟：31 起需要单独授权，更早的版本默认允许 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /** 是否已把本应用排除在电池优化之外 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 勿扰（免打扰访问）权限 */
    fun hasDndAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.isNotificationPolicyAccessGranted
    }

    fun openNotificationSettings(context: Context) =
        start(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )

    /** 首选实况通知管理页，打不开就退回普通的应用通知设置 */
    fun openPromotedNotificationSettings(context: Context) {
        val opened =
            supportsPromotedNotifications &&
                tryStart(context, Intent(ACTION_MANAGE_PROMOTED_NOTIFICATIONS).setData(packageUri(context)))
        if (!opened) openNotificationSettings(context)
    }

    /** 精确闹钟设置页只在 31 起存在，低版本落到应用详情 */
    fun openExactAlarmSettings(context: Context) {
        val opened =
            Build.VERSION.SDK_INT >= 31 &&
                tryStart(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(packageUri(context)))
        if (!opened) openAppDetailsSettings(context)
    }

    /** 首选「请求忽略电池优化」，打不开就退回电池优化白名单列表 */
    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        val opened =
            tryStart(
                context,
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri(context)),
            )
        if (!opened) {
            tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openDndAccessSettings(context: Context) =
        start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    /** 自启动没有统一的系统页，只能落到应用详情让用户自己找 */
    fun openAppDetailsSettings(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(packageUri(context)))

    private fun packageUri(context: Context): Uri = "package:${context.packageName}".toUri()

    /** 非 Activity 的 Context 启动 Activity 必须带 NEW_TASK，否则直接抛异常 */
    private fun start(context: Context, intent: Intent) {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 系统页不存在时 startActivity 会抛 [android.content.ActivityNotFoundException]，这里换成返回值 */
    private fun tryStart(context: Context, intent: Intent): Boolean =
        runCatching { start(context, intent) }.isSuccess
}

private const val ACTION_MANAGE_PROMOTED_NOTIFICATIONS = "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
