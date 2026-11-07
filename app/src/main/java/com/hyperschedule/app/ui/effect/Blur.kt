package com.hyperschedule.app.ui.effect

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 玻璃面的模糊半径（dp） */
internal const val GlassBlurRadius = 24f

/** 玻璃面叠在壁纸上的主题色浓度 */
private const val GlassBlendAlpha = 0.5f

/** 栏类玻璃的蒙层浓度 */
private const val BarBlendAlpha = 0.68f

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

/** 壁纸采样源，没壁纸或没开模糊时为 null */
@Composable
private fun wallpaperGlass(): Backdrop? {
    val state = LocalWallpaper.current ?: return null
    if (state.bitmap == null || !state.glassEnabled) return null
    return state.backdrop
}

/** 顶栏底色，有壁纸或玻璃层时透明，否则退回实心 */
@Composable
fun blurAppBarColor(backdrop: Backdrop?): Color =
    if (backdrop != null || wallpaperGlass() != null) Color.Transparent else MiuixTheme.colorScheme.surface

@Composable
fun Modifier.appBarBlur(backdrop: Backdrop?): Modifier {
    // 有壁纸时顶栏完全透明，壁纸从顶到脚连成一片
    if (backdrop == null || wallpaperGlass() != null) return this
    return glass(backdrop, RectangleShape, MiuixTheme.colorScheme.surface.copy(alpha = BarBlendAlpha))
}

@Composable
fun Modifier.navBarBlur(
    backdrop: Backdrop?,
    shape: Shape,
    blendColor: Color = MiuixTheme.colorScheme.surface.copy(alpha = BarBlendAlpha),
): Modifier = glass(backdrop, shape, blendColor)

/** 卡片在壁纸上的底，没壁纸时不加 */
@Composable
fun Modifier.cardSurface(shape: Shape): Modifier {
    val backdrop = wallpaperGlass() ?: return this
    return glass(backdrop, shape, MiuixTheme.colorScheme.surface.copy(alpha = GlassBlendAlpha))
}

@Composable
private fun Modifier.glass(backdrop: Backdrop?, shape: Shape, blendColor: Color): Modifier {
    if (backdrop == null) return background(blendColor, shape)
    val wallpaper = backdrop as? WallpaperBackdrop
    val shapeBlock = remember(shape) { { shape } }
    val effects: BackdropEffectScope.() -> Unit =
        remember(wallpaper) { { if (wallpaper == null) blur(GlassBlurRadius * density) } }
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
    override fun create(): BakeSignalNode = BakeSignalNode(baked)

    override fun update(node: BakeSignalNode) {
        node.token = baked
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bakeSignal"
    }

    override fun equals(other: Any?): Boolean = other is BakeSignalElement && other.baked === baked

    override fun hashCode(): Int = System.identityHashCode(baked)
}

private class BakeSignalNode(
    var token: Any?,
) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() = drawContent()
}
