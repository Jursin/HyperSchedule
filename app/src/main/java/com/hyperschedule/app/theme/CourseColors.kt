package com.hyperschedule.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.hyperschedule.app.data.db.entity.Course
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 默认色板，即 Material 500 原色 */
private val MaterialLightPalette: List<Color> =
    listOf(
        0xFF4CAF50,
        0xFF2196F3,
        0xFFFF9800,
        0xFFF44336,
        0xFFFFC107,
        0xFFE91E63,
        0xFF00BCD4,
        0xFF3F51B5,
        0xFFAB47BC,
        0xFF009688,
        0xFF673AB7,
        0xFF795548,
        0xFF607D8B,
    ).also { check(it.size == Course.COLOR_COUNT) { "色板数量要与 Course.COLOR_COUNT 一致" } }
        .map { Color(it) }

/** 默认色板的深色档 */
private val MaterialDarkPalette: List<Color> by lazy {
    MaterialLightPalette.map { color ->
        val (chroma, hue) = chromaHueOf(color)
        // oklchColor 越界时会自己按色域收彩度，这里不用再夹一次
        oklchColor(0.78f, chroma * 0.68f, hue)
    }
}

/** Monet 取色用的 13 个色相，均分保证彼此好区分 */
private val CourseHues =
    listOf(25, 164, 302, 80, 219, 358, 136, 274, 53, 192, 330, 108, 247)
        .also { check(it.size == Course.COLOR_COUNT) { "课程色相数量要与 Course.COLOR_COUNT 一致" } }

/** 色相均分，彩度取主题主色那一档并夹在柔和区间 */
private fun monetPalette(seed: Color, isDark: Boolean): List<Color> {
    val lightness = if (isDark) 0.80f else 0.56f
    val chroma = chromaHueOf(seed).first.coerceIn(0.055f, 0.1f)
    return CourseHues.map { hue ->
        val h = hue.toFloat()
        oklchColor(lightness, minOf(chroma, maxChroma(lightness, h) * 0.7f), h)
    }
}

/** 主题是否开启了「Monet 取色」 */
internal val LocalMonetCourseColors = compositionLocalOf { false }

/** 按主题模式取当前生效的色板 */
@Composable
internal fun rememberCoursePalette(isDark: Boolean): List<Color> {
    val monet = LocalMonetCourseColors.current
    // Monet 下课程色跟着主题主色的彩度走，换强调色整块色板一起变
    val themePrimary = MiuixTheme.colorScheme.primary
    return remember(isDark, monet, themePrimary) {
        when {
            monet -> monetPalette(themePrimary, isDark)
            isDark -> MaterialDarkPalette
            else -> MaterialLightPalette
        }
    }
}

/** 取色板第 [index] 个色，越界取模 */
internal fun courseColorAt(
    index: Int,
    palette: List<Color>,
): Color = palette[index.mod(palette.size)]

/** 课程显色，自定义色优先，否则按 [Course.colorInt] 取当前色板 */
@Composable
internal fun rememberCourseColorOf(
    colorInt: Int,
    colorArgb: Int?,
    isDark: Boolean,
): Color {
    val palette = rememberCoursePalette(isDark)
    return remember(colorInt, colorArgb, palette) {
        colorArgb?.let { Color(it) } ?: courseColorAt(colorInt, palette)
    }
}

/** 课程显色，跟随主题深浅取对应一档 */
@Composable
internal fun rememberCourseColor(course: Course): Color {
    val isDark = isDarkTheme()
    return rememberCourseColorOf(course.colorInt, course.colorArgb, isDark)
}
