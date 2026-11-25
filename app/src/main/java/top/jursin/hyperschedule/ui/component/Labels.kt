package top.jursin.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.theme.BadgeCorner
import top.jursin.hyperschedule.theme.BadgeHorizontalPadding
import top.jursin.hyperschedule.theme.BadgeVerticalPadding
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

/** 设置项右侧的值文本，placeholder 用弱化色表示尚未设置 */
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

/** 弱化提示文字 */
@Composable
fun HintText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MiuixTheme.textStyles.body1,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** 小角标胶囊 */
@Composable
fun SmallBadge(
    text: String,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        modifier =
            modifier
                .background(color = containerColor, shape = RoundedCornerShape(BadgeCorner))
                .padding(horizontal = BadgeHorizontalPadding, vertical = BadgeVerticalPadding),
    )
}
