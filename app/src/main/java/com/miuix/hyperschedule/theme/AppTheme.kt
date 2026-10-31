package com.miuix.hyperschedule.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/** 等宽数字：MiSans 用 thum，其他字体用 tnum；不支持的字体自动忽略 */
private const val TabularFigures = "\"thum\" 1, \"tnum\" 1"

/** 全站文本样式：在 Miuix 默认字号上打开等宽数字 */
private val TabularTextStyles: TextStyles =
    defaultTextStyles().let { base ->
        fun TextStyle.tabular() = copy(fontFeatureSettings = TabularFigures)
        base.copy(
            main = base.main.tabular(),
            paragraph = base.paragraph.tabular(),
            body1 = base.body1.tabular(),
            body2 = base.body2.tabular(),
            button = base.button.tabular(),
            footnote1 = base.footnote1.tabular(),
            footnote2 = base.footnote2.tabular(),
            headline1 = base.headline1.tabular(),
            headline2 = base.headline2.tabular(),
            subtitle = base.subtitle.tabular(),
            title1 = base.title1.tabular(),
            title2 = base.title2.tabular(),
            title3 = base.title3.tabular(),
            title4 = base.title4.tabular(),
        )
    }


@Composable
fun AppTheme(
    settings: SettingsState,
    content: @Composable () -> Unit,
) {
    val isDark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val mode = if (isDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight
    val keyColor = if (settings.customColor) {
        if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            colorResource(android.R.color.system_accent1_500)
        } else {
            Color(settings.seedColor.toLong())
        }
    } else {
        null
    }
    val controller = remember(mode, keyColor, settings.paletteStyle, isDark, settings.customColor) {
        if (settings.customColor) {
            ThemeController(
                colorSchemeMode = mode,
                keyColor = keyColor,
                paletteStyle = settings.paletteStyle.toMiuixStyle(),
                colorSpec = ThemeColorSpec.Spec2025,
                isDark = isDark,
            )
        } else {
            ThemeController(if (isDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        }
    }
    // 再套一层只为覆盖遮罩强度，弹窗和抽屉都读这个值
    MiuixTheme(controller = controller, textStyles = TabularTextStyles) {
        MiuixTheme(colors = MiuixTheme.colorScheme.copy(windowDimming = WindowScrimColor)) { content() }
    }
}

enum class ThemeMode(val title: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

enum class PaletteStyle(val title: String) {
    TonalSpot("TonalSpot"),
    Neutral("Neutral"),
    Vibrant("Vibrant"),
    Expressive("Expressive"),
}

fun PaletteStyle.toMiuixStyle(): ThemePaletteStyle = when (this) {
    PaletteStyle.TonalSpot -> ThemePaletteStyle.TonalSpot
    PaletteStyle.Neutral -> ThemePaletteStyle.Neutral
    PaletteStyle.Vibrant -> ThemePaletteStyle.Vibrant
    PaletteStyle.Expressive -> ThemePaletteStyle.Expressive
}

/** 当前是否深色主题：按背景亮度判断，动态取色下同样成立 */
@Composable
fun isDarkTheme(): Boolean = MiuixTheme.colorScheme.background.luminance() < 0.5f
