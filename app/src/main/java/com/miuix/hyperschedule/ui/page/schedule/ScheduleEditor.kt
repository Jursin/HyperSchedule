package com.miuix.hyperschedule.ui.page.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.ui.component.DAY_NAMES
import com.miuix.hyperschedule.ui.component.DragVisual
import com.miuix.hyperschedule.ui.component.ScheduleLayout
import java.util.UUID
import kotlin.math.floor

/** 「+」格 */
internal data class PendingCell(val day: Int, val section: Int)

/** 周 pager 前后各留的页数 */
internal const val EdgePages = 1000

/** 撤销历史里的一行 */
internal data class CourseRow(val course: Course, val weeks: List<Int>)

/** 一步要落库的内容 */
internal data class GridStep(
    val rows: List<CourseRow> = emptyList(),
    val deleteIds: List<String> = emptyList(),
)

/** 一次网格改动 */
internal data class GridChange(val forward: GridStep, val backward: GridStep)

/** 拖动会话 */
internal data class DragSession(
    // 起拖时所在的周
    val week: Int,
    val course: Course,
    val weeks: List<Int>,
    val grid: Rect,
    // 手指相对卡片左上角的偏移
    val grab: Offset,
    // 按下时落在第几节
    val grabSection: Int,
    // 0 表示落点无效
    val targetDay: Int = 0,
    val targetStart: Int = 0,
    // 已松手
    val released: Boolean = false,
)

/** 落点冲突，等用户选覆盖还是交换 */
internal data class PendingConflict(
    val week: Int,
    val session: DragSession,
    val moved: Course,
    val conflicts: List<CourseWithWeeks>,
)

private fun Course.sectionRange(): IntRange {
    val start = startSection ?: return IntRange.EMPTY
    return start..(endSection ?: start).coerceAtLeast(start)
}

private fun IntRange.overlaps(other: IntRange): Boolean =
    !isEmpty() && !other.isEmpty() && first <= other.last && other.first <= last

/** 网格内的拖动与撤销 */
internal class ScheduleEditor(
    private val layout: ScheduleLayout,
    private val density: Density,
    private val haptic: HapticFeedback,
    // 目标列是不是补课列
    private val isRemapped: (week: Int, day: Int) -> Boolean,
    private val onSave: (deletedIds: List<String>, rows: List<Pair<Course, List<Int>>>) -> Unit,
) {
    var drag by mutableStateOf<DragSession?>(null)
        private set
    var conflict by mutableStateOf<PendingConflict?>(null)
        private set
    var undoStack by mutableStateOf<List<GridChange>>(emptyList())
        private set
    var redoStack by mutableStateOf<List<GridChange>>(emptyList())
        private set

    /** 松手后等翻页停稳才落库 */
    private var releasedDrop: DragSession? = null

    /** 当前页日期列矩形（root） */
    private var liveGrid: Rect? = null

    /** 手指位置 */
    val fingerState = mutableStateOf(Offset.Zero)

    /** 被浮层顶替的原卡 */
    val draggingSlot: Pair<String, Int>? get() = drag?.let { it.course.id to it.week }

    val dragReleased: Boolean get() = drag?.released == true

    val hasHistory: Boolean get() = undoStack.isNotEmpty() || redoStack.isNotEmpty()

    /** 页上报日期列矩形 */
    fun updateLiveGrid(rect: Rect) {
        if (rect.height > 0f) liveGrid = rect
    }

    /** 切课表后作废旧历史 */
    fun resetHistory() {
        undoStack = emptyList()
        redoStack = emptyList()
    }

    /** 落点用的网格 */
    private fun DragSession.gridNow(): Rect = liveGrid ?: grid

    fun startDrag(
        week: Int,
        course: Course,
        rootPosition: Offset,
        allCourses: List<CourseWithWeeks>,
    ): Boolean {
        val grid = liveGrid?.takeIf { it.width > 0f && it.height > 0f } ?: return false
        val item = allCourses.firstOrNull { it.course.id == course.id } ?: return false
        val range = course.sectionRange()
        if (range.isEmpty()) return false
        val columnWidth = grid.width / DAY_NAMES.size
        val cardOrigin =
            Offset(
                grid.left + (course.day - 1) * columnWidth,
                grid.top + with(density) { layout.topOf(range.first).toPx() },
            )
        val pressedSection = with(density) { layout.sectionAt((rootPosition.y - grid.top).toDp()) }
        fingerState.value = rootPosition
        drag =
            DragSession(
                week = week,
                course = course,
                weeks = item.weeks.map { it.weekNumber },
                grid = grid,
                grab = rootPosition - cardOrigin,
                grabSection = pressedSection.coerceIn(range.first, range.last),
                // 先按原位显示落点
                targetDay = course.day,
                targetStart = range.first,
            )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    /** 按手指位置重算落点 */
    private fun DragSession.resolved(finger: Offset): DragSession {
        val grid = gridNow()
        val range = course.sectionRange()
        val columnWidth = grid.width / DAY_NAMES.size
        val day = floor((finger.x - grid.left) / columnWidth).toInt() + 1
        val section = with(density) { layout.sectionAt((finger.y - grid.top).toDp()) }
        // 按抓取那一节的差量整体平移
        val delta = section - grabSection
        val start = range.first + delta
        val end = range.last + delta
        val valid = start >= 1 && end <= layout.sectionCount
        return copy(
            // 横向超出网格贴最近一列，边缘翻周才有落点
            targetDay = if (valid) day.coerceIn(1, DAY_NAMES.size) else 0,
            targetStart = if (valid) start else 0,
        )
    }

    fun updateDrag(rootPosition: Offset) {
        val session = drag ?: return
        fingerState.value = rootPosition
        val next = session.resolved(rootPosition)
        // 只在跨格时写 state
        if (next.targetDay != session.targetDay || next.targetStart != session.targetStart) drag = next
    }

    /** 浮层卡片中心的 root 横坐标 */
    fun dragCardCenterX(finger: Offset): Float? {
        val session = drag ?: return null
        return finger.x - session.grab.x + session.grid.width / DAY_NAMES.size / 2f
    }

    fun releaseDrag() {
        val session = drag?.takeIf { !it.released } ?: return
        // 灰卡留到数据回来
        drag = session.copy(released = true)
        releasedDrop = drag
    }

    /** 停稳后按最终页写库 */
    fun commitDrop(allCourses: List<CourseWithWeeks>, validWeeks: IntRange, page: Int) {
        val released = releasedDrop ?: return
        releasedDrop = null
        // 用副本算，不碰可能已经开始的下一轮拖动
        val session = released.resolved(fingerState.value)
        val week = weekOfPage(page)
        // 只收拾这一份灰卡
        fun clearGhost() {
            if (drag === released) drag = null
        }
        // 学期外或补课列当作取消
        if (session.targetDay == 0 || week !in validWeeks || isRemapped(week, session.targetDay)) {
            clearGhost()
            return
        }
        val old = session.course
        val range = old.sectionRange()
        val delta = session.targetStart - range.first
        val newRange = (range.first + delta)..(range.last + delta)
        val moved = old.copy(day = session.targetDay, startSection = newRange.first, endSection = newRange.last)
        if (moved.day == old.day && moved.startSection == old.startSection && week == session.week) {
            clearGhost()
            return
        }
        // 目标格已有同一门课的其它周
        val mergeTarget =
            allCourses.firstOrNull { item ->
                item.course.id != old.id &&
                    item.course.name == old.name &&
                    item.course.teacher == old.teacher &&
                    item.course.position == old.position &&
                    item.course.day == moved.day &&
                    !item.course.isCustomTime &&
                    item.course.sectionRange() == newRange
            }
        if (mergeTarget != null) {
            writeMove(session, week, moved, mergeTarget, emptyList())
            return
        }
        // 只在目标这一周里判冲突
        val conflicts =
            allCourses.filter { item ->
                item.course.id != old.id &&
                    item.course.day == moved.day &&
                    !item.course.isCustomTime &&
                    item.weeks.any { it.weekNumber == week } &&
                    item.course.sectionRange().overlaps(newRange)
            }
        if (conflicts.isEmpty()) {
            writeMove(session, week, moved, null, emptyList())
        } else {
            // 弹窗期间网格保持原样
            clearGhost()
            conflict = PendingConflict(week, session, moved, conflicts)
        }
    }

    /** 数据回流后收起灰卡 */
    fun settleDrag(allCourses: List<CourseWithWeeks>) {
        val session = drag?.takeIf { it.released } ?: return
        val old = session.course
        val item = allCourses.firstOrNull { it.course.id == old.id }
        val movedAway =
            item == null ||
                item.course.day != old.day ||
                item.course.startSection != old.startSection ||
                item.weeks.none { it.weekNumber == session.week }
        if (movedAway) drag = null
    }

    /** 灰卡兜底 */
    fun clearReleasedDrag() {
        if (drag?.released == true) drag = null
    }

    /** 落点浮层 */
    fun dragVisual(layerOrigin: Offset): DragVisual? {
        val session = drag ?: return null
        if (session.released) return null
        val grid = session.gridNow()
        val columnWidth = grid.width / DAY_NAMES.size
        val range = session.course.sectionRange()
        val previewOrigin =
            if (session.targetDay > 0) {
                Offset(
                    grid.left + (session.targetDay - 1) * columnWidth,
                    grid.top + with(density) { layout.topOf(session.targetStart).toPx() },
                ) - layerOrigin
            } else {
                null
            }
        return DragVisual(
            course = session.course,
            previewOrigin = previewOrigin,
            width = with(density) { columnWidth.toDp() },
            height = layout.heightOf(range.first, range.last),
        )
    }

    fun cardOrigin(layerOrigin: Offset): Offset {
        val session = drag ?: return Offset.Zero
        return fingerState.value - session.grab - layerOrigin
    }

    fun undoMove() {
        val change = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = redoStack + change
        applyChange(change, forward = false)
    }

    fun redoMove() {
        val change = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = undoStack + change
        applyChange(change, forward = true)
    }

    fun swapConflict() {
        val pending = conflict ?: return
        pending.conflicts.singleOrNull()?.let { other -> commitSwap(pending, other) }
        conflict = null
    }

    fun overwriteConflict() {
        val pending = conflict ?: return
        writeMove(pending.session, pending.week, pending.moved, null, pending.conflicts)
        conflict = null
    }

    fun dismissConflict() {
        conflict = null
    }

    private fun applyChange(change: GridChange, forward: Boolean) {
        val step = if (forward) change.forward else change.backward
        onSave(step.deleteIds, step.rows.map { it.course to it.weeks })
    }

    private fun pushChange(change: GridChange) {
        undoStack = undoStack + change
        redoStack = emptyList()
        applyChange(change, forward = true)
    }

    // 只挪这一周
    private fun writeMove(
        session: DragSession,
        targetWeek: Int,
        moved: Course,
        mergeTarget: CourseWithWeeks?,
        conflicts: List<CourseWithWeeks>,
    ) {
        val old = session.course
        val oldWeeks = session.weeks
        val restWeeks = oldWeeks.filter { it != session.week }
        val split = restWeeks.isNotEmpty()
        val movedId = if (split) UUID.randomUUID().toString() else old.id

        val forwardRows = mutableListOf<CourseRow>()
        val forwardDeletes = mutableListOf<String>()
        val backwardRows = mutableListOf<CourseRow>()
        // 原位置的其它周
        if (split) forwardRows += CourseRow(old, restWeeks)
        backwardRows += CourseRow(old, oldWeeks)

        if (mergeTarget != null) {
            val targetWeeks = mergeTarget.weeks.map { it.weekNumber }
            forwardRows += CourseRow(mergeTarget.course, (targetWeeks + targetWeek).distinct())
            if (!split) forwardDeletes += old.id
            backwardRows += CourseRow(mergeTarget.course, targetWeeks)
        } else {
            forwardRows += CourseRow(moved.copy(id = movedId), listOf(targetWeek))
            conflicts.forEach { item ->
                val weeks = item.weeks.map { it.weekNumber }
                val left = weeks.filter { it != targetWeek }
                if (left.isEmpty()) forwardDeletes += item.course.id else forwardRows += CourseRow(item.course, left)
                backwardRows += CourseRow(item.course, weeks)
            }
        }
        // 拆出去的新行撤回时要删掉
        val backwardDeletes = if (split && mergeTarget == null) listOf(movedId) else emptyList()
        pushChange(
            GridChange(
                forward = GridStep(forwardRows, forwardDeletes),
                backward = GridStep(backwardRows, backwardDeletes),
            ),
        )
    }

    private fun commitSwap(pending: PendingConflict, other: CourseWithWeeks) {
        val session = pending.session
        val targetWeek = pending.week
        val moved = pending.moved
        val old = session.course
        val oldWeeks = session.weeks
        val restWeeks = oldWeeks.filter { it != session.week }
        val movedId = if (restWeeks.isEmpty()) old.id else UUID.randomUUID().toString()

        val otherOld = other.course
        val otherWeeks = other.weeks.map { it.weekNumber }
        val otherRest = otherWeeks.filter { it != targetWeek }
        val otherId = if (otherRest.isEmpty()) otherOld.id else UUID.randomUUID().toString()
        val otherRange = otherOld.sectionRange()
        val otherMoved =
            otherOld.copy(
                id = otherId,
                day = old.day,
                startSection = old.startSection,
                endSection = old.startSection?.let { it + (otherRange.last - otherRange.first) },
            )

        val forwardRows = mutableListOf<CourseRow>()
        if (restWeeks.isNotEmpty()) forwardRows += CourseRow(old, restWeeks)
        forwardRows += CourseRow(moved.copy(id = movedId), listOf(targetWeek))
        if (otherRest.isNotEmpty()) forwardRows += CourseRow(otherOld, otherRest)
        forwardRows += CourseRow(otherMoved, listOf(session.week))

        val backwardDeletes =
            buildList {
                if (restWeeks.isNotEmpty()) add(movedId)
                if (otherRest.isNotEmpty()) add(otherId)
            }
        pushChange(
            GridChange(
                forward = GridStep(forwardRows),
                backward = GridStep(listOf(CourseRow(old, oldWeeks), CourseRow(otherOld, otherWeeks)), backwardDeletes),
            ),
        )
    }

    private fun weekOfPage(page: Int): Int = page - EdgePages + 1
}

/** 拖动与撤销状态 */
@Composable
internal fun rememberScheduleEditor(
    layout: ScheduleLayout,
    isRemapped: (week: Int, day: Int) -> Boolean,
    onSave: (deletedIds: List<String>, rows: List<Pair<Course, List<Int>>>) -> Unit,
): ScheduleEditor {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val saveState = rememberUpdatedState(onSave)
    val remapState = rememberUpdatedState(isRemapped)
    return remember(layout, density, haptic) {
        ScheduleEditor(
            layout = layout,
            density = density,
            haptic = haptic,
            isRemapped = { week, day -> remapState.value(week, day) },
            onSave = { ids, rows -> saveState.value(ids, rows) },
        )
    }
}
