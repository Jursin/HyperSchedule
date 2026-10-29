package com.miuix.hyperschedule.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import kotlinx.coroutines.launch

/** 新建课表 */
@Composable
fun CreateTableDialog(
    repository: ScheduleRepository,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onCreated: (tableId: String) -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    TableNameDialog(
        title = "新建课表",
        appBlur = appBlur,
        onDismiss = { if (!creating) onDismiss() },
        onConfirm = { name ->
            if (creating) return@TableNameDialog
            creating = true
            scope.launch {
                runCatching { repository.createTable(name) }
                    .onSuccess(onCreated)
                    .onFailure {
                        creating = false
                        context.toast("新建课表失败：${it.message ?: "未知错误"}")
                        onFailed()
                    }
            }
        },
    )
}
