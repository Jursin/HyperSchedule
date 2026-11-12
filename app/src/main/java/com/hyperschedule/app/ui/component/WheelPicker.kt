package com.hyperschedule.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 惯性摩擦系数 */
private const val FlingFrictionMultiplier = 1.0f

/** 惯性结束的速度阈值（项/秒），减到该速度就带着余速交给吸附动画 */
private const val FlingVelocityThreshold = 3.4f

/** 吸附动画 */
private val SnapSpring: SpringSpec<Float> = spring(dampingRatio = 0.78f, stiffness = 200f)

/** 滚轮规格 */
private object WheelSpec {
    const val VISIBLE_ITEM_COUNT = 3
    val ItemHeight = 60.dp
}

/** 数字滚轮 */
@Composable
fun WheelPicker(
    value: Int,
    range: IntRange,
    label: (Int) -> String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    wrapAround: Boolean = false,
) {
    val itemCount = range.last - range.first + 1
    val currentIndex = value.coerceIn(range) - range.first
    val halfVisibleCount = WheelSpec.VISIBLE_ITEM_COUNT / 2
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // 已提交给外部的值，用来去掉重复回调
    val committedValue by rememberUpdatedState(range.first + currentIndex)
    val hapticFeedback = LocalHapticFeedback.current
    val base = remember { Animatable(currentIndex.toFloat()) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var itemHeightPx by remember { mutableIntStateOf(0) }
    var lastHapticIndex by remember { mutableIntStateOf(currentIndex) }

    // 无限滚动取模，否则夹在两端
    fun normalize(index: Int): Int =
        if (wrapAround) (index % itemCount + itemCount) % itemCount else index.coerceIn(0, itemCount - 1)

    fun tickHaptic(index: Int) {
        if (index != lastHapticIndex) {
            lastHapticIndex = index
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // 虚拟位置换算成 range 内的真实值回调给外部
    fun commitPosition(pos: Float) {
        val newValue = range.first + normalize(pos.roundToInt())
        if (newValue != committedValue) currentOnValueChange(newValue)
    }

    // 惯性落点吸附到最近的整格，无限滚动时保留未取模位置，动画才不会倒着绕回去
    fun snapTarget(projected: Float): Float {
        val rounded = projected.roundToInt().toFloat()
        return if (wrapAround) rounded else rounded.coerceIn(0f, (itemCount - 1).toFloat())
    }

    // 外部改值或 range 变化时把位置同步过去
    LaunchedEffect(currentIndex, itemCount, wrapAround) {
        if (wrapAround) {
            base.updateBounds(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        } else {
            base.updateBounds(0f, (itemCount - 1).toFloat())
        }
        if (!isDragging && !isSettling) {
            dragDelta = 0f
            base.snapTo(currentIndex.toFloat())
            lastHapticIndex = currentIndex
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (itemHeightPx > 0) {
            val raw = base.value + dragDelta - delta / itemHeightPx
            val position = if (wrapAround) raw else raw.coerceIn(0f, (itemCount - 1).toFloat())
            dragDelta = position - base.value
            // 拖动过程中就实时提交，手指还没抬起时点「确定」也能拿到正确的值
            commitPosition(position)
            tickHaptic(position.roundToInt())
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(WheelSpec.ItemHeight * WheelSpec.VISIBLE_ITEM_COUNT)
                .clipToBounds()
                .semantics {
                    contentDescription = "${label(range.first + currentIndex)}, ${range.first} - ${range.last}"
                }
                .onSizeChanged { itemHeightPx = it.height / WheelSpec.VISIBLE_ITEM_COUNT }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = draggableState,
                    onDragStarted = {
                        isDragging = true
                        // 打断进行中的惯性/吸附动画，从当前视觉位置接着拖
                        base.stop()
                        dragDelta = 0f
                    },
                    onDragStopped = { velocity ->
                        isDragging = false
                        if (itemHeightPx > 0) {
                            // 拖动位移并入动画基准
                            val startPosition = base.value + dragDelta
                            dragDelta = 0f
                            base.snapTo(startPosition)

                            val decay =
                                exponentialDecay<Float>(
                                    frictionMultiplier = FlingFrictionMultiplier,
                                    absVelocityThreshold = FlingVelocityThreshold,
                                )
                            val velocityInItems = -velocity / itemHeightPx
                            // 先算出惯性最终停在哪一格并立刻提交，动画结束前点「确定」拿到的也是这一项
                            val target = snapTarget(decay.calculateTargetValue(startPosition, velocityInItems))
                            isSettling = true
                            commitPosition(target)
                            try {
                                val decayResult =
                                    base.animateDecay(velocityInItems, decay) { tickHaptic(base.value.roundToInt()) }
                                // 用惯性残余速度衔接吸附，避免「滑行→顿一下→吸附」的割裂感
                                base.animateTo(target, SnapSpring, decayResult.endState.velocity) {
                                    tickHaptic(base.value.roundToInt())
                                }
                                // 无限滚动时位置会一直累加，归一化避免长时间滚动后精度下降
                                if (wrapAround && !isDragging) {
                                    val settled = normalize(base.value.roundToInt())
                                    base.snapTo(settled.toFloat())
                                    lastHapticIndex = settled
                                }
                            } finally {
                                if (!isDragging) isSettling = false
                            }
                        }
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (itemHeightPx > 0) {
            val position = base.value + dragDelta
            val roundedOffset = position.roundToInt()
            val centerItemOffset = position - roundedOffset
            val selectedColor = MiuixTheme.colorScheme.onSurface
            val unselectedColor = MiuixTheme.colorScheme.onSurfaceSecondary
            val title = MiuixTheme.textStyles.title2
            val textStyle = if (title.fontWeight == null) title.copy(fontWeight = FontWeight.SemiBold) else title

            for (offset in -halfVisibleCount - 1..halfVisibleCount + 1) {
                val rawIndex = roundedOffset + offset
                val itemIndex =
                    if (wrapAround) {
                        normalize(rawIndex)
                    } else {
                        if (rawIndex !in 0..<itemCount) continue
                        rawIndex
                    }

                val distanceFromCenter = offset.toFloat() - centerItemOffset
                val normalizedDistance = (abs(distanceFromCenter) / (halfVisibleCount + 0.5f)).coerceIn(0f, 1f)
                val alpha = (1f - normalizedDistance) * (1f - normalizedDistance * 0.5f)
                val scale = 1f - 0.2f * normalizedDistance

                Text(
                    text = label(range.first + itemIndex),
                    modifier =
                        Modifier.graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            translationY = distanceFromCenter * itemHeightPx
                        },
                    style = textStyle,
                    color =
                        Color(
                            red = lerp(selectedColor.red, unselectedColor.red, normalizedDistance),
                            green = lerp(selectedColor.green, unselectedColor.green, normalizedDistance),
                            blue = lerp(selectedColor.blue, unselectedColor.blue, normalizedDistance),
                            alpha = lerp(selectedColor.alpha, unselectedColor.alpha, normalizedDistance),
                        ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
