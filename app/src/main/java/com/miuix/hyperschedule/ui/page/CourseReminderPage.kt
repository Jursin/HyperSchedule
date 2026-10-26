package com.miuix.hyperschedule.ui.page

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.miuix.hyperschedule.reminder.ReminderPermissions
import com.miuix.hyperschedule.theme.ReminderSettings
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.ui.component.NumberPickerDialog
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.ValueLabel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GrantedColor = Color(0xFF4CAF50)

@Composable
fun CourseReminderPage(
    store: SettingsStore,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val reminder = store.state.reminder

    var showMinutesDialog by remember { mutableStateOf(false) }
    var tempMinuteIndex by remember { mutableIntStateOf(0) }

    var perms by remember(context) { mutableStateOf(ReminderPermissions.read(context)) }
    // 用户去系统设置授权后回来要立刻反映，否则状态一直是旧的
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) perms = ReminderPermissions.read(context)
            }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            perms = ReminderPermissions.read(context)
        }

    /** 13 起先弹运行时权限，用户拒过之后只能落到系统通知设置 */
    fun requestNotification() {
        val needsRuntimePermission =
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        when {
            needsRuntimePermission -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            !perms.notifications -> ReminderPermissions.openNotificationSettings(context)
        }
    }

    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        store.update { state -> state.copy(reminder = transform(state.reminder)) }
    }

    val master = reminder.enabled

    PreferenceScreen(title = "课程提醒", appBlur = appBlur, onBack = onBack) {
        PreferenceSection {
            SwitchPreference(
                checked = master,
                onCheckedChange = { checked -> update { it.copy(enabled = checked) } },
                title = "开启提醒",
            )
            if (master) {
                ArrowPreference(
                    title = "课前提醒时间",
                    endActions = { ValueLabel(ReminderSettings.preMinuteLabel(reminder.preMinutes)) },
                    onClick = {
                        tempMinuteIndex = ReminderSettings.preMinuteIndexOf(reminder.preMinutes)
                        showMinutesDialog = true
                    },
                )
            }
            SwitchPreference(
                checked = reminder.inClass,
                onCheckedChange = { checked -> update { it.copy(inClass = checked) } },
                title = "课中提醒",
                summary = "上课中显示距离下课的时间",
                enabled = master,
            )
            SwitchPreference(
                checked = reminder.autoDnd,
                onCheckedChange = { checked -> update { it.copy(autoDnd = checked) } },
                title = "自动开启勿扰",
                summary = "上课时自动开启勿扰，下课后自动恢复",
                enabled = master,
            )
            // 总开关关掉时子项都不可用，档位跟着一起收起
            if (master && reminder.autoDnd) {
                OverlayDropdownPreference(
                    title = "勿扰模式档位",
                    items = ReminderSettings.DND_MODE_LABELS,
                    selectedIndex = reminder.dndMode,
                    onSelectedIndexChange = { index -> update { it.copy(dndMode = index) } },
                )
            }
        }

        PreferenceSection("通知与权限") {
            ArrowPreference(
                title = "允许通知",
                summary = "用于推送课程提醒通知",
                endActions = { GrantedLabel(perms.notifications) },
                onClick = { requestNotification() },
            )
            if (ReminderPermissions.supportsPromotedNotifications) {
                ArrowPreference(
                    title = "实时动态",
                    summary = "用于通知栏置顶显示实时通知",
                    endActions = { GrantedLabel(perms.promoted) },
                    onClick = {
                        if (!perms.promoted) ReminderPermissions.openPromotedNotificationSettings(context)
                    },
                )
            }
            ArrowPreference(
                title = "精确闹钟权限",
                summary = "确保提醒准时触发，不受省电策略影响",
                endActions = { GrantedLabel(perms.exactAlarm) },
                onClick = {
                    if (!perms.exactAlarm) ReminderPermissions.openExactAlarmSettings(context)
                },
            )
            ArrowPreference(
                title = "电池优化",
                summary = "关闭电池优化以确保提醒准时送达",
                endActions = { GrantedLabel(perms.batteryUnrestricted, grantedText = "已关闭", deniedText = "未关闭") },
                onClick = {
                    if (!perms.batteryUnrestricted) ReminderPermissions.openBatteryOptimizationSettings(context)
                },
            )
            ArrowPreference(
                title = "勿扰权限",
                summary = "用于上课时自动开启勿扰",
                endActions = { GrantedLabel(perms.dnd) },
                onClick = {
                    if (!perms.dnd) ReminderPermissions.openDndAccessSettings(context)
                },
            )
            ArrowPreference(
                title = "自启动",
                summary = "在应用被系统清理后台后及时重启",
                endActions = { ValueLabel("前往检查") },
                onClick = { ReminderPermissions.openAppDetailsSettings(context) },
            )
        }
    }

    if (showMinutesDialog) {
        NumberPickerDialog(
            title = "课前提醒时间",
            value = tempMinuteIndex,
            onValueChange = { tempMinuteIndex = it },
            range = ReminderSettings.PRE_MINUTE_OPTIONS.indices,
            label = { ReminderSettings.preMinuteLabel(ReminderSettings.PRE_MINUTE_OPTIONS[it]) },
            appBlur = appBlur,
            onDismiss = { showMinutesDialog = false },
            onConfirm = {
                update { it.copy(preMinutes = ReminderSettings.PRE_MINUTE_OPTIONS[tempMinuteIndex]) }
                showMinutesDialog = false
            },
        )
    }
}

@Composable
private fun GrantedLabel(
    granted: Boolean,
    grantedText: String = "已授权",
    deniedText: String = "未授权",
) {
    Text(
        text = if (granted) grantedText else deniedText,
        style = MiuixTheme.textStyles.body2,
        color = if (granted) GrantedColor else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

/** 从任意 Context 向外找到持有生命周期的宿主（Activity），用来监听回到前台 */
private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? =
    when (this) {
        is LifecycleOwner -> this
        is ContextWrapper -> baseContext.findLifecycleOwner()
        else -> null
    }
