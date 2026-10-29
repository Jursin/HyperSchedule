package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 教务脚本的 alert / prompt / 单选对话框，页面只负责状态 */
internal data class EduAlertState(
    val title: String,
    val content: String,
    val confirmText: String,
    val onResult: (Boolean) -> Unit,
)

internal data class EduPromptState(
    val title: String,
    val tip: String,
    val defaultText: String,
    /** 脚本给的校验函数名，返回空或 false 表示通过 */
    val validatorJs: String,
    val onResult: (String?) -> Unit,
)

internal data class EduSelectionState(
    val title: String,
    val items: List<String>,
    val defaultIndex: Int,
    val onResult: (Int?) -> Unit,
)

/** 证书校验失败时由用户决定是否继续加载 */
internal class EduSslState(
    val failingUrl: String,
    val onResult: (Boolean) -> Unit,
)

@Composable
internal fun EduPromptDialog(
    title: String,
    tip: String,
    initial: String,
    appBlur: Boolean,
    /** 确认时先过一遍脚本的校验函数，返回非空文本即视为错误提示 */
    validate: (String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = {
            validate(value) { message ->
                if (message == null) onConfirm(value) else error = message
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tip.isNotBlank()) {
                Text(
                    text = tip,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(start = TextFieldDefaults.InsideMargin.width),
                )
            }
            error?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(start = TextFieldDefaults.InsideMargin.width),
                )
            }
        }
    }
}

@Composable
internal fun EduSelectionDialog(
    title: String,
    items: List<String>,
    defaultIndex: Int,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    // -1 表示不预选，未选中时「确定」保持禁用
    var selected by remember(items) { mutableIntStateOf(defaultIndex) }
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        confirmEnabled = selected in items.indices,
        onDismiss = onDismiss,
        onConfirm = { if (selected in items.indices) onConfirm(selected) },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            itemsIndexed(items) { index, item ->
                SelectableEditRow(
                    title = item,
                    selected = index == selected,
                    onSelect = { selected = index },
                    radioAtEnd = true,
                )
            }
        }
    }
}
