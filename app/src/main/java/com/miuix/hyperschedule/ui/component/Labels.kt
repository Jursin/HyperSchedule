package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 复选框 + 文字：对话框里的「自定义时间」「全部 / 单周 / 双周」等都用它 */
@Composable
fun CheckLabel(
    state: ToggleableState,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(state = state, onClick = onClick, modifier = Modifier.scale(0.72f))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** 设置项右侧的值文本；placeholder 用弱化色表示尚未设置 */
@Composable
fun ValueLabel(
    text: String,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color =
            if (placeholder) {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantActions
            },
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = modifier,
    )
}
