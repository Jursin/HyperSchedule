package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.ui.effect.WindowBlurEffect
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 对话框正文的行距 */
private val DialogContentSpacing = 12.dp

/** 确认对话框：底部一排等宽按钮，[actions] 可整排替换成自定义按钮 */
@Composable
fun ConfirmDialog(
    title: String,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确定",
    confirmEnabled: Boolean = true,
    cancelText: String? = "取消",
    destructive: Boolean = false,
    message: String? = null,
    summary: String? = null,
    summaryColor: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
    show: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    WindowDialog(
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        title = title,
        summary = summary,
        summaryColor = summaryColor,
        content = {
            WindowBlurEffect(useBlur = appBlur)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DialogContentSpacing),
            ) {
                if (message != null) Text(text = message)
                content?.invoke(this)
                DialogActionRow {
                    if (actions != null) {
                        actions()
                    } else {
                        if (cancelText != null) {
                            val dismiss = LocalDismissState.current
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = { dismiss?.invoke() },
                                text = cancelText,
                            )
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = onConfirm,
                            text = confirmText,
                            enabled = confirmEnabled,
                            colors =
                                if (destructive) {
                                    ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                                } else {
                                    ButtonDefaults.textButtonColorsPrimary()
                                },
                        )
                    }
                }
            }
        },
    )
}

/** 纯选择对话框：若干等宽按钮，用于「多选一」的确认场景 */
@Composable
fun ChoiceDialog(
    title: String,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    choices: List<DialogChoice>,
    message: String? = null,
) {
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = {},
        message = message,
        actions = {
            choices.forEach { choice ->
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        choice.onClick()
                    },
                    text = choice.text,
                    enabled = choice.enabled,
                    colors =
                        if (choice.destructive) {
                            ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                )
            }
        },
    )
}

/** [ChoiceDialog] 的一个选项；[destructive] 用错误色，[enabled] 为 false 时置灰 */
data class DialogChoice(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/** 有未保存改动时返回：确认后丢弃并退出编辑 */
@Composable
fun DiscardChangesDialog(
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    ConfirmDialog(
        title = "放弃修改",
        message = "本次编辑尚未保存，返回后修改将丢失。",
        appBlur = appBlur,
        destructive = true,
        confirmText = "放弃",
        onDismiss = onDismiss,
        onConfirm = onDiscard,
    )
}

/** 拖动落点冲突：覆盖或交换（只有一门冲突课程时才能交换）；取消靠返回键，不占按钮位 */
@Composable
fun ConflictDialog(
    appBlur: Boolean,
    canSwap: Boolean,
    onDismiss: () -> Unit,
    onSwap: () -> Unit,
    onOverwrite: () -> Unit,
) {
    ChoiceDialog(
        title = "位置冲突",
        appBlur = appBlur,
        onDismiss = onDismiss,
        message = "待移动课程与目标课程冲突，请选择操作",
        choices =
            listOf(
                DialogChoice(text = "交换", onClick = onSwap, enabled = canSwap),
                DialogChoice(text = "覆盖", onClick = onOverwrite, destructive = true),
            ),
    )
}

/** 底部按钮行：等宽排布，与上方内容留出额外间距 */
@Composable
private fun DialogActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
