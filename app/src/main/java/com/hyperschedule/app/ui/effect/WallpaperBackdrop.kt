package com.hyperschedule.app.ui.effect

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.blur.Backdrop

/** 预模糊纹理相对全屏像素的比例 */
private const val BakeScale = 0.4f

/** 模糊半径（dp）换算成高斯 σ 的系数 */
private const val SigmaPerRadius = 0.45f

/** 一次烘焙结果，换新实例即通知采样方重画 */
internal class BakedWallpaper(
    val image: ImageBitmap,
    /** 纹理相对全屏像素的比例 */
    val scale: Float,
)

@Stable
class WallpaperBackdrop : Backdrop {
    /** 当前壁纸，组合期写入 */
    var bitmap: ImageBitmap? by mutableStateOf(null)
        internal set

    /** 壁纸上的主题色蒙层，与背景那层同值 */
    var scrim: Color by mutableStateOf(Color.Transparent)
        internal set

    /** 背景壁纸自身的模糊强度（dp），玻璃在这份模糊之上再叠一档 */
    var wallpaperBlur: Int by mutableIntStateOf(0)
        internal set

    /** 壁纸层的尺寸，采样时用来算偏移 */
    var canvasSize: IntSize by mutableStateOf(IntSize.Zero)
        internal set

    /** 壁纸层在窗口里的坐标 */
    var anchor: LayoutCoordinates? = null

    override val isCoordinatesDependent: Boolean get() = true

    /** 烘焙好的模糊壁纸，所有玻璃面共用 */
    internal var baked: BakedWallpaper? by mutableStateOf(null)
        private set

    private var bakedKey: List<Any?>? = null

    /** 壁纸、尺寸、蒙层、模糊量任一变化才重烘 */
    internal fun needBake(density: Float): Boolean {
        val source = bitmap ?: return false
        val area = canvasSize
        return !(area.width <= 0 || area.height <= 0) && keyOf(source, area, density) != bakedKey
    }

    private fun keyOf(source: ImageBitmap, area: IntSize, density: Float): List<Any?> {
        // 壁纸与玻璃两档高斯合成一档
        val glassSigmaDp = GlassBlurRadius * SigmaPerRadius
        val blurDp = wallpaperBlur.toFloat()
        val sigmaDp = sqrt(blurDp * blurDp + glassSigmaDp * glassSigmaDp)
        return listOf(source, area, scrim, density * sigmaDp)
    }

    /** 合成预模糊纹理，checkActive 在每趟模糊之间调用 */
    internal fun bake(density: Float, checkActive: () -> Unit) {
        val source = bitmap ?: return
        val area = canvasSize
        if (area.width <= 0 || area.height <= 0) return
        val key = keyOf(source, area, density)
        if (key == bakedKey) return
        val texture = blurWallpaper(source, area, scrim, key.last() as Float, checkActive) ?: return
        bakedKey = key
        baked = texture
    }

    internal fun clear() {
        baked = null
        bakedKey = null
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) = drawTexture(baked, anchor, coordinates, downscaleFactor)
}

/** 纹理按壁纸层坐标贴回去，downscaleFactor > 1 时跟着缩放（渐进模糊的分级层会用到） */
private fun DrawScope.drawTexture(
    texture: BakedWallpaper?,
    anchor: LayoutCoordinates?,
    coordinates: LayoutCoordinates?,
    downscaleFactor: Int,
) {
    // 状态必须最早读，纹理换新靠它通知每个玻璃面重画
    if (texture == null) return
    val surface = coordinates ?: return
    val wallpaper = anchor ?: return
    val offset = runCatching { wallpaper.localPositionOf(surface, Offset.Zero) }.getOrNull() ?: return
    val scale = texture.scale
    if (scale <= 0f) return
    val image = texture.image
    val inv = if (downscaleFactor > 1) 1f / downscaleFactor else 1f
    withTransform({
        // 先平移到壁纸层原点，再按 1/scale 放大回窗口坐标
        translate(-offset.x * inv, -offset.y * inv)
        scale(inv / scale, inv / scale, Offset.Zero)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(image.width, image.height),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** 把壁纸按铺满裁切画进降采样缓冲，压上蒙层后模糊一次 */
private fun blurWallpaper(
    source: ImageBitmap,
    area: IntSize,
    scrim: Color,
    sigma: Float,
    checkActive: () -> Unit,
): BakedWallpaper? {
    val width = (area.width * BakeScale).roundToInt().coerceAtLeast(1)
    val height = (area.height * BakeScale).roundToInt().coerceAtLeast(1)
    val bitmap = source.asAndroidBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    val buffer = createBitmap(width, height)
    val canvas = Canvas(buffer)
    // 与背景那层同一套几何：等比铺满居中裁切
    val fit = max(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
    val drawWidth = bitmap.width * fit
    val drawHeight = bitmap.height * fit
    val dst =
        RectF(
            (width - drawWidth) / 2f,
            (height - drawHeight) / 2f,
            (width + drawWidth) / 2f,
            (height + drawHeight) / 2f,
        )
    canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))
    if (scrim.alpha > 0f) canvas.drawColor(scrim.toArgb())
    // 半径同步 ×BakeScale，放大回原尺寸后扩散范围不变
    boxBlur(buffer, sigma * BakeScale, checkActive)
    return BakedWallpaper(buffer.asImageBitmap(), width.toFloat() / area.width)
}

/** 三次盒式模糊近似高斯，耗时与半径无关，checkActive 抛异常即中止 */
private fun boxBlur(bitmap: Bitmap, sigma: Float, checkActive: () -> Unit) {
    val radius = sigma.roundToInt()
    if (radius <= 0) return
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val temp = IntArray(pixels.size)
    repeat(3) {
        checkActive()
        blurPass(pixels, temp, width, height, radius, horizontal = true)
        checkActive()
        blurPass(temp, pixels, width, height, radius, horizontal = false)
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

/** 单趟滑动窗口求均，src 读 dst 写，横竖各一次 */
private fun blurPass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val step = if (horizontal) 1 else width
    val window = radius * 2 + 1
    for (line in 0 until lines) {
        val base = if (horizontal) line * width else line
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        // 先累加左侧窗口，越界用边缘像素补齐
        for (i in -radius..radius) {
            val color = src[base + i.coerceIn(0, length - 1) * step]
            a += color ushr 24 and 0xFF
            r += color shr 16 and 0xFF
            g += color shr 8 and 0xFF
            b += color and 0xFF
        }
        for (i in 0 until length) {
            dst[base + i * step] =
                ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            // 窗口右移一格：加入右侧新像素、去掉左侧旧像素
            val out = src[base + (i - radius).coerceIn(0, length - 1) * step]
            val input = src[base + (i + radius + 1).coerceIn(0, length - 1) * step]
            a += (input ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
            r += (input shr 16 and 0xFF) - (out shr 16 and 0xFF)
            g += (input shr 8 and 0xFF) - (out shr 8 and 0xFF)
            b += (input and 0xFF) - (out and 0xFF)
        }
    }
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
