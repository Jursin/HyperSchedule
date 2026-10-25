package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.schedule.DEFAULT_SECTION_TIMES
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.MAX_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.currentWeekOf
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.theme.ScheduleStyle
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.ConflictDialog
import com.miuix.hyperschedule.ui.component.CourseDetailItem
import com.miuix.hyperschedule.ui.component.CourseDetailSheet
import com.miuix.hyperschedule.ui.component.NumberPickerDialog
import com.miuix.hyperschedule.ui.component.ProvideBackToNow
import com.miuix.hyperschedule.ui.component.ScheduleAppearanceSheet
import com.miuix.hyperschedule.ui.component.ScheduleDragLayer
import com.miuix.hyperschedule.ui.component.ScheduleLayout
import com.miuix.hyperschedule.ui.component.isSameSlotAs
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import com.miuix.hyperschedule.ui.navigation.LocalLastTapDown
import com.miuix.hyperschedule.ui.navigation.LocalMainTabNavigating
import com.miuix.hyperschedule.ui.navigation.LocalPageIsCurrent
import com.miuix.hyperschedule.ui.navigation.ProvideOverlay
import com.miuix.hyperschedule.ui.page.schedule.EdgePages
import com.miuix.hyperschedule.ui.page.schedule.HistoryFabColumn
import com.miuix.hyperschedule.ui.page.schedule.PendingCell
import com.miuix.hyperschedule.ui.page.schedule.ScheduleTopBar
import com.miuix.hyperschedule.ui.page.schedule.ScheduleWeekPage
import com.miuix.hyperschedule.ui.page.schedule.rememberScheduleEditor
import com.miuix.hyperschedule.ui.rememberHolidayPlan
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun SchedulePage(
    table: CourseTableData?,
    allCourses: List<CourseWithWeeks>,
    showNonCurrentWeek: Boolean = true,
    style: ScheduleStyle = ScheduleStyle.Default,
    onStyleChange: (ScheduleStyle) -> Unit = {},
    onStyleCommit: () -> Unit = {},
    holidayRepository: HolidayRepository? = null,
    appBlur: Boolean = false,
    onEditCourse: (Course) -> Unit = {},
    onAddCourse: (day: Int, startSection: Int, endSection: Int) -> Unit = { _, _, _ -> },
    onManageCourses: () -> Unit = {},
    onManageTables: () -> Unit = {},
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

    // 网格几何：特殊节次插空、午休/晚休分界线，页与列共用同一份
    val specialBlocks = table?.specialBlocks.orEmpty()
    val morningSplit = table?.morningAfternoonSplit.orEmpty()
    val eveningSplit = table?.afternoonEveningSplit.orEmpty()
    val layout =
        remember(sectionTimes, specialBlocks, morningSplit, eveningSplit, style.cardHeight, style.breakDividers) {
            ScheduleLayout.build(
                sectionTimes = sectionTimes,
                specialBlocks = specialBlocks,
                morningSplit = morningSplit,
                eveningSplit = eveningSplit,
                sectionHeight = style.cardHeight.dp,
                showBreakDividers = style.breakDividers,
            )
        }

    // 空串 = 未设置
    val startDate = remember(config?.semesterStartDate) { parseDateOrNull(config?.semesterStartDate.orEmpty()) }
    val totalWeeks = (config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS).coerceAtLeast(1)
    val currentWeek = remember(startDate, today) { startDate?.let { currentWeekOf(it, today) } }

    // 放假 / 调休：整学期按（周次, 星期）算成一张索引表，翻页只查表
    val holidayPlan = rememberHolidayPlan(holidayRepository, startDate, totalWeeks)

    // 拖动与撤销：改位置只在松手时落库，历史里只记网格内的移动
    val editor = rememberScheduleEditor(layout, onSaveCourses)

    fun pageOfWeek(week: Int): Int = EdgePages + week - 1

    val todayPage = currentWeek?.let { pageOfWeek(it) }
    val pageCount = EdgePages * 2 + MAX_TOTAL_WEEKS
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
    // 点开的那张卡属于哪一周（补课列可能是别的周），抽屉按它判断「本周生效」
    var detailWeek by remember { mutableIntStateOf(0) }
    var detailVisible by remember { mutableStateOf(false) }
    var appearanceVisible by remember { mutableStateOf(false) }
    // 收起动画跑完才卸载抽屉
    var appearanceMounted by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpWeek by remember { mutableIntStateOf(1) }
    var historyVisible by remember { mutableStateOf(false) }
    var dragLayerOrigin by remember { mutableStateOf(Offset.Zero) }

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

    val pageWeek = viewingPage - EdgePages + 1

    // 查看的周不是当前周时显示「今」按钮，待复位期间不显示
    val showBackToNow = remember(todayPage) {
        derivedStateOf { !pendingReset && todayPage != null && todayPage != viewingPage }
    }

    // 切课表后旧历史作废，避免撤销把另一张课表的课写回来
    LaunchedEffect(table?.tableId) { editor.resetHistory() }

    // 每次移动/撤销/重做都重置 3 秒；栈空时直接不显示
    LaunchedEffect(editor.undoStack.size, editor.redoStack.size) {
        if (!editor.hasHistory) {
            historyVisible = false
            return@LaunchedEffect
        }
        historyVisible = true
        delay(3000.milliseconds)
        historyVisible = false
    }

    // 数据回流后收起灰卡
    LaunchedEffect(allCourses) { editor.settleDrag(allCourses) }
    LaunchedEffect(editor.dragReleased) {
        if (editor.dragReleased) {
            delay(1000.milliseconds)
            editor.clearReleasedDrag()
        }
    }

    // 采集放在页面最外层（周 pager 之外）：内层 pager 会打断「内容采集 → 顶栏取样」的对应关系
    Scaffold(
        topBar = {
            ScheduleTopBar(
                startDate = startDate,
                pageWeek = pageWeek,
                totalWeeks = totalWeeks,
                currentWeek = currentWeek,
                today = today,
                gridLines = style.gridLines,
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                onManageTables = onManageTables,
                onManageCourses = onManageCourses,
                onAppearanceClick = {
                    appearanceVisible = true
                    appearanceMounted = true
                },
                onJumpClick = { week ->
                    jumpWeek = week.coerceIn(1, totalWeeks)
                    showJumpDialog = true
                },
            )
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
                ScheduleWeekPage(
                    page = page,
                    pagerState = pagerState,
                    layout = layout,
                    sectionTimes = sectionTimes,
                    style = style,
                    startDate = startDate,
                    totalWeeks = totalWeeks,
                    currentWeek = currentWeek,
                    now = now,
                    holidayPlan = holidayPlan,
                    allCourses = allCourses,
                    showNonCurrentWeek = showNonCurrentWeek,
                    blurBackdrop = blurBackdrop,
                    topPadding = paddingValues.calculateTopPadding(),
                    pendingCell = pendingCell,
                    onPendingCellChange = { pendingCell = it },
                    editor = editor,
                    dayColumnsBounds = dayColumnsBounds,
                    onCourseClick = { clicked, shownWeek ->
                        detailAnchor = clicked
                        detailWeek = shownWeek
                        detailVisible = true
                    },
                    onAddCourse = onAddCourse,
                )
            }

            ScheduleDragLayer(visualProvider = { editor.dragVisual(dragLayerOrigin) }, style = style)

            // 不做淡入/缩放：给 FAB 加图层会把阴影按图层边界裁成直边，看起来先方后圆
            if (historyVisible) {
                HistoryFabColumn(
                    canUndo = editor.undoStack.isNotEmpty(),
                    canRedo = editor.redoStack.isNotEmpty(),
                    onUndo = { editor.undoMove() },
                    onRedo = { editor.redoMove() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
                )
            }
        }
    }

    // 每次都从库里取：编辑页改完返回，抽屉内容立刻跟着更新
    val anchor = detailAnchor
    val anchorWeek = if (detailWeek > 0) detailWeek else pageWeek
    val detailItems =
        remember(allCourses, anchor, anchorWeek) {
            if (anchor == null) {
                emptyList()
            } else {
                allCourses
                    .filter { it.course.isSameSlotAs(anchor) }
                    // 这一格的时段被改走后，退回按课名取，避免抽屉突然空掉
                    .ifEmpty { allCourses.filter { it.course.name == anchor.name } }
                    .map { item ->
                        val weeks = item.weeks.map { it.weekNumber }
                        CourseDetailItem(item.course, weeks, weeks.contains(anchorWeek))
                    }
                    // 本周生效的自动翻过去
                    .sortedBy { it.weeks.minOrNull() ?: Int.MAX_VALUE }
            }
        }

    // 两个抽屉挂同一个浮层槽（层级高于底部导航），分两次挂会互相覆盖
    if (appearanceMounted || detailItems.isNotEmpty()) {
        ProvideOverlay {
            if (appearanceMounted) {
                ScheduleAppearanceSheet(
                    show = appearanceVisible,
                    style = style,
                    onStyleChange = onStyleChange,
                    onStyleCommit = onStyleCommit,
                    onDismiss = { appearanceVisible = false },
                    onDismissFinished = { appearanceMounted = false },
                )
            }
            if (detailItems.isNotEmpty()) {
                CourseDetailSheet(
                    items = detailItems,
                    show = detailVisible,
                    onEdit = onEditCourse,
                    onAdd = onAddCourse,
                    onDismiss = { detailVisible = false },
                    // 收起动画结束后再丢掉数据
                    onDismissFinished = {
                        detailAnchor = null
                        detailWeek = 0
                    },
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
    editor.conflict?.let { pending ->
        ConflictDialog(
            appBlur = appBlur,
            canSwap = pending.conflicts.size == 1,
            onDismiss = { editor.dismissConflict() },
            onSwap = { editor.swapConflict() },
            onOverwrite = { editor.overwriteConflict() },
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
