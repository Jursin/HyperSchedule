package com.miuix.hyperschedule.ui.page.schedule

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** 落页任务的槽位 */
internal class PagerSettleSlot {
    var job: Job? = null
}

/** 松手后的横向落页 */
private suspend fun settleSchedulePager(
    pagerState: PagerState,
    velocityX: Float,
    flickPx: Float,
    startOffset: Float,
    fingerDragPages: Float,
    pages: IntRange,
) {
    val pageCount = pagerState.pageCount
    if (pageCount <= 0) return
    val lo = pages.first.coerceIn(0, pageCount - 1)
    val hi = pages.last.coerceIn(lo, pageCount - 1)
    val visual = pagerState.currentPage + pagerState.currentPageOffsetFraction
    // 位移优先取手指净位移
    val pagerDrag = visual - startOffset
    val drag = if (abs(fingerDragPages) > abs(pagerDrag)) fingerDragPages else pagerDrag
    val flick =
        when {
            velocityX <= -flickPx -> 1
            velocityX >= flickPx -> -1
            else -> 0
        }
    val fights = flick != 0 && (flick > 0) == (drag > 0)
    val step =
        when {
            fights -> if (abs(drag) >= 0.15f) (if (drag > 0f) 1 else -1) else 0
            drag >= 0.25f -> 1
            drag <= -0.25f -> -1
            else -> flick
        }
    val from = startOffset.roundToInt().coerceIn(lo, hi)
    val target =
        when (step) {
            1 -> max(visual.roundToInt(), from + 1)
            -1 -> min(visual.roundToInt(), from - 1)
            else -> visual.roundToInt()
        }.coerceIn(lo, hi)
    pagerState.animateScrollToPage(
        target,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
    )
}

@Composable
internal fun Modifier.schedulePagerGesture(
    pagerState: PagerState,
    settleSlot: PagerSettleSlot,
    enabled: () -> Boolean,
    blockedPointer: () -> PointerId?,
    // 可翻的页范围
    pages: () -> IntRange,
    onVerticalScroll: (dy: Float) -> Unit,
): Modifier {
    val latestEnabled = rememberUpdatedState(enabled)
    val latestBlocked = rememberUpdatedState(blockedPointer)
    val latestPages = rememberUpdatedState(pages)
    val latestVertical = rememberUpdatedState(onVerticalScroll)
    val scope = rememberCoroutineScope()
    return this.pointerInput(pagerState) {
        val touchSlop = viewConfiguration.touchSlop
        val flickPx = 100.dp.toPx()
        awaitEachGesture {
            val viewportPx = size.width.toFloat().coerceAtLeast(1f)
            var down = awaitFirstDown(requireUnconsumed = false)
            // 追踪的是不是「第二根手指」
            var altPointer = false
            var tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var startOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
            var roundStart = down.uptimeMillis
            var lastMove = down.uptimeMillis
            var accX = 0f
            var accY = 0f
            var locked = false
            var xDominant = false
            var dragChannel: Channel<Float>? = null
            var workerJob: Job? = null
            var workerDone = CompletableDeferred<Unit>()

            fun settleRound() {
                if (!xDominant) return
                val trackedVx = runCatching { tracker.calculateVelocity().x }.getOrNull() ?: 0f
                val span = (lastMove - roundStart).coerceAtLeast(16L)
                val avgVx = accX * 1000f / span
                // 回带时以平均速度为准
                val vx =
                    when {
                        avgVx != 0f && trackedVx != 0f && (avgVx > 0f) != (trackedVx > 0f) -> avgVx
                        abs(avgVx) > abs(trackedVx) -> avgVx
                        else -> trackedVx
                    }
                val done = workerDone
                if (workerJob == null) done.complete(Unit)
                // 先快照，协程跑到时本轮已被清空
                val from = startOffset
                val dragPages = -accX / viewportPx
                val range = latestPages.value()
                settleSlot.job?.cancel()
                settleSlot.job =
                    scope.launch {
                        done.await()
                        runCatching { settleSchedulePager(pagerState, vx, flickPx, from, dragPages, range) }
                    }
            }

            /** 判活不判空 */
            fun ensureWorker() {
                if (workerJob?.isActive == true) return
                dragChannel?.close()
                workerJob?.cancel()
                settleSlot.job?.cancel()
                settleSlot.job = null
                val channel = Channel<Float>(Channel.UNLIMITED)
                dragChannel = channel
                workerDone = CompletableDeferred()
                val roundDone = workerDone
                workerJob =
                    scope.launch {
                        try {
                            pagerState.scroll(MutatePriority.UserInput) {
                                startOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                for (pixel in channel) {
                                    // 手指带着课卡时不能滑出可翻范围
                                    val visual = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                    val range = latestPages.value()
                                    val last = (pagerState.pageCount - 1).coerceAtLeast(0)
                                    val lo = range.first.coerceIn(0, last).toFloat()
                                    val hi = range.last.coerceIn(0, last).toFloat().coerceAtLeast(lo)
                                    val room = if (pixel > 0f) (hi - visual) * viewportPx else (lo - visual) * viewportPx
                                    val delta = if (pixel > 0f) min(pixel, room) else max(pixel, room)
                                    if (delta != 0f) scrollBy(delta)
                                }
                            }
                        } finally {
                            roundDone.complete(Unit)
                        }
                    }
            }

            /** 为下一根手指准备一轮干净状态 */
            fun resetRound() {
                tracker = VelocityTracker()
                startOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                accX = 0f
                accY = 0f
                locked = false
                xDominant = false
            }

            /** 换一根手指接着追踪 */
            fun switchTo(next: PointerInputChange, alt: Boolean) {
                dragChannel?.close()
                dragChannel = null
                settleRound()
                workerJob = null
                resetRound()
                down = next
                altPointer = alt
                tracker.addPosition(next.uptimeMillis, next.position)
                roundStart = next.uptimeMillis
                lastMove = next.uptimeMillis
            }

            // 任何退出路径都要关掉 channel
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    val blocked = latestBlocked.value()
                    if (change == null || blocked == down.id) {
                        val alt = event.changes.firstOrNull { it.pressed && it.id != down.id && it.id != blocked }
                        if (alt != null) {
                            switchTo(alt, true)
                            continue
                        }
                        if (change == null && event.changes.none { it.pressed }) break
                        if (change != null && !change.pressed) break
                        continue
                    }

                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        // 被追踪的手指抬起
                        val alt = event.changes.firstOrNull { it.pressed && it.id != blocked }
                        if (alt != null) {
                            switchTo(alt, true)
                            continue
                        }
                        // 拖课卡那根还按着
                        val stillDown =
                            blocked?.let { id -> event.changes.firstOrNull { it.id == id && it.pressed } }
                        if (stillDown != null) {
                            switchTo(stillDown, false)
                            continue
                        }
                        break
                    }
                    if (!latestEnabled.value()) break

                    val delta = change.positionChangeIgnoreConsumed()
                    val dx = delta.x
                    val dy = delta.y
                    accX += dx
                    accY += dy
                    if (dx != 0f || dy != 0f) lastMove = change.uptimeMillis
                    if (!locked) {
                        val ax = abs(accX)
                        val ay = abs(accY)
                        when {
                            // 横向不弱于纵向就判横
                            ax >= touchSlop && ax >= ay -> {
                                xDominant = true
                                locked = true
                            }
                            ay >= touchSlop && ay > ax * 1.3f -> locked = true
                            // 两轴接近
                            ax >= touchSlop && ay >= touchSlop -> {
                                xDominant = ax >= ay
                                locked = true
                            }
                        }
                        if (xDominant) ensureWorker()
                    } else if (!xDominant && altPointer && abs(accX) >= touchSlop && abs(accX) > abs(accY) * 1.5f) {
                        // 起手判成纵向后仍可改判
                        xDominant = true
                        ensureWorker()
                    }
                    if (!xDominant) {
                        // 第二根手指纵滑
                        if (altPointer && locked && dy != 0f) {
                            latestVertical.value(dy)
                            change.consume()
                        }
                        continue
                    }
                    // scrollBy 坐标与手指相反
                    dragChannel?.trySend(-dx)
                    change.consume()
                }
            } finally {
                dragChannel?.close()
                dragChannel = null
            }
            // 先落页再丢 worker
            settleRound()
            workerJob = null
        }
    }
}
