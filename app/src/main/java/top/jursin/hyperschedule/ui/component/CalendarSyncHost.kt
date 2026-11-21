package top.jursin.hyperschedule.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import top.jursin.hyperschedule.data.exporter.CalendarSync
import top.jursin.hyperschedule.schedule.cjkDigitGap
import kotlinx.coroutines.launch

/** 长按 ICS 导出条目触发，[request] 打开确认弹窗 */
@Stable
class CalendarSyncState internal constructor() {
    internal var visible by mutableStateOf(false)

    fun request() {
        visible = true
    }
}

@Composable
fun rememberCalendarSyncState(): CalendarSyncState = remember { CalendarSyncState() }

/** 挂在页面里，负责确认弹窗、权限申请与结果提示 */
@Composable
fun CalendarSyncHost(state: CalendarSyncState, appBlur: Boolean, tableId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun run() {
        state.visible = false
        scope.launch {
            CalendarSync.sync(context, tableId).fold(
                onSuccess = { count ->
                    val text = if (count == 0) "课表里没有可同步的课程" else "已同步${count}节课到系统日历".cjkDigitGap()
                    context.toast(text)
                },
                onFailure = { context.toast("同步失败：${it.message ?: "无法写入日历"}") },
            )
        }
    }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) run() else context.toast("未授予日历权限")
        }

    if (!state.visible) return
    ConfirmDialog(
        title = "同步到系统日历",
        message = "将课表写入到系统日历，重复同步会覆盖上一次的数据。",
        appBlur = appBlur,
        confirmText = "同步",
        onDismiss = { state.visible = false },
        onConfirm = {
            if (CalendarSync.hasPermission(context)) run() else permission.launch(CalendarSync.permissions)
        },
    )
}
