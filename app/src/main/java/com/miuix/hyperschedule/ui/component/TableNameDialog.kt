package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.TextField

/** 添加 / 重命名课表：名称为空时确定按钮不可点，最多 20 字 */
@Composable
fun TableNameDialog(
    title: String,
    initialName: String = "",
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    IconHeaderDialog(
        title = title,
        appBlur = appBlur,
        confirmEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(name.trim()) },
    ) {
        TextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = "课表名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
