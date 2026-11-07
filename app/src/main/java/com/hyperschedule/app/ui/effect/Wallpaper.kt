package com.hyperschedule.app.ui.effect

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
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
    /** 壁纸采样源，运行时不支持模糊时为 null */
    val backdrop: WallpaperBackdrop?,
    glassEnabled: Boolean,
) {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        internal set

    var glassEnabled by mutableStateOf(glassEnabled)
        internal set
}

/** 当前页面上的壁纸状态 */
val LocalWallpaper = staticCompositionLocalOf<WallpaperState?> { null }

@Composable
fun wallpaperVisible(): Boolean = LocalWallpaper.current?.bitmap != null

/** 建壁纸状态并加载图片，采样源只建一次 */
@Composable
fun rememberWallpaperState(wallpaper: String, blurEnabled: Boolean): WallpaperState {
    val backdrop = if (isRuntimeShaderSupported()) remember { WallpaperBackdrop() } else null
    val state =
        remember(backdrop) {
            WallpaperState(backdrop, blurEnabled).apply {
                // 先用会话缓存
                bitmap = wallpaper.takeIf { it.isNotEmpty() }?.let { WallpaperCache.get(it) }
            }
        }
    // 只改内部状态不换实例
    SideEffect { state.glassEnabled = blurEnabled }
    LaunchedEffect(state, wallpaper) {
        if (wallpaper.isEmpty()) {
            state.bitmap = null
            state.backdrop?.bitmap = null
            return@LaunchedEffect
        }
        // 新图解码完再换上去
        val cached = WallpaperCache.get(wallpaper)
        state.bitmap = cached ?: withContext(Dispatchers.IO) { loadWallpaper(wallpaper) }
    }
    return state
}

/** 页面 Scaffold 的底色，有壁纸时必须透出去 */
@Composable
fun pageContainerColor(): Color =
    if (wallpaperVisible()) Color.Transparent else MiuixTheme.colorScheme.surface

/** 页面最底层的壁纸，同时驱动一次预烘 */
@Composable
fun WallpaperBackground(
    state: WallpaperState,
    blurRadius: Int,
    modifier: Modifier = Modifier,
) {
    // 壁纸被撤掉时采样源也得跟着空
    val backdrop = state.backdrop
    val bitmap = state.bitmap
    backdrop?.bitmap = bitmap
    backdrop?.wallpaperBlur = blurRadius
    if (backdrop == null || bitmap == null) {
        backdrop?.clear()
        return
    }
    val scrim = MiuixTheme.colorScheme.surface.copy(alpha = WallpaperScrimAlpha)
    backdrop.scrim = scrim
    val density = LocalDensity.current.density
    // 壁纸、尺寸、蒙层、模糊档位任一变化就重烘一次
    LaunchedEffect(backdrop, bitmap, blurRadius, scrim, backdrop.canvasSize, density) {
        if (!backdrop.needBake(density)) return@LaunchedEffect
        withContext(Dispatchers.Default) { backdrop.bake(density) { ensureActive() } }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    backdrop.canvasSize = coordinates.size
                    backdrop.anchor = coordinates
                }
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
