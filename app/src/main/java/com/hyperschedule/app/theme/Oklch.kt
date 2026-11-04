package com.hyperschedule.app.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** 彩度二分起点，sRGB 内任何色相的最大彩度都远小于它 */
private const val ChromaProbe = 0.5f

/** 该明度、色相下 sRGB 能表示的最大彩度，用于按色相做归一化 */
internal fun maxChroma(
    lightness: Float,
    hue: Float,
): Float {
    if (inSrgbGamut(linearRgb(lightness, ChromaProbe, hue))) return ChromaProbe
    var inGamut = 0f
    var outOfGamut = ChromaProbe
    repeat(24) {
        val mid = (inGamut + outOfGamut) / 2f
        if (inSrgbGamut(linearRgb(lightness, mid, hue))) inGamut = mid else outOfGamut = mid
    }
    return inGamut
}

/** sRGB → OKLCH 的彩度与色相（0–360），用于把既有颜色按统一规则换到另一明度 */
internal fun chromaHueOf(color: Color): Pair<Float, Float> {
    val r = decodeSrgb(color.red)
    val g = decodeSrgb(color.green)
    val b = decodeSrgb(color.blue)
    val l = (0.41222146f * r + 0.53633255f * g + 0.051445995f * b).cubeRoot()
    val m = (0.2119035f * r + 0.6806995f * g + 0.10739696f * b).cubeRoot()
    val s = (0.08830246f * r + 0.28171885f * g + 0.6299787f * b).cubeRoot()
    val a = 1.9779985f * l - 2.4285922f * m + 0.4505937f * s
    val bb = 0.025904037f * l + 0.78277177f * m - 0.80867577f * s
    val hue = atan2(bb, a) * 180f / PI.toFloat()
    return hypot(a, bb) to (hue + 360f) % 360f
}

/** OKLCH → sRGB，越界时保住明度与色相、二分降彩度 */
internal fun oklchColor(
    lightness: Float,
    chroma: Float,
    hue: Float,
): Color {
    val c = if (inSrgbGamut(linearRgb(lightness, chroma, hue))) chroma else maxChroma(lightness, hue)
    val rgb = linearRgb(lightness, c, hue)
    return Color(encodeSrgb(rgb[0]), encodeSrgb(rgb[1]), encodeSrgb(rgb[2]))
}

/** OKLCH → 线性 sRGB，可能越界 */
private fun linearRgb(
    lightness: Float,
    chroma: Float,
    hue: Float,
): FloatArray {
    val rad = hue * PI.toFloat() / 180f
    val a = chroma * cos(rad)
    val b = chroma * sin(rad)
    val l = cube(lightness + 0.39633778f * a + 0.21580376f * b)
    val m = cube(lightness - 0.105561346f * a - 0.06385417f * b)
    val s = cube(lightness - 0.08948418f * a - 1.2914855f * b)
    return floatArrayOf(
        4.0767417f * l - 3.3077116f * m + 0.23096994f * s,
        -1.268438f * l + 2.6097574f * m - 0.34131938f * s,
        -0.0041960864f * l - 0.7034186f * m + 1.7076147f * s,
    )
}

private fun cube(x: Float): Float = x * x * x

/** 立方根的 Float 版本，LMS 分量非负，不用担心负数开三方的 NaN */
private fun Float.cubeRoot(): Float = pow(1f / 3f)

/** 线性值反做 sRGB 伽马解码 */
private fun decodeSrgb(channel: Float): Float =
    if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

private fun inSrgbGamut(rgb: FloatArray): Boolean = rgb.all { it >= -1e-4f && it <= 1.0001f }

/** 线性值做 sRGB 伽马编码 */
private fun encodeSrgb(linear: Float): Float {
    val c = linear.coerceIn(0f, 1f)
    return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
}
