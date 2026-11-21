package top.jursin.hyperschedule.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import top.jursin.hyperschedule.data.local.AccentColor
import top.jursin.hyperschedule.data.local.PaletteStyle
import top.jursin.hyperschedule.data.local.SettingsState
import top.jursin.hyperschedule.data.local.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/** 等宽数字，MiSans 用 thum，其他字体用 tnum，不支持的字体自动忽略 */
private const val TabularFigures = "\"thum\" 1, \"tnum\" 1"

/** 全站文本样式，在默认字号上打开等宽数字 */
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
    val monet = settings.useMonet
    val mode =
        when {
            monet && isDark -> ColorSchemeMode.MonetDark
            monet -> ColorSchemeMode.MonetLight
            isDark -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.Light
        }
    val keyColor =
        when {
            !monet -> null
            settings.accentColor != AccentColor.Default -> Color(settings.accentColor.seedColor)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> colorResource(android.R.color.system_accent1_500)
            else -> Color(AccentColor.Default.seedColor)
        }
    val controller = remember(mode, keyColor, settings.paletteStyle) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = settings.paletteStyle.toMiuixStyle(),
        )
    }
    val colors = controller.currentColors()
    val themedColors = remember(colors, isDark, settings.pureBlack) {
        val usePureBlack = settings.useMonet && isDark && settings.pureBlack
        val base = if (usePureBlack) colors.copy(background = Color.Black, surface = Color.Black) else colors
        base.copy(windowDimming = WindowScrimColor)
    }
    MiuixTheme(controller = controller, textStyles = TabularTextStyles) {
        MiuixTheme(colors = themedColors) {
            CompositionLocalProvider(LocalMonetCourseColors provides settings.useMonet) {
                content()
            }
        }
    }
}

private fun PaletteStyle.toMiuixStyle(): ThemePaletteStyle = when (this) {
    PaletteStyle.TonalSpot -> ThemePaletteStyle.TonalSpot
    PaletteStyle.Neutral -> ThemePaletteStyle.Neutral
    PaletteStyle.Vibrant -> ThemePaletteStyle.Vibrant
    PaletteStyle.Expressive -> ThemePaletteStyle.Expressive
    PaletteStyle.Rainbow -> ThemePaletteStyle.Rainbow
    PaletteStyle.FruitSalad -> ThemePaletteStyle.FruitSalad
    PaletteStyle.Monochrome -> ThemePaletteStyle.Monochrome
    PaletteStyle.Fidelity -> ThemePaletteStyle.Fidelity
    PaletteStyle.Content -> ThemePaletteStyle.Content
}

/** 当前是否深色主题，按背景亮度判断，Monet 取色下同样成立 */
@Composable
fun isDarkTheme(): Boolean = MiuixTheme.colorScheme.background.luminance() < 0.5f
