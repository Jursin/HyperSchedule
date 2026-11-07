package com.hyperschedule.app.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.theme.CardItemPadding
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 吸附到关键点的距离，占整条轨道的比例 */
private const val KeyPointMagnet = 0.05f

/** 滑块条目，关键点标在默认值上并按档位震动，滑块只认拖动 */
@Composable
internal fun StyleSlider(
    title: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    keyPoint: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // 手势协程按 valueRange/steps 重启，回调用 rememberUpdatedState 取最新值，避免点到旧样式
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnFinished = rememberUpdatedState(onValueChangeFinished)
    BasicComponent(
        title = title,
        insideMargin = CardItemPadding,
        endActions = {
            Text(
                text = valueText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        bottomAction = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(valueRange, steps) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var dragged = false
                                while (true) {
                                    val change =
                                        awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        // 没拖动、也没被滑块自己处理掉的抬手才算点击
                                        if (!dragged && !change.isConsumed) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentOnValueChange.value(
                                                tapValueAt(
                                                    x = change.position.x,
                                                    width = size.width.toFloat(),
                                                    height = size.height.toFloat(),
                                                    valueRange = valueRange,
                                                    steps = steps,
                                                ),
                                            )
                                            currentOnFinished.value()
                                        }
                                        break
                                    }
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        dragged = true
                                    }
                                }
                            }
                        },
            ) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier.fillMaxWidth(),
                    valueRange = valueRange,
                    steps = steps,
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    showKeyPoints = true,
                    keyPoints = listOf(keyPoint),
                    magnetThreshold = KeyPointMagnet,
                )
            }
        },
    )
}

/** 点击位置换算成值，两端扣掉滑块半径后按档位取整 */
private fun tapValueAt(
    x: Float,
    width: Float,
    height: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val thumbRadius = height / 2f
    val travel = (width - 2f * thumbRadius).coerceAtLeast(1f)
    val fraction = ((x - thumbRadius) / travel).coerceIn(0f, 1f)
    val span = valueRange.endInclusive - valueRange.start
    val stepCount = steps + 1
    val index = (fraction * stepCount).roundToInt().coerceIn(0, stepCount)
    return valueRange.start + span * index / stepCount
}
