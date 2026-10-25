package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 整宽 50dp 的动作按钮（列表末尾的删除、添加等）：左侧图标 + 文案 */
@Composable
fun WideActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MiuixTheme.colorScheme.onBackground,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        colors =
            ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.surfaceContainerHighest,
                contentColor = contentColor,
            ),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = contentColor)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium, color = contentColor)
    }
}
