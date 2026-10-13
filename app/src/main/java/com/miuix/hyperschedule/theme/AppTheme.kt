package com.miuix.hyperschedule.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

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
    MiuixTheme(controller = controller, content = content)
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
