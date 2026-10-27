package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 分享码 / 口令输入：取回期间禁用确定，失败原因就地显示 */
@Composable
fun ShareCodeDialog(
    title: String,
    hint: String,
    appBlur: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    IconHeaderDialog(
        title = title,
        appBlur = appBlur,
        confirmEnabled = input.isNotBlank() && !busy,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(input.trim()) },
    ) {
        TextField(
            value = input,
            onValueChange = { input = it.take(2000) },
            label = hint,
            useLabelAsPlaceholder = true,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        if (busy) {
            Text(text = "正在获取课表…", style = MiuixTheme.textStyles.body2)
        }
        error?.let {
            Text(
                text = it,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}
