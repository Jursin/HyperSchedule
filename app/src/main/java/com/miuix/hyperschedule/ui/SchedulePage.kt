package com.miuix.hyperschedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.schedule.DEFAULT_SECTION_TIMES
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.currentSectionAt
import com.miuix.hyperschedule.schedule.currentWeekOf
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.ConflictDialog
import com.miuix.hyperschedule.ui.component.CourseDetailItem
import com.miuix.hyperschedule.ui.component.CourseDetailSheet
import com.miuix.hyperschedule.ui.component.DAY_NAMES
import com.miuix.hyperschedule.ui.component.DayGridColumn
import com.miuix.hyperschedule.ui.component.DragVisual
import com.miuix.hyperschedule.ui.component.LocalLastTapDown
import com.miuix.hyperschedule.ui.component.NumberPickerDialog
import com.miuix.hyperschedule.ui.component.ProvideBackToNow
import com.miuix.hyperschedule.ui.component.ScheduleDragLayer
import com.miuix.hyperschedule.ui.component.SectionHeight
import com.miuix.hyperschedule.ui.component.SectionTimesColumn
import com.miuix.hyperschedule.ui.component.WeekdayRow
import com.miuix.hyperschedule.ui.component.isSameSlotAs
import com.miuix.hyperschedule.ui.component.resolveWeekCourses
import com.miuix.hyperschedule.ui.navigation.LocalMainTabNavigating
import com.miuix.hyperschedule.ui.navigation.ProvideOverlay
import com.miuix.hyperschedule.ui.navigation.LocalPageIsCurrent
import com.miuix.hyperschedule.ui.theme.captureForBlur
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.floor
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 「+」格：同一时刻只有一个，记录它属于哪一天哪一节 */
private data class PendingCell(val day: Int, val section: Int)

/** 周 pager 前后各留的页数；取大值让两边都能一直滑 */
private const val EdgePages = 1000

/** 学期总周数上限（与设置页范围一致）；pager 页数按它固定，改设置时无需重建 */
private const val MaxWeeks = 60

/** 撤销历史里的一行：课程位置 + 它的周次 */
private data class CourseRow(val course: Course, val weeks: List<Int>)

/** 一步要落库的内容：写入的行 + 删掉的课程 id */
private data class GridStep(
    val rows: List<CourseRow> = emptyList(),
    val deleteIds: List<String> = emptyList(),
)

/** 一次网格改动：forward = 落点，backward = 撤回 */
private data class GridChange(val forward: GridStep, val backward: GridStep)

/** 拖动会话：记下按下的位置与所属网格，松手时才能算出落点 */
private data class DragSession(
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
private data class PendingConflict(
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

@Composable
fun SchedulePage(
    table: CourseTableData?,
    allCourses: List<CourseWithWeeks>,
    showNonCurrentWeek: Boolean = true,
    appBlur: Boolean = false,
    onEditCourse: (Course) -> Unit = {},
    onAddCourse: (day: Int, startSection: Int, endSection: Int) -> Unit = { _, _, _ -> },
    onManageCourses: () -> Unit = {},
    // 拖动改位置用：deletedIds 为覆盖掉的课程
    onSaveCourses: (deletedIds: List<String>, rows: List<Pair<Course, List<Int>>>) -> Unit = { _, _ -> },
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val scope = rememberCoroutineScope()

    // 每分钟走一格：跨零点换天、当前节次高亮都要自己滚
    val now by
        produceState(initialValue = LocalDateTime.now()) {
            while (true) {
                delay(1.minutes)
                value = LocalDateTime.now()
            }
        }
    val today = now.toLocalDate()

    // 导入的作息可能不是 8 节，节次列与网格高度都跟着它走
    val sectionTimes = table?.sectionTimes ?: DEFAULT_SECTION_TIMES
    val config = table?.config

    // 空串 = 未设置
    val startDate = remember(config?.semesterStartDate) { parseDateOrNull(config?.semesterStartDate.orEmpty()) }
    val totalWeeks = (config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS).coerceAtLeast(1)
    val currentWeek = remember(startDate, today) { startDate?.let { currentWeekOf(it, today) } }

    fun weekOfPage(page: Int): Int = page - EdgePages + 1

    fun pageOfWeek(week: Int): Int = EdgePages + week - 1

    val todayPage = currentWeek?.let { pageOfWeek(it) }
    val pageCount = EdgePages * 2 + MaxWeeks
    val lastPage = pageCount - 1
    // 页数固定，学期设置变化时只影响内容，不动 pager 本身
    val pagerState =
        remember {
            PagerState(
                currentPage = (todayPage ?: pageOfWeek(1)).coerceIn(0, lastPage),
                pageCount = { pageCount },
            )
        }
    val viewingPage by remember { derivedStateOf { pagerState.currentPage } }

    var pendingCell by remember { mutableStateOf<PendingCell?>(null) }
    var detailAnchor by remember { mutableStateOf<Course?>(null) }
    var detailVisible by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpWeek by remember { mutableIntStateOf(1) }

    // 换页后原来的「+」不再属于当前视图
    LaunchedEffect(viewingPage) { pendingCell = null }

    // 点击网格外任何一处都收起「+」；底栏在页面之外，靠 MainScaffold 记的全局按下位置覆盖
    // 各页的网格范围分开记：左右邻页是偏移着布局的，共用一个变量会把范围带偏
    val dayColumnsBounds = remember { mutableStateMapOf<Int, Rect>() }
    val lastTapDown = LocalLastTapDown.current
    LaunchedEffect(Unit) {
        snapshotFlow { lastTapDown.value }.collect { position ->
            val bounds = dayColumnsBounds[pagerState.currentPage]
            if (position != null && (bounds == null || !bounds.contains(position))) {
                pendingCell = null
            }
        }
    }

    val pageIsCurrent = LocalPageIsCurrent.current
    val tabNavigating = LocalMainTabNavigating.current
    // 离开过就记一笔，切回那一刻或导航结束时复位到本周
    var pendingReset by remember { mutableStateOf(false) }
    LaunchedEffect(pageIsCurrent, tabNavigating, todayPage) {
        if (!pageIsCurrent) pendingReset = true
        // 导航结束（已在屏外）或刚切回（此刻 pager 还没动，本页仍在屏外）时复位
        if (pendingReset && (pageIsCurrent || !tabNavigating)) {
            pagerState.scrollToPage((todayPage ?: pageOfWeek(1)).coerceIn(0, lastPage))
            pendingReset = false
        }
    }

    val pageWeek = weekOfPage(viewingPage)
    val title =
        when {
            startDate == null -> "未设置开学日期"
            pageWeek < 1 -> "未开学"
            pageWeek > totalWeeks -> "学期已结束"
            else -> "第${pageWeek}周"
        }
    // 跳转按钮的图标与标题文字同尺寸
    val titleFontSize = MiuixTheme.textStyles.title3.fontSize
    val titleIconSize = with(LocalDensity.current) { titleFontSize.toDp() }

    // 查看的周不是当前周时显示「今」按钮，待复位期间不显示
    val showBackToNow = remember(todayPage) {
        derivedStateOf { !pendingReset && todayPage != null && todayPage != viewingPage }
    }

    // 拖动与撤销：改位置只在松手时落库，历史里只记网格内的移动
    val density = LocalDensity.current
    val sectionPx = with(density) { SectionHeight.toPx() }
    val haptic = LocalHapticFeedback.current
    var drag by remember { mutableStateOf<DragSession?>(null) }
    var dragLayerOrigin by remember { mutableStateOf(Offset.Zero) }
    var conflict by remember { mutableStateOf<PendingConflict?>(null) }
    var undoStack by remember { mutableStateOf<List<GridChange>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<GridChange>>(emptyList()) }
    var historyTick by remember { mutableIntStateOf(0) }
    var historyVisible by remember { mutableStateOf(false) }

    // 每次移动/撤销/重做都重置 3 秒；栈空时直接不显示
    LaunchedEffect(historyTick, undoStack.size, redoStack.size) {
        if (undoStack.isEmpty() && redoStack.isEmpty()) {
            historyVisible = false
            return@LaunchedEffect
        }
        historyVisible = true
        delay(3000)
        historyVisible = false
    }

    fun applyChange(change: GridChange, forward: Boolean) {
        val step = if (forward) change.forward else change.backward
        onSaveCourses(step.deleteIds, step.rows.map { it.course to it.weeks })
    }

    fun pushChange(change: GridChange) {
        undoStack = undoStack + change
        redoStack = emptyList()
        historyTick++
        applyChange(change, forward = true)
    }

    fun undoMove() {
        val change = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = redoStack + change
        historyTick++
        applyChange(change, forward = false)
    }

    fun redoMove() {
        val change = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = undoStack + change
        historyTick++
        applyChange(change, forward = true)
    }

    fun startDrag(page: Int, course: Course, rootPosition: Offset) {
        val grid = dayColumnsBounds[page] ?: return
        val item = allCourses.firstOrNull { it.course.id == course.id } ?: return
        val range = course.sectionRange()
        if (range.isEmpty()) return
        val columnWidth = grid.width / DAY_NAMES.size
        val cardOrigin =
            Offset(
                grid.left + (course.day - 1) * columnWidth,
                grid.top + (range.first - 1) * sectionPx,
            )
        val pressedSection = floor((rootPosition.y - grid.top) / sectionPx).toInt() + 1
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
        val section = floor((rootPosition.y - session.grid.top) / sectionPx).toInt() + 1
        // 按抓取那一节的差量整体平移
        val delta = section - session.grabSection
        val start = range.first + delta
        val end = range.last + delta
        val valid = day in 1..DAY_NAMES.size && start >= 1 && end <= sectionTimes.size
        drag =
            session.copy(
                finger = rootPosition,
                targetDay = if (valid) day else 0,
                targetStart = if (valid) start else 0,
            )
    }

    // 只挪当前这一周：拆出本周（原行留其余周），落到目标格；并入同门课、覆盖掉冲突课的本周
    fun commitDrop(
        session: DragSession,
        moved: Course,
        week: Int,
        mergeTarget: CourseWithWeeks?,
        conflicts: List<CourseWithWeeks>,
    ) {
        val old = session.course
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
    fun commitSwap(session: DragSession, moved: Course, week: Int, other: CourseWithWeeks) {
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

    fun finishDrag() {
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
            commitDrop(session, moved, week, mergeTarget, emptyList())
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
            commitDrop(session, moved, week, null, emptyList())
        } else {
            // 什么都没改，弹窗期间网格保持原样
            drag = null
            conflict = PendingConflict(session, moved, conflicts)
        }
    }

    // 数据回流后收起灰卡：确认这一周确实不在原位了才放手，避免半路的一次无关回调把灰卡提前撤掉
    val dragReleased by remember { derivedStateOf { drag?.released == true } }
    LaunchedEffect(allCourses) {
        val session = drag?.takeIf { it.released } ?: return@LaunchedEffect
        val old = session.course
        val item = allCourses.firstOrNull { it.course.id == old.id }
        val movedAway =
            item == null ||
                item.course.day != old.day ||
                item.course.startSection != old.startSection ||
                item.weeks.none { it.weekNumber == weekOfPage(session.page) }
        if (movedAway) drag = null
    }
    LaunchedEffect(dragReleased) {
        if (dragReleased) {
            delay(1000)
            if (drag?.released == true) drag = null
        }
    }

    // 落点浮层：抬起的卡片跟手，灰色预览略滞后地贴到目标格
    fun dragVisual(): DragVisual? {
        val session = drag ?: return null
        if (session.released) return null
        val columnWidth = session.grid.width / DAY_NAMES.size
        val range = session.course.sectionRange()
        val previewOrigin =
            if (session.targetDay > 0) {
                Offset(
                    session.grid.left + (session.targetDay - 1) * columnWidth,
                    session.grid.top + (session.targetStart - 1) * sectionPx,
                ) - dragLayerOrigin
            } else {
                null
            }
        return DragVisual(
            course = session.course,
            cardOrigin = session.finger - session.grab - dragLayerOrigin,
            previewOrigin = previewOrigin,
            width = with(density) { columnWidth.toDp() },
            height = SectionHeight * (range.last - range.first + 1),
        )
    }

    // 只有「谁在拖」变化时才让网格重组，跟手位移只影响浮层
    val draggingCourse by remember { derivedStateOf { drag?.course } }

    // 采集放在页面最外层（周 pager 之外）：内层 pager 会打断「内容采集 → 顶栏取样」的对应关系
    Scaffold(
        topBar = {
            Box {
                BlurredTopAppBar(
                    title = "",
                    blurBackdrop = blurBackdrop,
                    scrollBehavior = scrollBehavior,
                    leading = {
                        IconButton(onClick = {}) {
                            Icon(MiuixIcons.ConvertFile, contentDescription = "课表切换")
                        }
                    },
                    actions = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onManageCourses) {
                                Icon(MiuixIcons.Backup, contentDescription = "课程管理")
                            }
                            IconButton(onClick = {}) {
                                Icon(MiuixIcons.Background, contentDescription = "课表外观")
                            }
                        }
                    },
                    bottomContent = {
                        WeekdayRow(
                            startDate = startDate,
                            week = pageWeek,
                            currentWeek = currentWeek,
                            today = today,
                        )
                    },
                    pinnedTitle = true,
                )
                // 周次标题固定在栏中间，跳转按钮紧跟其后（约一个空格）
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                            .height(TopAppBarDefaults.CollapsedHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    // 未设置开学日期时没有可跳转的周，不显示跳转按钮
                    if (startDate != null) {
                        IconButton(
                            onClick = {
                                jumpWeek = pageWeek.coerceIn(1, totalWeeks)
                                showJumpDialog = true
                            },
                            modifier = Modifier.padding(start = 4.dp),
                            minWidth = 32.dp,
                            minHeight = 32.dp,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Basic.ArrowUpDown,
                                contentDescription = "跳转周数",
                                modifier = Modifier.size(titleIconSize),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        // 拖动浮层与撤销按钮都挂在这一层：坐标统一按它换算
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords -> dragLayerOrigin = coords.boundsInRoot().topLeft },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 底部导航切页动画期间禁用手势，避免和过渡动画互抢
                userScrollEnabled = !LocalMainTabNavigating.current,
                beyondViewportPageCount = 1,
            ) { page ->
                val week = weekOfPage(page)
                // 未开学 / 学期已结束：网格照常显示，只是不放任何课程
                val inSemester = week in 1..totalWeeks
                val currentSection =
                    if (week == currentWeek) currentSectionAt(sectionTimes, now.toLocalTime()) else -1
                val pageCourses =
                    remember(allCourses, week, inSemester, showNonCurrentWeek) {
                        if (inSemester) {
                            resolveWeekCourses(allCourses, week, showNonCurrentWeek)
                        } else {
                            emptyList()
                        }
                    }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            // 只让当前页往 backdrop 里录：一个 backdrop 只有一个采集点，多页同录会互相覆盖
                            // （把顶栏圈进来会让模糊面采样到自己 → 原生崩溃）
                            .captureForBlur(blurBackdrop.takeIf { page == pagerState.currentPage })
                            .overScrollVertical()
                            .scrollEndHaptic()
                            // 每页各自的滚动位置
                            .verticalScroll(rememberScrollState())
                            // 顶栏高度作为滚动内容的内边距：网格才能滚到顶栏下方，顶栏有东西可模糊
                            .padding(top = paddingValues.calculateTopPadding()),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SectionTimesColumn(currentSection = currentSection, sectionTimes = sectionTimes)
                        // 「网格」= 7 个日期列这块矩形；页面按它判断点击是否落在网格外
                        Row(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coords -> dayColumnsBounds[page] = coords.boundsInRoot() },
                        ) {
                            for (day in 1..DAY_NAMES.size) {
                                DayGridColumn(
                                    modifier = Modifier.weight(1f),
                                    courses = pageCourses.filter { it.course.day == day },
                                    sectionCount = sectionTimes.size,
                                    sectionTimes = sectionTimes,
                                    pendingSection = pendingCell?.takeIf { it.day == day }?.section ?: -1,
                                    // 没设开学日期就没有可添加的日期，不可点
                                    onCellTap =
                                        if (startDate != null) {
                                            { section -> pendingCell = PendingCell(day, section) }
                                        } else {
                                            null
                                        },
                                    // 点卡片只带出一门课，抽屉再按「同一格」把该格所有课程取出来
                                    onCourseClick = { clicked ->
                                        detailAnchor = clicked
                                        detailVisible = true
                                    },
                                    // 再点一次「+」直接进新建课程页
                                    onPendingTap = { section -> onAddCourse(day, section, section) },
                                    // 拖动中的卡片原地按「本周不上」显示，由浮层代替它跟手
                                    draggingCourse = draggingCourse,
                                    onDragStart = { course, position -> startDrag(page, course, position) },
                                    onDrag = { position -> updateDrag(position) },
                                    onDragEnd = { finishDrag() },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            ScheduleDragLayer { dragVisual() }

            // 不做淡入/缩放：给 FAB 加图层会把阴影按图层边界裁成直边，看起来先方后圆
            if (historyVisible) {
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (undoStack.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { undoMove() },
                            containerColor = MiuixTheme.colorScheme.secondaryVariant,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Undo,
                                contentDescription = "撤销",
                                tint = MiuixTheme.colorScheme.onSecondaryVariant,
                            )
                        }
                    }
                    if (redoStack.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { redoMove() },
                            containerColor = MiuixTheme.colorScheme.secondaryVariant,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Redo,
                                contentDescription = "重做",
                                tint = MiuixTheme.colorScheme.onSecondaryVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    detailAnchor?.let { anchor ->
        // 每次都从库里取：编辑页改完返回，抽屉内容立刻跟着更新
        val items =
            remember(allCourses, anchor, pageWeek) {
                allCourses
                    .filter { it.course.isSameSlotAs(anchor) }
                    // 这一格的时段被改走后，退回按课名取，避免抽屉突然空掉
                    .ifEmpty { allCourses.filter { it.course.name == anchor.name } }
                    .map { item ->
                        val weeks = item.weeks.map { it.weekNumber }
                        CourseDetailItem(item.course, weeks, weeks.contains(pageWeek))
                    }
                    // 本周生效的自动翻过去
                    .sortedBy { it.weeks.minOrNull() ?: Int.MAX_VALUE }
            }
        if (items.isNotEmpty()) {
            // 挂到跨页浮层宿主上，层级高于底部导航
            ProvideOverlay {
                CourseDetailSheet(
                    items = items,
                    show = detailVisible,
                    onEdit = onEditCourse,
                    onAdd = { day, startSection, endSection -> onAddCourse(day, startSection, endSection) },
                    onDismiss = { detailVisible = false },
                    // 收起动画结束后再丢掉数据
                    onDismissFinished = { detailAnchor = null },
                )
            }
        }
    }

    ProvideBackToNow(
        visible = showBackToNow,
        onClick = {
            todayPage?.let { target ->
                scope.launch { pagerState.animateScrollToPage(target.coerceIn(0, lastPage)) }
            }
        },
    )

    // 落点已有课：覆盖或交换，返回键等同取消
    conflict?.let { pending ->
        ConflictDialog(
            appBlur = appBlur,
            canSwap = pending.conflicts.size == 1,
            onDismiss = { conflict = null },
            onSwap = {
                pending.conflicts.singleOrNull()?.let { other ->
                    commitSwap(pending.session, pending.moved, weekOfPage(pending.session.page), other)
                }
                conflict = null
            },
            onOverwrite = {
                commitDrop(pending.session, pending.moved, weekOfPage(pending.session.page), null, pending.conflicts)
                conflict = null
            },
        )
    }

    if (showJumpDialog) {
        NumberPickerDialog(
            title = "跳转周数",
            value = jumpWeek,
            onValueChange = { jumpWeek = it },
            range = 1..totalWeeks,
            label = { "第${it}周" },
            appBlur = appBlur,
            onDismiss = { showJumpDialog = false },
            onConfirm = {
                scope.launch { pagerState.animateScrollToPage(pageOfWeek(jumpWeek).coerceIn(0, lastPage)) }
                showJumpDialog = false
            },
        )
    }
}
