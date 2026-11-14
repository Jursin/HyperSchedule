package com.hyperschedule.app.ui.effect

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlurEffect
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 玻璃面的模糊半径（dp） */
internal const val GlassBlurRadius = 24f

/** 玻璃面叠在壁纸上的主题色浓度 */
private const val GlassBlendAlpha = 0.5f

/** 栏类玻璃的蒙层浓度 */
private const val BarBlendAlpha = 0.68f

/** 糊带顶边的模糊半径（dp） */
private const val AppBarBlurRadius = 12f

/** 页面内容的采样层，没开模糊或没有运行着色器能力时为 null */
@Composable
fun rememberAppBlurBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!enabled || !isRuntimeShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    // 有壁纸时页面本身透明，采样层再铺底色糊带里就只剩一层纯色
    val opaque = !wallpaperVisible()
    return rememberLayerBackdrop {
        if (opaque) drawRect(surface)
        drawContent()
    }
}

fun Modifier.captureForBlur(backdrop: LayerBackdrop?): Modifier = if (backdrop != null) this.layerBackdrop(backdrop) else this

/** 玻璃面的壁纸采样源（预糊纹理） */
@Composable
private fun wallpaperGlass(): Backdrop? = LocalWallpaper.current?.glassBackdrop

/** 顶栏糊带采样源：有壁纸时把壁纸层与内容层合成，栏下没内容就糊出壁纸本身 */
@Composable
fun appBarBandBackdrop(content: Backdrop?): Backdrop? {
    val wallpaper = LocalWallpaper.current?.glassSample
    return when {
        wallpaper == null -> content
        content == null -> wallpaper
        else -> remember(wallpaper, content) { CombinedBackdrop(wallpaper, content) }
    }
}

/** 多层采样依次叠加，先给的画在下面 */
private class CombinedBackdrop(
    private vararg val layers: Backdrop,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        for (layer in layers) {
            with(layer) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        }
    }
}

/** 糊带整条铺满、模糊强度沿高度递减，形状与效果各一份，不随重组重建 */
private val BandShape: () -> Shape = { RectangleShape }

private val BandEffects: BackdropEffectScope.() -> Unit = {
    progressiveTextureBlurEffect(blurRadiusX = AppBarBlurRadius, gradient = ProgressiveBlur.Top)
}

/** 顶栏渐进糊带：模糊从顶边连续收到 0，不叠表面色，有壁纸时栏下没内容就与壁纸无异 */
fun Modifier.appBarBand(backdrop: Backdrop?): Modifier =
    if (backdrop == null) {
        this
    } else {
        drawBackdrop(
            backdrop = backdrop,
            shape = BandShape,
            effects = BandEffects,
            progressiveGradient = ProgressiveBlur.Top,
        )
    }

@Composable
fun Modifier.navBarBlur(
    backdrop: Backdrop?,
    shape: Shape,
    blendColor: Color = MiuixTheme.colorScheme.surface.copy(alpha = BarBlendAlpha),
    blurRadius: Float = GlassBlurRadius,
): Modifier = glass(backdrop, shape, blendColor, blurRadius)

/** 卡片在壁纸上的底，没壁纸时不加 */
@Composable
fun Modifier.cardSurface(shape: Shape): Modifier {
    val backdrop = wallpaperGlass() ?: return this
    return glass(backdrop, shape, MiuixTheme.colorScheme.surface.copy(alpha = GlassBlendAlpha))
}

@Composable
private fun Modifier.glass(
    backdrop: Backdrop?,
    shape: Shape,
    blendColor: Color,
    blurRadius: Float = GlassBlurRadius,
): Modifier {
    if (backdrop == null) return background(blendColor, shape)
    val wallpaper = backdrop as? WallpaperBackdrop
    val shapeBlock = remember(shape) { { shape } }
    val effects: BackdropEffectScope.() -> Unit =
        remember(wallpaper, blurRadius) { { if (wallpaper == null) blur(blurRadius * density) } }
    val surface: DrawScope.() -> Unit = remember(blendColor) { { drawRect(blendColor) } }
    val glass =
        drawBackdrop(
            backdrop = backdrop,
            shape = shapeBlock,
            effects = effects,
            onDrawSurface = surface,
        )
    return if (wallpaper == null) glass else glass.bakeSignal(wallpaper.baked)
}

private fun Modifier.bakeSignal(baked: Any?): Modifier = this then BakeSignalElement(baked)

private class BakeSignalElement(
    private val baked: Any?,
) : ModifierNodeElement<BakeSignalNode>() {
    override fun create(): BakeSignalNode = BakeSignalNode()

    override fun update(node: BakeSignalNode) {
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bakeSignal"
    }

    override fun equals(other: Any?): Boolean = other is BakeSignalElement && other.baked === baked

    override fun hashCode(): Int = System.identityHashCode(baked)
}

private class BakeSignalNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() = drawContent()
}
