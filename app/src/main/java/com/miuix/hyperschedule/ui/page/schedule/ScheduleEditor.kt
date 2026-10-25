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

/** 「+」格：同一时刻只有一个，记录它属于哪一天哪一节 */
internal data class PendingCell(val day: Int, val section: Int)

/** 周 pager 前后各留的页数；取大值让两边都能一直滑 */
internal const val EdgePages = 1000

/** 撤销历史里的一行：课程位置 + 它的周次 */
internal data class CourseRow(val course: Course, val weeks: List<Int>)

/** 一步要落库的内容：写入的行 + 删掉的课程 id */
internal data class GridStep(
    val rows: List<CourseRow> = emptyList(),
    val deleteIds: List<String> = emptyList(),
)

/** 一次网格改动：forward = 落点，backward = 撤回 */
internal data class GridChange(val forward: GridStep, val backward: GridStep)

/** 拖动会话：记下按下的位置与所属网格，松手时才能算出落点 */
internal data class DragSession(
    val page: Int,
    val course: Course,
    val weeks: List<Int>,
    val grid: Rect,
    // 手指相对卡片左上角的偏移
    val grab: Offset,
    // 按下时落在第几节
    val grabSection: Int,
    val finger: Offset,
    // 0 表示落点无效
    val targetDay: Int = 0,
    val targetStart: Int = 0,
    // 已松手：浮层收起，但原格的灰卡留到数据回流，避免闪回原色
    val released: Boolean = false,
)

/** 落点冲突，等用户选覆盖还是交换 */
internal data class PendingConflict(
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

/** 网格内的拖动与撤销：改位置只在松手时落库，历史里只记网格内的移动 */
internal class ScheduleEditor(
    private val layout: ScheduleLayout,
    private val density: Density,
    private val haptic: HapticFeedback,
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

    /** 只有「谁在拖」变化时才让网格重组，跟手位移只影响浮层 */
    val draggingCourse: Course? get() = drag?.course

    val dragReleased: Boolean get() = drag?.released == true

    val hasHistory: Boolean get() = undoStack.isNotEmpty() || redoStack.isNotEmpty()

    /** 切课表后旧历史作废，避免撤销把另一张课表的课写回来 */
    fun resetHistory() {
        undoStack = emptyList()
        redoStack = emptyList()
    }

    fun startDrag(
        page: Int,
        course: Course,
        rootPosition: Offset,
        allCourses: List<CourseWithWeeks>,
        bounds: Map<Int, Rect>,
    ) {
        val grid = bounds[page] ?: return
        val item = allCourses.firstOrNull { it.course.id == course.id } ?: return
        val range = course.sectionRange()
        if (range.isEmpty()) return
        val columnWidth = grid.width / DAY_NAMES.size
        val cardOrigin =
            Offset(
                grid.left + (course.day - 1) * columnWidth,
                grid.top + with(density) { layout.topOf(range.first).toPx() },
            )
        val pressedSection = with(density) { layout.sectionAt((rootPosition.y - grid.top).toDp()) }
        drag =
            DragSession(
                page = page,
                course = course,
                weeks = item.weeks.map { it.weekNumber },
                grid = grid,
                grab = rootPosition - cardOrigin,
                grabSection = pressedSection.coerceIn(range.first, range.last),
                finger = rootPosition,
                // 先按原位显示落点预览，移动后跟着换格
                targetDay = course.day,
                targetStart = range.first,
            )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun updateDrag(rootPosition: Offset) {
        val session = drag ?: return
        val range = session.course.sectionRange()
        val columnWidth = session.grid.width / DAY_NAMES.size
        val day = floor((rootPosition.x - session.grid.left) / columnWidth).toInt() + 1
        val section = with(density) { layout.sectionAt((rootPosition.y - session.grid.top).toDp()) }
        // 按抓取那一节的差量整体平移
        val delta = section - session.grabSection
        val start = range.first + delta
        val end = range.last + delta
        val valid = day in 1..DAY_NAMES.size && start >= 1 && end <= layout.sectionCount
        drag =
            session.copy(
                finger = rootPosition,
                targetDay = if (valid) day else 0,
                targetStart = if (valid) start else 0,
            )
    }

    fun finishDrag(allCourses: List<CourseWithWeeks>) {
        val session = drag ?: return
        if (session.released) return
        // 松手先收起浮层，原格的灰卡继续留着，等库回来再交还给正常渲染
        drag = session.copy(released = true)
        if (session.targetDay == 0) {
            drag = null
            return
        }
        val old = session.course
        val range = old.sectionRange()
        val delta = session.targetStart - range.first
        val newRange = (range.first + delta)..(range.last + delta)
        val moved = old.copy(day = session.targetDay, startSection = newRange.first, endSection = newRange.last)
        if (moved.day == old.day && moved.startSection == old.startSection) {
            drag = null
            return
        }
        val week = weekOfPage(session.page)
        // 目标格已有同一门课的其它周：并回去
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
            commitDrop(moved, week, mergeTarget, emptyList(), session, old)
            return
        }
        // 只在当前这一周里判冲突：看到的就按看到的处理
        val conflicts =
            allCourses.filter { item ->
                item.course.id != old.id &&
                    item.course.day == moved.day &&
                    !item.course.isCustomTime &&
                    item.weeks.any { it.weekNumber == week } &&
                    item.course.sectionRange().overlaps(newRange)
            }
        if (conflicts.isEmpty()) {
            commitDrop(moved, week, null, emptyList(), session, old)
        } else {
            // 什么都没改，弹窗期间网格保持原样
            drag = null
            conflict = PendingConflict(session, moved, conflicts)
        }
    }

    /** 数据回流后收起灰卡：确认这一周确实不在原位才放手，避免无关回调提前撤掉灰卡 */
    fun settleDrag(allCourses: List<CourseWithWeeks>) {
        val session = drag?.takeIf { it.released } ?: return
        val old = session.course
        val item = allCourses.firstOrNull { it.course.id == old.id }
        val movedAway =
            item == null ||
                item.course.day != old.day ||
                item.course.startSection != old.startSection ||
                item.weeks.none { it.weekNumber == weekOfPage(session.page) }
        if (movedAway) drag = null
    }

    /** 灰卡兜底：数据一直没回流也要收掉 */
    fun clearReleasedDrag() {
        if (drag?.released == true) drag = null
    }

    /** 落点浮层：抬起的卡片跟手，灰色预览略滞后地贴到目标格 */
    fun dragVisual(layerOrigin: Offset): DragVisual? {
        val session = drag ?: return null
        if (session.released) return null
        val columnWidth = session.grid.width / DAY_NAMES.size
        val range = session.course.sectionRange()
        val previewOrigin =
            if (session.targetDay > 0) {
                Offset(
                    session.grid.left + (session.targetDay - 1) * columnWidth,
                    session.grid.top + with(density) { layout.topOf(session.targetStart).toPx() },
                ) - layerOrigin
            } else {
                null
            }
        return DragVisual(
            course = session.course,
            cardOrigin = session.finger - session.grab - layerOrigin,
            previewOrigin = previewOrigin,
            width = with(density) { columnWidth.toDp() },
            height = layout.heightOf(range.first, range.last),
        )
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

    /** 冲突弹窗：交换两边的本周 */
    fun swapConflict() {
        val pending = conflict ?: return
        pending.conflicts.singleOrNull()?.let { other ->
            commitSwap(pending.session, pending.moved, weekOfPage(pending.session.page), other)
        }
        conflict = null
    }

    /** 冲突弹窗：覆盖目标格的本周 */
    fun overwriteConflict() {
        val pending = conflict ?: return
        val session = pending.session
        commitDrop(pending.moved, weekOfPage(session.page), null, pending.conflicts, session, session.course)
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

    // 只挪当前这一周：拆出本周（原行留其余周），落到目标格；并入同门课、覆盖掉冲突课的本周
    private fun commitDrop(
        moved: Course,
        week: Int,
        mergeTarget: CourseWithWeeks?,
        conflicts: List<CourseWithWeeks>,
        session: DragSession,
        old: Course,
    ) {
        val oldWeeks = session.weeks
        val restWeeks = oldWeeks.filter { it != week }
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
            forwardRows += CourseRow(mergeTarget.course, targetWeeks + week)
            if (!split) forwardDeletes += old.id
            backwardRows += CourseRow(mergeTarget.course, targetWeeks)
        } else {
            forwardRows += CourseRow(moved.copy(id = movedId), listOf(week))
            conflicts.forEach { item ->
                val weeks = item.weeks.map { it.weekNumber }
                val left = weeks.filter { it != week }
                if (left.isEmpty()) forwardDeletes += item.course.id else forwardRows += CourseRow(item.course, left)
                backwardRows += CourseRow(item.course, weeks)
            }
        }
        // 拆出去的新行撤回时要删掉；并入/覆盖时没有新建行
        val backwardDeletes = if (split && mergeTarget == null) listOf(movedId) else emptyList()
        pushChange(
            GridChange(
                forward = GridStep(forwardRows, forwardDeletes),
                backward = GridStep(backwardRows, backwardDeletes),
            ),
        )
    }

    // 交换：目标课这一周挪到拖动起点，两边各自保留节数和其它周
    private fun commitSwap(session: DragSession, moved: Course, week: Int, other: CourseWithWeeks) {
        val old = session.course
        val oldWeeks = session.weeks
        val restWeeks = oldWeeks.filter { it != week }
        val movedId = if (restWeeks.isEmpty()) old.id else UUID.randomUUID().toString()

        val otherOld = other.course
        val otherWeeks = other.weeks.map { it.weekNumber }
        val otherRest = otherWeeks.filter { it != week }
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
        forwardRows += CourseRow(moved.copy(id = movedId), listOf(week))
        if (otherRest.isNotEmpty()) forwardRows += CourseRow(otherOld, otherRest)
        forwardRows += CourseRow(otherMoved, listOf(week))

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

/** 拖动 / 撤销状态；样式或作息变化会让网格重算，此时历史作废 */
@Composable
internal fun rememberScheduleEditor(
    layout: ScheduleLayout,
    onSave: (deletedIds: List<String>, rows: List<Pair<Course, List<Int>>>) -> Unit,
): ScheduleEditor {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val saveState = rememberUpdatedState(onSave)
    return remember(layout, density, haptic) {
        ScheduleEditor(layout, density, haptic) { ids, rows -> saveState.value(ids, rows) }
    }
}
