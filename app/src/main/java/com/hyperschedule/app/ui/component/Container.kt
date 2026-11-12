package com.hyperschedule.app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.theme.CardCornerRadius
import com.hyperschedule.app.theme.ContainerTintAlpha
import com.hyperschedule.app.theme.accentBlueColor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun Container(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = accentBlueColor(),
    onClick: (() -> Unit)? = null,
) {
    val colors =
        CardDefaults.defaultColors(
            color = textColor.copy(alpha = ContainerTintAlpha),
            contentColor = textColor,
        )
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = colors,
        pressFeedbackType = if (onClick != null) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = onClick,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MiuixTheme.textStyles.body2,
            color = colors.contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
