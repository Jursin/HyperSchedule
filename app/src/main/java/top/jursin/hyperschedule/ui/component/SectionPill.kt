package top.jursin.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.ui.effect.cardSurface
import top.jursin.hyperschedule.ui.effect.wallpaperVisible
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SectionPill(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    style: TextStyle = MiuixTheme.textStyles.body2,
    inset: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(percent = 50)
    val hasWallpaper = wallpaperVisible()
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = textAlign,
        modifier =
            modifier
                .padding(vertical = 6.dp)
                .then(
                    if (hasWallpaper) {
                        Modifier.cardSurface(CornerSize(percent = 50))
                    } else {
                        Modifier.background(color = CardDefaults.defaultColors().color, shape = shape)
                    },
                )
                .padding(horizontal = inset, vertical = 4.dp),
    )
}

/** 整行居中的胶囊提示，供空状态文案复用 */
@Composable
fun CenteredPill(
    text: String,
    modifier: Modifier = Modifier,
    pillModifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SectionPill(text = text, modifier = pillModifier, textAlign = textAlign)
    }
}
