package com.miuix.hyperschedule.ui.component

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.miuix.hyperschedule.theme.isDarkTheme
import com.miuix.hyperschedule.ui.effect.navBarBlur
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop

@Composable
fun BackToNowButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blurBackdrop: LayerBackdrop? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter =
            scaleIn(initialScale = 0.5f, animationSpec = tween(100, easing = LinearEasing)) +
                fadeIn(animationSpec = tween(100, easing = LinearEasing)),
        exit =
            scaleOut(targetScale = 0.5f, animationSpec = tween(100, easing = LinearEasing)) +
                fadeOut(animationSpec = tween(100, easing = LinearEasing)),
        modifier = modifier,
    ) {
        BackToNowButtonBody(onClick = onClick, blurBackdrop = blurBackdrop)
    }
}

@Composable
private fun BackToNowButtonBody(onClick: () -> Unit, blurBackdrop: LayerBackdrop?) {
    val isDark = isDarkTheme()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val fillColor = if (isDark) Color(0xCC3C81FFL) else Color(0xDB277AFFL)
    val outerColor = if (isDark) Color(0xCC3C81FFL) else Color(0xCC277AFFL)

    var pressed by remember { mutableStateOf(false) }
    // 按钮像素尺寸：手势容差判定与光晕位图尺寸
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }

    val pressScale by
        animateFloatAsState(
            targetValue = if (pressed) BackToNowPressScale else 1f,
            animationSpec =
                spring(
                    dampingRatio = 0.99f,
                    stiffness =
                        if (pressed) BackToNowPressStiffnessDown else BackToNowPressStiffnessUp,
                ),
            label = "backToNowScale",
        )

    val glowImage =
        remember(buttonSize, isDark) {
            buttonSize.takeIf { it != IntSize.Zero }?.let { size ->
                buildGlowBitmap(
                        btnW = size.width,
                        btnH = size.height,
                        pad = with(density) { BackToNowGlowPad.toPx() }.roundToInt(),
                        dy = with(density) { BackToNowGlowDy.toPx() },
                        radiusPx = with(density) { BackToNowCornerRadius.toPx() },
                        color = if (isDark) GlowColorDark else GlowColorLight,
                    )
                    .asImageBitmap()
            }
        }

    Box(
        modifier =
            Modifier
                // 光晕与按钮一起缩放；clip=false 防止层裁剪切掉外溢光晕
                .graphicsLayer {
                    clip = false
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .drawBehind { glowImage?.let { drawImage(it, Offset.Zero) } }
                // drawBehind 在 padding 之前，按整盒坐标绘制
                .padding(BackToNowGlowPad),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(BackToNowWidth, BackToNowHeight)
                    .navBarBlur(
                        backdrop = blurBackdrop,
                        shape = RoundedCornerShape(BackToNowCornerRadius),
                        blurRadius = BackToNowBlurRadius,
                        blendColor = fillColor,
                    )
                    .pointerInput(buttonSize) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // 立刻消费：按下"今"按钮后，手指横移不再翻动下层 pager
                            down.consume()

                            // 容差 12dp：按住后小幅移动不算取消，避免误触
                            val tolerance = 12.dp.toPx()
                            var inside = true
                            pressed = true

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }

                                if (change == null || !change.pressed) {
                                    val wasInside = inside
                                    change?.consume()
                                    pressed = false
                                    // 滑出按钮后松手 = 取消，不触发跳转
                                    if (wasInside) {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onClick()
                                    }
                                    break
                                }

                                change.consume()
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                val nowInside =
                                    abs(dx) <= buttonSize.width / 2f + tolerance &&
                                        abs(dy) <= buttonSize.height / 2f + tolerance
                                if (nowInside != inside) {
                                    inside = nowInside
                                    pressed = inside
                                }
                            }
                        }
                    }
                    .onSizeChanged { buttonSize = it }
                    .semantics { contentDescription = "今" }
                    // 填充 + 内缩半宽双描边；有模糊时填充交给模糊层
                    .drawBehind {
                        val radius = BackToNowCornerRadius.toPx()
                        val s = 1.dp.toPx()
                        if (blurBackdrop == null) drawRoundRect(fillColor, cornerRadius = CornerRadius(radius))
                        drawRoundRect(
                            color = outerColor,
                            topLeft = Offset(s / 2, s / 2),
                            size = Size(size.width - s, size.height - s),
                            cornerRadius = CornerRadius(radius),
                            style = Stroke(s),
                        )
                        drawRoundRect(
                            color = InnerBorderColor,
                            topLeft = Offset(s * 1.5f, s * 1.5f),
                            size = Size(size.width - 3 * s, size.height - 3 * s),
                            cornerRadius = CornerRadius(radius),
                            style = Stroke(s),
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "今",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }
}

private val BackToNowWidth = 80.dp
private val BackToNowHeight = 44.dp
private val BackToNowCornerRadius = 80.dp
private val BackToNowGlowDy = 8.dp

/** 光晕外溢区 = 40dp 模糊空间 + 8dp 下移 */
private val BackToNowGlowPad = 48.dp

/** 背景模糊半径 */
private const val BackToNowBlurRadius = 3f

/** 光晕模糊半径（半分辨率位图，换算成全尺寸 σ） */
private const val BackToNowGlowBlurPx = 40f

/** 光晕颜色：浅色 / 深色 */
private const val GlowColorLight = 0x333C81FF
private const val GlowColorDark = 0x4D3482FF
private val InnerBorderColor = Color(0x33FFFFFF)

/** 按压缩放下限 */
private const val BackToNowPressScale = 0.9f

/** 按压回弹刚度：按下 / 松手 */
private const val BackToNowPressStiffnessDown = 1755f
private const val BackToNowPressStiffnessUp = 439f

/**
 * 预渲染光晕位图：画下移 [dy] 的胶囊 → 模糊 → DST_OUT 挖掉按钮区域
 * 必须用软件 Canvas，硬件 Canvas 不支持 BlurMaskFilter
 */
private fun buildGlowBitmap(
    btnW: Int,
    btnH: Int,
    pad: Int,
    dy: Float,
    radiusPx: Float,
    color: Int,
): Bitmap {
    val bitmap = createBitmap(btnW + 2 * pad, btnH + 2 * pad)
    val canvas = Canvas(bitmap)
    val glow =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(BackToNowGlowBlurPx, BlurMaskFilter.Blur.NORMAL)
        }
    canvas.drawRoundRect(
        RectF(pad.toFloat(), pad + dy, (pad + btnW).toFloat(), pad + dy + btnH),
        radiusPx,
        radiusPx,
        glow,
    )
    val punch =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    canvas.drawRoundRect(
        RectF(pad.toFloat(), pad.toFloat(), (pad + btnW).toFloat(), (pad + btnH).toFloat()),
        radiusPx,
        radiusPx,
        punch,
    )
    return bitmap
}

/** 单个 tab 上报给共享「今」按钮的状态；切页时宿主换槽位，按钮就地收起/展开 */
class BackToNowSlot(val visible: State<Boolean>, val onClick: () -> Unit)

class BackToNowHostState {
    internal val slots = mutableStateMapOf<Int, BackToNowSlot>()

    fun slotOf(page: Int): BackToNowSlot? = slots[page]
}

val LocalBackToNowHost = compositionLocalOf<BackToNowHostState?> { null }

/** 当前内容所属的 tab 页码（即 MainScaffold 传入的 page） */
val LocalTabPageIndex = compositionLocalOf { -1 }

/** 把本页的「今」按钮状态挂到跨页共享的宿主上；[visible] 须是 remember 出来的稳定 State */
@Composable
fun ProvideBackToNow(visible: State<Boolean>, onClick: () -> Unit) {
    val host = LocalBackToNowHost.current ?: return
    val page = LocalTabPageIndex.current
    // 点击闭包每次重组都会换实例，包一层避免宿主跟着重组
    val latestOnClick = rememberUpdatedState(onClick)
    val slot = remember(page, visible) { BackToNowSlot(visible) { latestOnClick.value() } }
    DisposableEffect(page, host, slot) {
        host.slots[page] = slot
        onDispose {
            if (host.slots[page] === slot) host.slots.remove(page)
        }
    }
}
