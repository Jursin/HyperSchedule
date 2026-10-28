package com.miuix.hyperschedule.ui.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blendColors
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.effect
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 模糊半径（dp）换算成高斯 σ 的系数 */
private const val SIGMA_PER_RADIUS = 0.45f

/** 采样余量取 3σ */
private const val PADDING_SIGMA = 3f

@Composable
fun rememberAppBlurBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!enabled || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

fun Modifier.captureForBlur(backdrop: LayerBackdrop?): Modifier = if (backdrop != null) this.layerBackdrop(backdrop) else this

/** 开模糊时顶栏底色交给模糊层，关掉时退回实心 */
@Composable
fun blurAppBarColor(backdrop: LayerBackdrop?): Color =
    if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

@Composable
fun Modifier.appBarBlur(
    backdrop: LayerBackdrop?,
    blurRadius: Float = 20f,
): Modifier {
    if (backdrop == null) return this
    return backdropBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        blurRadius = blurRadius,
        blendColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f),
    )
}

@Composable
fun Modifier.navBarBlur(
    backdrop: LayerBackdrop?,
    shape: Shape,
    blurRadius: Float = 20f,
    blendColor: Color = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f),
): Modifier {
    if (backdrop == null) return this
    return backdropBlur(backdrop, shape, blurRadius, blendColor)
}

/** 全分辨率背景模糊 + 蒙层 */
private fun Modifier.backdropBlur(
    backdrop: LayerBackdrop,
    shape: Shape,
    blurRadius: Float,
    blendColor: Color,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        val sigma = blurRadius * density * SIGMA_PER_RADIUS
        // 先抬高再挂效果：记录层按这个范围扩边，模糊的 clamp 边缘落在可见区域之外
        padding = sigma * PADDING_SIGMA
        effect(BlurEffect(radiusX = sigma, radiusY = sigma, edgeTreatment = TileMode.Clamp))
        blendColors(BlurColors(blendColors = listOf(BlendColorEntry(blendColor))))
    },
)
