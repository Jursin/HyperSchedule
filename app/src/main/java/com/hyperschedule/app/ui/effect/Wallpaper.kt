package com.hyperschedule.app.ui.effect

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import java.io.File
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 解码后的壁纸长边上限 */
private const val MaxWallpaperSide = 1920

/** 壁纸上方压的主题色浓度 */
private const val WallpaperScrimAlpha = 0.35f

/** 同一张壁纸只解码一次 */
private val WallpaperCache = LruCache<String, ImageBitmap>(2)

/** 壁纸状态 */
@Stable
class WallpaperState internal constructor(
    private val layer: LayerBackdrop?,
    glassEnabled: Boolean,
) {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        internal set

    var glassEnabled by mutableStateOf(glassEnabled)
        internal set

    val glassBackdrop: LayerBackdrop?
        get() = if (glassEnabled && bitmap != null) layer else null

    val glassSample: LayerBackdrop?
        get() = glassBackdrop
}

/** 当前页面上的壁纸状态 */
val LocalWallpaper = staticCompositionLocalOf<WallpaperState?> { null }

@Composable
fun wallpaperVisible(): Boolean = LocalWallpaper.current?.bitmap != null

/** 建壁纸状态并加载图片，采样层只建一次 */
@Composable
fun rememberWallpaperState(wallpaper: String, blurEnabled: Boolean): WallpaperState {
    val supported = isRuntimeShaderSupported()
    val layer = if (supported) rememberLayerBackdrop() else null
    val state =
        remember(layer) {
            WallpaperState(layer, blurEnabled).apply {
                // 先用会话缓存
                bitmap = wallpaper.takeIf { it.isNotEmpty() }?.let { WallpaperCache.get(it) }
            }
        }
    // 只改内部状态不换实例
    SideEffect { state.glassEnabled = blurEnabled }
    LaunchedEffect(state, wallpaper) {
        if (wallpaper.isEmpty()) {
            state.bitmap = null
            return@LaunchedEffect
        }
        // 新图解码完再换上去
        state.bitmap = WallpaperCache.get(wallpaper) ?: withContext(Dispatchers.IO) { loadWallpaper(wallpaper) }
    }
    return state
}

/** 页面 Scaffold 的底色，有壁纸时必须透出去 */
@Composable
fun pageContainerColor(): Color =
    if (wallpaperVisible()) Color.Transparent else MiuixTheme.colorScheme.surface

/** 采样层在窗口里的位置 */
private val LayerOrigins = WeakHashMap<LayerBackdrop, MutableState<Offset>>()

/** 记录采样层挂载点的窗口位置 */
fun Modifier.captureForBlur(backdrop: LayerBackdrop?): Modifier =
    if (backdrop == null) {
        this
    } else {
        this.layerBackdrop(backdrop).onGloballyPositioned { coordinates ->
            val origin = LayerOrigins[backdrop] ?: return@onGloballyPositioned
            val position = coordinates.positionInWindow()
            if (origin.value != position) origin.value = position
        }
    }

/** 页面采样层 */
@Composable
fun rememberPageBackdrop(
    enabled: Boolean,
    state: WallpaperState?,
): LayerBackdrop? {
    if (!enabled || !isRuntimeShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    val origin = remember { mutableStateOf(Offset.Zero) }
    val windowSize = LocalWindowInfo.current.containerSize
    val layer =
        rememberLayerBackdrop {
            val bitmap = state?.bitmap
            if (bitmap == null) {
                drawRect(surface)
            } else {
                val offset = origin.value
                withTransform({ translate(-offset.x, -offset.y) }) {
                    drawWallpaper(bitmap, windowSize, surface.copy(alpha = WallpaperScrimAlpha))
                }
            }
            drawContent()
        }
    SideEffect { LayerOrigins[layer] = origin }
    return layer
}

/** 页面最底层的壁纸，同时作为玻璃的采样层 */
@Composable
fun WallpaperBackground(
    state: WallpaperState,
    blurRadius: Int,
    modifier: Modifier = Modifier,
) {
    val bitmap = state.bitmap ?: return
    val scrim = MiuixTheme.colorScheme.surface.copy(alpha = WallpaperScrimAlpha)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                // 挂在最外层：录进去的是屏幕上真正显示的样子（含壁纸自身那档模糊）
                .then(state.glassSample?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .then(
                    // 半径为 0 不挂效果
                    if (blurRadius > 0) {
                        Modifier.blur(blurRadius.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                    } else {
                        Modifier
                    },
                )
                .drawBehind { drawWallpaper(bitmap, size.toIntSize(), scrim) },
    )
}

/** 把壁纸按铺满裁切画进壁纸层坐标，背景与采样共用这一份几何 */
internal fun DrawScope.drawWallpaper(bitmap: ImageBitmap, size: IntSize, scrim: Color) {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    if (width <= 0f || height <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return
    val fit = max(width / bitmap.width, height / bitmap.height)
    val drawWidth = (bitmap.width * fit).roundToInt()
    val drawHeight = (bitmap.height * fit).roundToInt()
    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset =
            IntOffset(
                ((size.width - drawWidth) / 2f).roundToInt(),
                ((size.height - drawHeight) / 2f).roundToInt(),
            ),
        dstSize = IntSize(drawWidth, drawHeight),
        filterQuality = FilterQuality.Low,
    )
    if (scrim.alpha > 0f) drawRect(scrim, size = Size(width, height))
}

/** 读壁纸位图并记进会话缓存，文件不在或解不出来时为 null */
private fun loadWallpaper(path: String): ImageBitmap? {
    if (!File(path).isFile) return null
    return decodeWallpaper(path)?.also { WallpaperCache.put(path, it) }
}

/** 按长边上限做 2 的幂次降采样 */
private fun decodeWallpaper(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MaxWallpaperSide || bounds.outHeight / (sample * 2) >= MaxWallpaperSide) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}
