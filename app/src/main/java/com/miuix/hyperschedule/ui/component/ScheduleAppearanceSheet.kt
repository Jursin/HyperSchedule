package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.ScheduleStyle
import com.miuix.hyperschedule.theme.SheetBottomInset
import com.miuix.hyperschedule.theme.sheetCardColor
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课程表外观抽屉：改动即时生效，不需要「应用」按钮 */
@Composable
fun ScheduleAppearanceSheet(
    show: Boolean,
    style: ScheduleStyle,
    onStyleChange: (ScheduleStyle) -> Unit,
    onStyleCommit: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    // 拖动滑块只改内存让课表实时跟着变，手势结束再落盘
    fun applyStyle(updated: ScheduleStyle) {
        onStyleChange(updated)
        onStyleCommit()
    }
    SheetScaffold(
        title = "课程表外观",
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        SheetScrollColumn {
            PreferenceSection(cardColor = sheetCardColor(), title = "网格") {
                SwitchPreference(
                    checked = style.gridLines,
                    onCheckedChange = { applyStyle(style.copy(gridLines = it)) },
                    title = "显示网格线",
                )
                SwitchPreference(
                    checked = style.breakDividers,
                    onCheckedChange = { applyStyle(style.copy(breakDividers = it)) },
                    title = "显示午休、晚休分界线",
                )
            }

            PreferenceSection(cardColor = sheetCardColor(), title = "卡片") {
                StyleSlider(
                    title = "课程卡片高度",
                    value = style.cardHeight.toFloat(),
                    valueText = "${style.cardHeight}dp",
                    valueRange = ScheduleStyle.MIN_CARD_HEIGHT.toFloat()..ScheduleStyle.MAX_CARD_HEIGHT.toFloat(),
                    steps = ScheduleStyle.MAX_CARD_HEIGHT - ScheduleStyle.MIN_CARD_HEIGHT - 1,
                    keyPoint = ScheduleStyle.Default.cardHeight.toFloat(),
                    onValueChange = { value ->
                        onStyleChange(style.copy(cardHeight = value.roundToInt()))
                    },
                    onValueChangeFinished = onStyleCommit,
                )
                StyleSlider(
                    title = "课程卡片圆角",
                    value = style.cardCorner.toFloat(),
                    valueText = "${style.cardCorner}dp",
                    valueRange = 0f..ScheduleStyle.MAX_CARD_CORNER.toFloat(),
                    steps = ScheduleStyle.MAX_CARD_CORNER - 1,
                    keyPoint = ScheduleStyle.Default.cardCorner.toFloat(),
                    onValueChange = { value ->
                        onStyleChange(style.copy(cardCorner = value.roundToInt()))
                    },
                    onValueChangeFinished = onStyleCommit,
                )
                SwitchPreference(
                    checked = style.cardBorder,
                    onCheckedChange = { applyStyle(style.copy(cardBorder = it)) },
                    title = "显示卡片边框",
                )
            }

            PreferenceSection(cardColor = sheetCardColor(), title = "内容") {
                // 抽屉挂在浮层里，Overlay 版下拉弹层会被抽屉盖住，用窗口版才会浮在最上层
                WindowDropdownPreference(
                    title = "卡片内容对齐方式",
                    items = listOf("居中", "左对齐"),
                    selectedIndex = if (style.contentCenter) 0 else 1,
                    onSelectedIndexChange = { index ->
                        applyStyle(style.copy(contentCenter = index == 0))
                    },
                )
                StyleSlider(
                    title = "文字缩放比例",
                    value = style.textScale,
                    valueText = String.format(Locale.ROOT, "%.2f x", style.textScale),
                    valueRange = ScheduleStyle.MIN_TEXT_SCALE..ScheduleStyle.MAX_TEXT_SCALE,
                    steps =
                        ((ScheduleStyle.MAX_TEXT_SCALE - ScheduleStyle.MIN_TEXT_SCALE) /
                            ScheduleStyle.TEXT_SCALE_STEP).roundToInt() - 1,
                    keyPoint = ScheduleStyle.Default.textScale,
                    onValueChange = { value ->
                        onStyleChange(style.copy(textScale = ScheduleStyle.snapTextScale(value)))
                    },
                    onValueChangeFinished = onStyleCommit,
                )
                SwitchPreference(
                    checked = style.showPosition,
                    onCheckedChange = { applyStyle(style.copy(showPosition = it)) },
                    title = "显示地点",
                )
                SwitchPreference(
                    checked = style.showTeacher,
                    onCheckedChange = { applyStyle(style.copy(showTeacher = it)) },
                    title = "显示教师",
                )
                SwitchPreference(
                    checked = style.positionPrefix,
                    onCheckedChange = { applyStyle(style.copy(positionPrefix = it)) },
                    title = "地点前显示 @ 符号",
                )
            }

            // 抽屉底下压着悬浮导航栏，留出位置让最后一项能完整露出来
            Spacer(modifier = Modifier.height(SheetBottomInset))
        }
    }
}

/** 吸附到关键点的距离，占整条轨道的比例（与 Slider 的默认手感一致） */
private const val KeyPointMagnet = 0.05f

/**
 * 滑块条目：关键点标在默认值上，按档位震动。
 *
 * Miuix 的 Slider 只认拖动（要越过触摸阈值才开始拖），这里在轨道上补一层点击：
 * 点哪儿就跳到哪儿，换算方式与 Slider 内部一致（两端扣掉滑块半径，再按档位取整）。
 */
@Composable
private fun StyleSlider(
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

/** 点击位置换算成值：与 Slider 内部的 `horizontalVisualFraction` + 档位取整保持同一套算法 */
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
