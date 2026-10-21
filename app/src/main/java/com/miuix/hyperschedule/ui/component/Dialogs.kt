package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.ui.theme.WindowBlurEffect
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 拖动落点冲突：覆盖或交换（只有一门冲突课程时才能交换）；取消靠返回键，不占按钮位 */
@Composable
fun ConflictDialog(
    appBlur: Boolean,
    canSwap: Boolean,
    onDismiss: () -> Unit,
    onSwap: () -> Unit,
    onOverwrite: () -> Unit,
) {
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "位置冲突",
        content = {
            WindowBlurEffect(useBlur = appBlur)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "待移动课程与目标课程冲突，请选择操作")
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onSwap,
                        text = "交换",
                        enabled = canSwap,
                    )
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onOverwrite,
                        text = "覆盖",
                        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    )
                }
            }
        },
    )
}

/** 通用确认对话框；[cancelText] 为 null 时只显示确认按钮，[content] 用于进度/输入框等自定义正文 */
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
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = title,
        content = {
            WindowBlurEffect(useBlur = appBlur)
            val dismiss = LocalDismissState.current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (message != null) Text(text = message)
                content?.invoke(this)
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (cancelText != null) {
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
        },
    )
}
