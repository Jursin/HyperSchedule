package com.miuix.hyperschedule.ui.page.schedule

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** 贴边热区宽度 */
private val EdgeHotZone = 32.dp

/** 贴边停留多久翻一页 */
private const val EdgeDwellMs = 500L

/** 松手后等翻页停稳的上限 */
private const val PagerSettleMs = 500L

/** 页面级触控状态 */
internal class ScheduleTouchState {
    /** 正按住课程卡拖拽的指针 */
    var pointerId: PointerId? = null
    /** 按下时的 root 坐标 */
    var downRoot: Offset? = null
    /** 已越过容差 */
    var started = false

    fun begin(pointerId: PointerId, downRoot: Offset) {
        this.pointerId = pointerId
        this.downRoot = downRoot
        started = false
    }

    fun end() {
        pointerId = null
        downRoot = null
        started = false
    }
}

/** 拖到屏幕左右边缘时自动翻周 */
internal class EdgeAutoPager(
    private val scope: CoroutineScope,
    private val haptic: HapticFeedback,
    private val zonePx: Float,
) {
    private var job: Job? = null
    /** 循环当前方向 */
    private var direction = 0
    /** 最近一次算出的方向 */
    private var latest = 0
    /** 翻页动画在跑 */
    private var animating = false
    /** Job 代号 */
    private var generation = 0

    fun update(centerX: Float, bounds: Rect, pages: IntRange, pagerState: PagerState) {
        // 热区不超过屏宽的 1/10
        val zone = zonePx.coerceAtMost(bounds.width * 0.1f)
        val dir =
            when {
                zone <= 0f -> 0
                centerX <= bounds.left + zone -> -1
                centerX >= bounds.right - zone -> 1
                else -> 0
            }
        // 学期首尾没有可落的周
        val exhausted =
            (dir < 0 && pagerState.currentPage <= pages.first) ||
                (dir > 0 && pagerState.currentPage >= pages.last)
        latest = if (exhausted) 0 else dir
        if (latest == 0) {
            // 动画中不取消 pager
            if (!animating) {
                direction = 0
                job?.cancel()
                job = null
            }
            return
        }
        // 已在同方向的循环里
        if (direction == latest && job?.isActive == true) return
        if (animating) return
        val next = latest
        val gen = ++generation
        job?.cancel()
        direction = next
        job =
            scope.launch {
                try {
                    while (true) {
                        // 每翻一页都先等满停留
                        delay(EdgeDwellMs.milliseconds)
                        if (latest != next) break
                        val target = pagerState.currentPage + next
                        if (target !in pages) break
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        animating = true
                        try {
                            pagerState.animateScrollToPage(target)
                        } catch (e: CancellationException) {
                            // 被第二根手指抢占就等下一轮
                            if (!isActive) throw e
                        } finally {
                            animating = false
                        }
                    }
                } finally {
                    if (generation == gen) {
                        direction = 0
                        job = null
                    }
                }
            }
    }

    fun stop() {
        latest = 0
        if (animating) return
        direction = 0
        job?.cancel()
        job = null
    }
}

@Composable
internal fun rememberEdgeAutoPager(): EdgeAutoPager {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val zone = with(LocalDensity.current) { EdgeHotZone.toPx() }
    return remember(scope, haptic, zone) { EdgeAutoPager(scope, haptic, zone) }
}

/** 跨周拖课的页面级 owner */
@Composable
internal fun Modifier.scheduleDragOwner(
    editor: ScheduleEditor,
    touchState: ScheduleTouchState,
    pagerState: PagerState,
    settleSlot: PagerSettleSlot,
    allCourses: () -> List<CourseWithWeeks>,
    validWeeks: () -> IntRange,
    origin: () -> Offset,
    onMove: (cardCenterX: Float) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    // 只在 editor 或 touchState 变化时重启
    val latestCourses = rememberUpdatedState(allCourses)
    val latestWeeks = rememberUpdatedState(validWeeks)
    val latestOrigin = rememberUpdatedState(origin)
    val latestMove = rememberUpdatedState(onMove)
    val latestEnd = rememberUpdatedState(onEnd)
    return this.pointerInput(editor, touchState) {
        val slop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            var tracked: PointerId? = null
            var downRoot = Offset.Zero
            fun finish(root: Offset?) {
                if (touchState.started && root != null) {
                    editor.updateDrag(root)
                    latestMove.value(editor.dragCardCenterX(root) ?: root.x)
                }
                // 先停贴边翻周
                latestEnd.value()
                editor.releaseDrag()
                touchState.end()
                scope.launch {
                    // 等横滑落页再算落点
                    settleSlot.job?.join()
                    withTimeoutOrNull(PagerSettleMs.milliseconds) {
                        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                    }
                    val settled = (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt()
                    editor.commitDrop(latestCourses.value(), latestWeeks.value(), settled)
                }
            }
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val dragId = touchState.pointerId
                if (dragId == null) {
                    tracked = null
                    continue
                }
                val change = event.changes.firstOrNull { it.id == dragId }
                if (change == null) {
                    // 指针消失
                    if (tracked == dragId) {
                        finish(null)
                        tracked = null
                    }
                    continue
                }
                if (tracked != dragId) {
                    tracked = dragId
                    downRoot = touchState.downRoot ?: (change.position + latestOrigin.value())
                }
                val rootNow = change.position + latestOrigin.value()
                if (!change.pressed) {
                    finish(rootNow)
                    tracked = null
                    change.consume()
                    continue
                }
                if (!touchState.started && (rootNow - downRoot).getDistance() > slop) {
                    touchState.started = true
                }
                if (touchState.started) {
                    editor.updateDrag(rootNow)
                    latestMove.value(editor.dragCardCenterX(rootNow) ?: rootNow.x)
                }
                change.consume()
            }
        }
    }
}
