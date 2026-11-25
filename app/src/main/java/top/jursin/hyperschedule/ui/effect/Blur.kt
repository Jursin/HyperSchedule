package top.jursin.hyperschedule.ui.effect

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.theme.isDarkTheme
import top.jursin.hyperschedule.ui.component.LocalInsideFloatingWindow
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.glass.glass as miuixGlass
import top.yukonga.miuix.kmp.glass.glassShadow
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 页面级玻璃采样层 */
val LocalPageGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val LocalFabBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 玻璃面的模糊半径（dp） */
internal const val GlassBlurRadius = 24f

/** 全项目玻璃材质统一的明暗样式 */
@Composable
fun glassStyle(): GlassStyle = GlassDefaults.style(isDarkTheme())

@Composable
fun rememberAppBlurBackdrop(enabled: Boolean): LayerBackdrop? {
    val wallpaper = LocalWallpaper.current?.takeIf { !LocalInsideFloatingWindow.current }
    return rememberPageBackdrop(enabled, wallpaper)
}


/** 玻璃面的壁纸采样源 */
@Composable
private fun wallpaperGlass(): Backdrop? = LocalWallpaper.current?.glassBackdrop

/** 顶栏糊带的模糊半径（dp） */
internal const val AppBarBandBlurRadius = 20f

/** 顶栏糊带 */
@Composable
fun Modifier.appBarBand(
    backdrop: Backdrop?,
    enabled: Boolean = true,
): Modifier {
    if (backdrop == null) return this
    val wallpaper = LocalWallpaper.current
    return progressiveTextureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        gradient = ProgressiveBlur.Top.copy(curve = 0.25f),
        blurRadius = AppBarBandBlurRadius,
        enabled = enabled,
    ).then(RepaintElement(wallpaper?.bitmap, wallpaper?.glassEnabled == true))
}

/** 底栏、浮起面板等栏式玻璃面 */
@Composable
fun Modifier.navBarBlur(
    backdrop: Backdrop?,
    corner: CornerSize,
    blendColor: Color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.68f),
    shadow: GlassShadow? = null,
): Modifier = glass(backdrop, corner, blendColor, blurRadius = 6f, shadow = shadow, shading = false, noise = 0f)

/** 卡片在壁纸上的底，没壁纸时不加 */
@Composable
fun Modifier.cardSurface(corner: CornerSize): Modifier {
    val backdrop = wallpaperGlass() ?: return this
    return glass(backdrop, corner, MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
}

@Composable
private fun Modifier.glass(
    backdrop: Backdrop?,
    corner: CornerSize,
    blendColor: Color,
    blurRadius: Float = GlassBlurRadius,
    shadow: GlassShadow? = null,
    shading: Boolean = true,
    noise: Float = GlassDefaults.NoiseCoefficient,
): Modifier {
    val rounded = remember(corner) { RoundedCornerShape(corner) }
    val shape = remember(corner) { GlassShape(corner, corner, corner, corner, smoothing = 0f) }
    val wallpaper = LocalWallpaper.current
    val repaint = RepaintElement(wallpaper?.bitmap, wallpaper?.glassEnabled == true)
    if (backdrop == null) {
        return glassShadow(shape, shadow).background(blendColor, rounded).then(repaint)
    }
    if (!isRuntimeShaderSupported()) {
        val shapeBlock = remember(rounded) { { rounded } }
        val effects: BackdropEffectScope.() -> Unit = remember(blurRadius) { { blur(blurRadius * density) } }
        val surface: DrawScope.() -> Unit = remember(blendColor) { { drawRect(blendColor) } }
        return glassShadow(shape, shadow)
            .drawBackdrop(
                backdrop = backdrop,
                shape = shapeBlock,
                effects = effects,
                onDrawSurface = surface,
            ).then(repaint)
    }
    val material = glassTint(blendColor, blurRadius)
    return glassShadow(shape, shadow)
        .miuixGlass(
            backdrop = backdrop,
            shape = shape,
            style = glassStyle(),
            material = material,
            noiseCoefficient = noise,
            shading = shading,
        ).then(repaint)
}

@Composable
internal fun glassTint(blendColor: Color, blurRadius: Float): GlassMaterial {
    val base = GlassMaterials.puredThinGlass(isDarkTheme())
    return remember(base, blendColor, blurRadius) {
        base.copy(
            blurRadius = blurRadius.dp,
            first = GlassColorLayer(blendColor, GlassColorBlendMode.SrcOver),
            second = null,
            third = null,
        )
    }
}

/** 壁纸换新后让玻璃面重画一次的空壳节点 */
@SuppressLint("ModifierNodeInspectableProperties")
private class RepaintElement(
    private val bitmap: ImageBitmap?,
    private val glassEnabled: Boolean,
) : ModifierNodeElement<RepaintNode>() {
    override fun create(): RepaintNode = RepaintNode()

    override fun update(node: RepaintNode) = node.invalidateDraw()

    override fun equals(other: Any?): Boolean =
        other is RepaintElement && other.bitmap === bitmap && other.glassEnabled == glassEnabled

    override fun hashCode(): Int = 31 * System.identityHashCode(bitmap) + glassEnabled.hashCode()
}

private class RepaintNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() = drawContent()
}
