package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.isDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 提示卡片的语气，只影响配色 */
enum class InfoCardType {
    /** 一般说明，灰底 */
    Info,

    /** 需要留意，主题色底 */
    Important,

    /** 可能造成损失，黄底 */
    Warning,

    /** 会改动或覆盖数据，红底 */
    Caution,
}

/** 主题色底的透明度 */
private const val IMPORTANT_TINT_ALPHA = 0.25f

/** 灰底，浅色略深于白，深色略浅于底色 */
private val InfoBackgroundLight = Color(0xFFF2F2F2)
private val InfoBackgroundDark = Color(0xFF2A2A2A)
private val InfoContentLight = Color(0xFF4D4D4D)
private val InfoContentDark = Color(0xFFCFCFCF)

/** 黄底 */
private val WarningBackgroundLight = Color(0xFFFFF3D6)
private val WarningBackgroundDark = Color(0xFF3A2F13)
private val WarningContentLight = Color(0xFF8A6100)
private val WarningContentDark = Color(0xFFFFD27A)

/** 红底 */
private val CautionBackgroundLight = Color(0xFFFFE7E5)
private val CautionBackgroundDark = Color(0xFF3D1F1D)
private val CautionContentLight = Color(0xFFB3261E)
private val CautionContentDark = Color(0xFFFFB4AB)

/** 页面里的提示卡片，语气只决定配色 */
@Composable
fun InfoCard(
    text: String,
    modifier: Modifier = Modifier,
    type: InfoCardType = InfoCardType.Info,
) {
    val dark = isDarkTheme()
    val background =
        when (type) {
            InfoCardType.Info -> if (dark) InfoBackgroundDark else InfoBackgroundLight
            // 主题色只铺一层淡底
            InfoCardType.Important -> MiuixTheme.colorScheme.primaryContainer.copy(alpha = IMPORTANT_TINT_ALPHA)
            InfoCardType.Warning -> if (dark) WarningBackgroundDark else WarningBackgroundLight
            InfoCardType.Caution -> if (dark) CautionBackgroundDark else CautionBackgroundLight
        }
    val contentColor =
        when (type) {
            InfoCardType.Info -> if (dark) InfoContentDark else InfoContentLight
            InfoCardType.Important -> MiuixTheme.colorScheme.primary
            InfoCardType.Warning -> if (dark) WarningContentDark else WarningContentLight
            InfoCardType.Caution -> if (dark) CautionContentDark else CautionContentLight
        }
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = CardDefaults.defaultColors(color = background, contentColor = MiuixTheme.colorScheme.onSurface),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MiuixTheme.textStyles.body2,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
