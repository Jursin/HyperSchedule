package com.hyperschedule.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.hyperschedule.app.data.model.Course

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

/** 深色档明度 */
private const val DarkLightness = 0.78f

/** 提亮时彩度按比例收敛 */
private const val DarkChromaScale = 0.68f

/** 默认色板的深色档 */
private val MaterialDarkPalette: List<Color> by lazy {
    MaterialLightPalette.map { color ->
        val (chroma, hue) = chromaHueOf(color)
        // oklchColor 越界时会自己按色域收彩度，这里不用再夹一次
        oklchColor(DarkLightness, chroma * DarkChromaScale, hue)
    }
}

/** 开启「自定义颜色」后用的色板 */
private val CourseHues =
    listOf(25, 164, 302, 80, 219, 358, 136, 274, 53, 192, 330, 108, 247)
        .also { check(it.size == Course.COLOR_COUNT) { "课程色相数量要与 Course.COLOR_COUNT 一致" } }

/** 浅色档明度，对应 Material tone 40 */
private const val TonalLightLightness = 0.48f

/** 彩度取该色相在当前明度下能到的最大彩度的 75% */
private const val ChromaRatio = 0.75f

private fun tonalPalette(lightness: Float) =
    CourseHues.map { hue ->
        val h = hue.toFloat()
        oklchColor(lightness, maxChroma(lightness, h) * ChromaRatio, h)
    }

private val TonalLightPalette by lazy { tonalPalette(TonalLightLightness) }

private val TonalDarkPalette by lazy { tonalPalette(DarkLightness) }

/** 主题是否开启了「自定义颜色」 */
internal val LocalCustomCourseColors = compositionLocalOf { false }

/** 按主题模式取当前生效的色板 */
@Composable
internal fun rememberCoursePalette(isDark: Boolean): List<Color> {
    val custom = LocalCustomCourseColors.current
    return remember(isDark, custom) {
        when {
            custom -> if (isDark) TonalDarkPalette else TonalLightPalette
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
