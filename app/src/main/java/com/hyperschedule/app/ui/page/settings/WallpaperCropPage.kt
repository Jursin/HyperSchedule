package com.hyperschedule.app.ui.page.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hyperschedule.app.data.local.WallpaperStore
import com.hyperschedule.app.ui.component.BlurredTopAppBar
import com.hyperschedule.app.ui.component.SaveFab
import com.hyperschedule.app.ui.effect.captureForBlur
import com.hyperschedule.app.ui.effect.rememberAppBlurBackdrop
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 裁剪框最大放大倍数 */
private const val MAX_CROP_ZOOM = 5f

/** 双击放大到的倍数 */
private const val DOUBLE_TAP_ZOOM = 2.5f

/** 存盘长边上限 */
private const val OUTPUT_MAX_SIDE = 2160

/** 框外压暗的程度，框内保持原色 */
private const val OUTSIDE_SCRIM_ALPHA = 0.62f

/** 裁剪框离页面边的距离 */
private val CropAreaMargin = 24.dp

/** 折角的臂长占框宽的比例 */
private const val CORNER_ARM_RATIO = 0.05f

/** 折角臂长上下限 */
private val CornerArmMin = 10.dp
private val CornerArmMax = 24.dp

/** 折角线的粗细 */
private val CornerStroke = 2.5.dp

/** 参考线的粗细与浓度 */
private val GridStroke = 0.6.dp
private const val GRID_ALPHA = 0.45f

/** 保存按钮边距 */
private val FabSpan = 96.dp

/** 壁纸裁剪 */
@Composable
fun WallpaperCropPage(
    uri: String,
    appBlur: Boolean = false,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val scope = rememberCoroutineScope()
    // 裁剪比例取当前窗口，转屏与分屏变化时跟着换
    val window = LocalWindowInfo.current.containerDpSize
    val screenAspect = if (window.height.value > 0f) window.width.value / window.height.value else 1f
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var saveTick by remember { mutableIntStateOf(0) }
    val crop = remember { CropState() }

    LaunchedEffect(uri) {
        val loaded = withContext(Dispatchers.IO) { WallpaperStore.decode(context, uri.toUri()) }
        bitmap = loaded
        unavailable = loaded == null
    }

    val hint =
        when {
            saving -> "正在保存…"
            unavailable -> "无法读取这张图片"
            saveFailed -> "保存失败，请重试"
            else -> null
        }

    Scaffold(
        topBar = {
            BlurredTopAppBar(
                title = "裁剪壁纸",
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                pinnedTitle = true,
                onBack = if (saving) null else onBack,
            )
        },
        floatingActionButton = {
            SaveFab(canSave = bitmap != null && !saving) {
                if (bitmap != null && !saving) saveTick++
            }
        },
    ) { paddingValues ->
        CropArea(
            bitmap = bitmap,
            hint = hint,
            saveTick = saveTick,
            crop = crop,
            blurBackdrop = blurBackdrop,
            screenAspect = screenAspect,
            topInset = paddingValues.calculateTopPadding(),
            onSave = { rect, target ->
                val source = bitmap
                if (source != null) {
                    saveFailed = false
                    saving = true
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { source.cropToFile(context, rect, target) }
                        saving = false
                        if (path != null) onSaved(path) else saveFailed = true
                    }
                }
            },
        )
    }
}

/** 缩放与位移 */
private class CropState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
}

@Composable
private fun CropArea(
    bitmap: Bitmap?,
    hint: String?,
    saveTick: Int,
    crop: CropState,
    blurBackdrop: LayerBackdrop?,
    screenAspect: Float,
    topInset: Dp,
    onSave: (Rect, Size) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val source = remember(bitmap) { bitmap?.asImageBitmap() }
    val shown = source?.let { IntSize(it.width, it.height) }
    val density = LocalDensity.current
    val marginPx = with(density) { CropAreaMargin.toPx() }
    val fabSpan = with(density) { FabSpan.toPx() }
    val zoomJob = remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .then(if (blurBackdrop != null) Modifier.captureForBlur(blurBackdrop) else Modifier)
                .padding(top = topInset),
    ) {
        // 框取整屏比例
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val maxWidth = (areaWidth - marginPx * 2f).coerceAtLeast(1f)
        val evenHeight = min((areaHeight - marginPx * 2f).coerceAtLeast(1f), maxWidth / screenAspect)
        val hitsFab = (areaWidth + evenHeight * screenAspect) / 2f > areaWidth - fabSpan
        val bandBottom = areaHeight - (if (hitsFab) fabSpan else marginPx)
        val band = (bandBottom - marginPx).coerceAtLeast(1f)
        val frameHeight = min(band, maxWidth / screenAspect)
        val frame = Size(frameHeight * screenAspect, frameHeight)
        val frameTop = marginPx + (band - frameHeight) / 2f
        // 保存请求只处理一次
        var handledTick by remember { mutableIntStateOf(0) }
        LaunchedEffect(saveTick, shown, frame) {
            if (saveTick == 0 || saveTick == handledTick || shown == null || frame.width <= 0f) {
                return@LaunchedEffect
            }
            handledTick = saveTick
            val rect = sourceRect(shown, frame, crop.scale, crop.offset)
            onSave(rect, outputSize(frame, rect))
        }

        // 图和框都有了才画，没图时下面只挂提示
        if (source != null && shown != null) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(shown, frame, frameTop) {
                            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                                zoomJob.value?.cancel()
                                // 锚点取裁剪框中心
                                val centre = Offset(this.size.width / 2f, frameTop + frame.height / 2f)
                                val fromCentre = centroid - centre
                                val next = (crop.scale * zoom).coerceIn(1f, MAX_CROP_ZOOM)
                                // 以双指中心为锚缩放，锚点下的画面不动，再叠加拖动
                                val moved = fromCentre - (fromCentre - crop.offset) * (next / crop.scale) + pan
                                crop.scale = next
                                crop.offset = clampOffset(moved, displaySize(shown, frame, next), frame)
                            }
                        }
                        .pointerInput(shown, frame, frameTop) {
                            detectTapGestures(
                                onDoubleTap = { point ->
                                    val centre = Offset(this.size.width / 2f, frameTop + frame.height / 2f)
                                    val fromCentre = point - centre
                                    val target = if (crop.scale > 1.05f) 1f else DOUBLE_TAP_ZOOM
                                    val moved = fromCentre - (fromCentre - crop.offset) * (target / crop.scale)
                                    val toOffset = clampOffset(moved, displaySize(shown, frame, target), frame)
                                    val fromScale = crop.scale
                                    val fromOffset = crop.offset
                                    zoomJob.value?.cancel()
                                    zoomJob.value =
                                        scope.launch {
                                            animate(0f, 1f, animationSpec = tween(durationMillis = 220)) { value, _ ->
                                                crop.scale = fromScale + (target - fromScale) * value
                                                crop.offset = lerp(fromOffset, toOffset, value)
                                            }
                                        }
                                },
                            )
                        },
            ) {
                val frameLeft = (size.width - frame.width) / 2f
                val display = displaySize(shown, frame, crop.scale)
                val origin =
                    Offset(
                        x = frameLeft + (frame.width - display.width) / 2f + crop.offset.x,
                        y = frameTop + (frame.height - display.height) / 2f + crop.offset.y,
                    )
                // 整张图先铺满画布，框外部分交给蒙版压暗
                drawImage(
                    image = source,
                    srcOffset = IntOffset.Zero,
                    srcSize = shown,
                    dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                    dstSize = IntSize(display.width.roundToInt(), display.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                drawCropOverlay(
                    frame = Rect(left = frameLeft, top = frameTop, right = frameLeft + frame.width, bottom = frameTop + frame.height),
                    image = Rect(offset = origin, size = display),
                )
            }
        }

        if (hint != null) {
            Text(
                text = hint,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(percent = 50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** 裁剪框叠层 */
private fun DrawScope.drawCropOverlay(frame: Rect, image: Rect) {
    val scrim = Color.Black.copy(alpha = OUTSIDE_SCRIM_ALPHA)
    clipRect(image.left, image.top, image.right, image.bottom) {
        drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, frame.top))
        drawRect(scrim, topLeft = Offset(0f, frame.bottom), size = Size(size.width, size.height - frame.bottom))
        drawRect(scrim, topLeft = Offset(0f, frame.top), size = Size(frame.left, frame.height))
        drawRect(scrim, topLeft = Offset(frame.right, frame.top), size = Size(size.width - frame.right, frame.height))
    }

    val gridStroke = GridStroke.toPx()
    val guides = buildList {
        for (i in 1..2) {
            val x = frame.left + frame.width * i / 3f
            val y = frame.top + frame.height * i / 3f
            add(Offset(x, frame.top) to Offset(x, frame.bottom))
            add(Offset(frame.left, y) to Offset(frame.right, y))
        }
    }
    guides.forEach { (from, to) -> drawLine(Color.Black.copy(alpha = 0.15f), from, to, gridStroke * 1.8f) }
    guides.forEach { (from, to) -> drawLine(Color.White.copy(alpha = GRID_ALPHA), from, to, gridStroke) }

    // 折角整体在裁剪区域外侧，内缘贴着框边
    val arm = (frame.width * CORNER_ARM_RATIO).coerceIn(CornerArmMin.toPx(), CornerArmMax.toPx())
    val edge = CornerStroke.toPx()

    fun cornerArms(x: Float, y: Float, dx: Float, dy: Float): List<Rect> {
        val hLeft = if (dx > 0f) x - edge else x + edge - arm
        val hTop = if (dy > 0f) y - edge else y
        val vLeft = if (dx > 0f) x - edge else x
        val vTop = if (dy > 0f) y - edge else y + edge - arm
        return listOf(
            Rect(hLeft, hTop, hLeft + arm, hTop + edge),
            Rect(vLeft, vTop, vLeft + edge, vTop + arm),
        )
    }

    val arms =
        cornerArms(frame.left, frame.top, 1f, 1f) +
            cornerArms(frame.right, frame.top, -1f, 1f) +
            cornerArms(frame.left, frame.bottom, 1f, -1f) +
            cornerArms(frame.right, frame.bottom, -1f, -1f)
    arms.forEach { drawRect(Color.White, topLeft = it.topLeft, size = it.size) }
}

/** 按 Cover 铺满裁剪框的基础缩放 */
private fun baseScale(image: IntSize, frame: Size): Float =
    max(frame.width / image.width, frame.height / image.height)

private fun displaySize(image: IntSize, frame: Size, scale: Float): Size {
    val k = baseScale(image, frame) * scale
    return Size(image.width * k, image.height * k)
}

/** 位移上限 */
private fun clampOffset(offset: Offset, display: Size, frame: Size): Offset {
    val maxX = ((display.width - frame.width) / 2f).coerceAtLeast(0f)
    val maxY = ((display.height - frame.height) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** 裁剪框对应的原图像素范围 */
private fun sourceRect(image: IntSize, frame: Size, scale: Float, offset: Offset): Rect {
    val k = baseScale(image, frame) * scale
    // 整图按 k 缩放后，框左上角在图片里的位置就是把原点反过来除回去
    val originX = (frame.width - image.width * k) / 2f + offset.x
    val originY = (frame.height - image.height * k) / 2f + offset.y
    val left = -originX / k
    val top = -originY / k
    return Rect(
        left = left.coerceIn(0f, image.width - 1f),
        top = top.coerceIn(0f, image.height - 1f),
        right = (left + frame.width / k).coerceIn(1f, image.width.toFloat()),
        bottom = (top + frame.height / k).coerceIn(1f, image.height.toFloat()),
    )
}

/** 存盘尺寸，长边按原图裁切区取并在上限内收住 */
private fun outputSize(frame: Size, source: Rect): Size {
    val frameLong = max(frame.width, frame.height)
    val targetLong = max(frameLong, min(max(source.width, source.height), OUTPUT_MAX_SIDE.toFloat()))
    val factor = targetLong / frameLong
    return Size(frame.width * factor, frame.height * factor)
}

/** 把裁剪范围按存盘尺寸重采样写成 JPEG */
private fun Bitmap.cropToFile(context: Context, rect: Rect, target: Size): String? {
    val left = rect.left.roundToInt().coerceIn(0, width - 1)
    val top = rect.top.roundToInt().coerceIn(0, height - 1)
    val cropWidth = rect.width.roundToInt().coerceIn(1, width - left)
    val cropHeight = rect.height.roundToInt().coerceIn(1, height - top)
    val cropped = Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
    val outWidth = target.width.roundToInt().coerceAtLeast(1)
    val outHeight = target.height.roundToInt().coerceAtLeast(1)
    val output = Bitmap.createScaledBitmap(cropped, outWidth, outHeight, true)
    if (cropped !== this && cropped !== output) cropped.recycle()
    return WallpaperStore.save(context, output)
}
