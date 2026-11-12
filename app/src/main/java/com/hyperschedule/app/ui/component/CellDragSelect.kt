package com.hyperschedule.app.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** 格子多选手势，单击切换起止格，按住滑动按起始格状态整段选中或取消 */
@Composable
internal fun Modifier.cellDragSelect(
    enabled: Boolean,
    haptics: HapticFeedback,
    hitTest: (Offset) -> Int?,
    selection: () -> Set<Int>,
    onTap: (cell: Int) -> Unit,
    onRange: (anchor: Int, target: Int, add: Boolean, base: Set<Int>) -> Unit,
    canStart: (Int) -> Boolean = { true },
): Modifier {
    val hit = rememberUpdatedState(hitTest)
    val selectedCells = rememberUpdatedState(selection)
    val startable = rememberUpdatedState(canStart)
    val tap = rememberUpdatedState(onTap)
    val range = rememberUpdatedState(onRange)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val anchor = hit.value(down.position)?.takeIf { startable.value(it) }
            if (anchor == null) {
                // 落在格子外或不可选的格子上，等所有手指抬起，免得被下一格当成续接的手势
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
                return@awaitEachGesture
            }
            // 按下格的状态决定整段是选中还是取消，base 取按下的那一刻，回拖才有依据
            val start: Int = anchor
            val base = selectedCells.value()
            val add = start !in base
            var target = start
            var dragging = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                if (change == null) break
                if (!change.pressed) {
                    if (!dragging) tap.value(start)
                    break
                }
                val delta = change.position - down.position
                if (!dragging &&
                    (abs(delta.x) > viewConfiguration.touchSlop ||
                        abs(delta.y) > viewConfiguration.touchSlop)
                ) {
                    dragging = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    range.value(start, start, add, base)
                    change.consume()
                }
                if (dragging) {
                    change.consume()
                    val cell = hit.value(change.position)
                    if (cell != null && cell != target) {
                        target = cell
                        // 手指每越过一格震动一次
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        range.value(start, cell, add, base)
                    }
                }
            }
        }
    }
}
