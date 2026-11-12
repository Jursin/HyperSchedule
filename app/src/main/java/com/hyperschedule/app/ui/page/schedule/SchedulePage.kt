package com.hyperschedule.app.ui.page.schedule

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.db.entity.Course
import com.hyperschedule.app.data.db.entity.CourseWithWeeks
import com.hyperschedule.app.data.local.ScheduleStyle
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.schedule.DEFAULT_SECTION_TIMES
import com.hyperschedule.app.schedule.DEFAULT_TOTAL_WEEKS
import com.hyperschedule.app.schedule.MAX_TOTAL_WEEKS
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.schedule.currentWeekOf
import com.hyperschedule.app.schedule.parseDateOrNull
import com.hyperschedule.app.ui.component.ConflictDialog
import com.hyperschedule.app.ui.component.CourseDetailItem
import com.hyperschedule.app.ui.component.CourseDetailSheet
import com.hyperschedule.app.ui.component.NumberPickerDialog
import com.hyperschedule.app.ui.component.ProvideBackToNow
import com.hyperschedule.app.ui.component.ScheduleAppearanceSheet
import com.hyperschedule.app.ui.component.ScheduleDragLayer
import com.hyperschedule.app.ui.component.ScheduleLayout
import com.hyperschedule.app.ui.component.ScrollToNowOnShow
import com.hyperschedule.app.ui.component.isSameSlotAs
import com.hyperschedule.app.ui.component.rememberStretchFling
import com.hyperschedule.app.ui.component.rememberStretchOverscrollState
import com.hyperschedule.app.ui.component.stretchOverscroll
import com.hyperschedule.app.ui.effect.captureForBlur
import com.hyperschedule.app.ui.effect.pageContainerColor
import com.hyperschedule.app.ui.effect.rememberAppBlurBackdrop
import com.hyperschedule.app.ui.effect.wallpaperVisible
import com.hyperschedule.app.ui.navigation.LocalMainTabNavigating
import com.hyperschedule.app.ui.navigation.LocalPageIsCurrent
import com.hyperschedule.app.ui.navigation.LocalPageReselect
import com.hyperschedule.app.ui.navigation.ProvideOverlay
import com.hyperschedule.app.ui.state.CourseTableData
import com.hyperschedule.app.ui.state.rememberHolidayPlan
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

/** 空格「+」出现后自动收起的时长 */
private const val PendingCellLifeMs = 3_000L

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
    wallpaper: String = "",
    wallpaperBlur: Int = 0,
    onWallpaperPick: (Uri) -> Unit = {},
    onWallpaperClear: () -> Unit = {},
    onWallpaperBlurChange: (Int) -> Unit = {},
    onWallpaperBlurCommit: () -> Unit = {},
    onEditCourse: (Course) -> Unit = {},
    onAddCourse: (day: Int, startSection: Int, endSection: Int) -> Unit = { _, _, _ -> },
    onManageCourses: () -> Unit = {},
    onManageTables: () -> Unit = {},
    // 拖动改位置用
    onSaveCourses: (deletedIds: List<String>, rows: List<Pair<Course, List<Int>>>) -> Unit = { _, _ -> },
) {
    val scrollBehavior = MiuixScrollBehavior()
    val hasWallpaper = wallpaperVisible()
    // 顶栏不铺底色时不用再录采样层
    val blurBackdrop = rememberAppBlurBackdrop(appBlur && !hasWallpaper)
    val scope = rememberCoroutineScope()

    // 每分钟走一格
    val now by
        produceState(initialValue = LocalDateTime.now()) {
            while (true) {
                delay(1.minutes)
                value = LocalDateTime.now()
            }
        }
    val today = now.toLocalDate()

    // 节次列与网格高度跟着作息走
    val sectionTimes = table?.sectionTimes ?: DEFAULT_SECTION_TIMES
    val config = table?.config

    // 网格几何
    val specialBlocks = table?.specialBlocks.orEmpty()
    val morningSplit = table?.morningAfternoonSplit.orEmpty()
    val eveningSplit = table?.afternoonEveningSplit.orEmpty()
    val layout =
        remember(sectionTimes, specialBlocks, morningSplit, eveningSplit, style.hourHeight, style.breakDividers) {
            ScheduleLayout.build(
                sectionTimes = sectionTimes,
                specialBlocks = specialBlocks,
                morningSplit = morningSplit,
                eveningSplit = eveningSplit,
                hourHeight = style.hourHeight.dp,
                showBreakDividers = style.breakDividers,
            )
        }

    // 空串 = 未设置
    val startDate = remember(config?.semesterStartDate) { parseDateOrNull(config?.semesterStartDate.orEmpty()) }
    val totalWeeks = (config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS).coerceAtLeast(1)
    val currentWeek = remember(startDate, today) { startDate?.let { currentWeekOf(it, today) } }

    val holidayPlan = rememberHolidayPlan(holidayRepository, startDate, totalWeeks)

    val editor =
        rememberScheduleEditor(
            layout = layout,
            sectionTimes = sectionTimes,
            isRemapped = { week, day -> holidayPlan.of(week, day).remapped },
            onSave = onSaveCourses,
        )

    fun pageOfWeek(week: Int): Int = EdgePages + week - 1

    // 没设开学日期时把第 1 页当成本周
    val nowWeek = currentWeek ?: 1
    val todayPage = pageOfWeek(nowWeek)
    val pageCount = EdgePages * 2 + MAX_TOTAL_WEEKS
    val lastPage = pageCount - 1
    // 页数固定，学期设置只影响内容
    val pagerState =
        remember {
            PagerState(
                currentPage = todayPage.coerceIn(0, lastPage),
                pageCount = { pageCount },
            )
        }
    val viewingPage by remember { derivedStateOf { pagerState.currentPage } }

    // 课表数据异步到达，首帧算不出本周，拿到后补一次定位，否则会停在第 1 周
    var weekLocated by remember(table?.tableId) { mutableStateOf(false) }
    LaunchedEffect(currentWeek, table?.tableId) {
        if (weekLocated || currentWeek == null) return@LaunchedEffect
        weekLocated = true
        pagerState.scrollToPage(todayPage.coerceIn(0, lastPage))
    }

    var pendingCell by remember { mutableStateOf<PendingCell?>(null) }
    var detailAnchor by remember { mutableStateOf<Course?>(null) }
    // 点开的卡属于哪一周
    var detailWeek by remember { mutableIntStateOf(0) }
    // 那一格的放假/调休角标
    var detailMark by remember { mutableStateOf<String?>(null) }
    var detailVisible by remember { mutableStateOf(false) }
    var appearanceVisible by remember { mutableStateOf(false) }
    // 收起动画跑完才卸载抽屉
    var appearanceMounted by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpWeek by remember { mutableIntStateOf(1) }
    var historyVisible by remember { mutableStateOf(false) }
    var dragLayerBounds by remember { mutableStateOf(Rect.Zero) }

    // 换页后原来的「+」不再属于当前视图
    LaunchedEffect(viewingPage) { pendingCell = null }

    // 纵向滚动全周共用一份，拉伸作用在整页
    val pageScroll = rememberScrollState()
    val stretch = rememberStretchOverscrollState()
    // 抬手或回弹结束就收回去
    LaunchedEffect(pageScroll, stretch) {
        snapshotFlow { pageScroll.isScrollInProgress }
            .collectLatest { scrolling -> if (!scrolling) stretch.settle() }
    }
    // 与系统同一套衰减，撞边即刻交给拉伸
    val fling = rememberStretchFling(stretch) { pageScroll.value }

    // 「+」停 3 秒自动收起，重复点同一格也会重新计时
    LaunchedEffect(pendingCell) {
        if (pendingCell != null) {
            delay(PendingCellLifeMs)
            pendingCell = null
        }
    }

    val pageIsCurrent = LocalPageIsCurrent.current
    val tabNavigating = LocalMainTabNavigating.current
    // 离开过就复位到本周
    var pendingReset by remember { mutableStateOf(false) }
    LaunchedEffect(pageIsCurrent, tabNavigating, todayPage) {
        if (!pageIsCurrent) pendingReset = true
        // 导航结束或刚切回时复位
        if (pendingReset && (pageIsCurrent || !tabNavigating)) {
            pagerState.scrollToPage(todayPage.coerceIn(0, lastPage))
            pendingReset = false
        }
    }

    val pageWeek = viewingPage - EdgePages + 1

    // 不在本周时显示「今」
    val showBackToNow = remember(todayPage) {
        derivedStateOf { !pendingReset && todayPage != viewingPage }
    }

    // 切课表后作废旧历史
    LaunchedEffect(table?.tableId) { editor.resetHistory() }

    // 历史按钮 3 秒后隐藏
    LaunchedEffect(editor.undoStack.size, editor.redoStack.size) {
        if (!editor.hasHistory) {
            historyVisible = false
            return@LaunchedEffect
        }
        historyVisible = true
        delay(3000.milliseconds)
        historyVisible = false
    }

    // 数据回流后收起灰卡，数据没回来也要兜底
    LaunchedEffect(allCourses, editor.dragReleased) {
        if (!editor.dragReleased) return@LaunchedEffect
        editor.settleDrag(allCourses)
        delay(1000.milliseconds)
        editor.clearReleasedDrag()
    }

    // 跨周拖课
    val touchState = remember { ScheduleTouchState() }
    val edgePager = rememberEdgeAutoPager()
    val settleSlot = remember { PagerSettleSlot() }
    // pager 在 root 里的左上角
    var pagerOrigin by remember { mutableStateOf(Offset.Zero) }

    // 拖动结束但指针记录还在时清掉
    val dragging = editor.drag != null
    LaunchedEffect(dragging) {
        if (!dragging && touchState.pointerId != null) touchState.end()
    }

    // 采集放在周 pager 之外
    Scaffold(
        // 拉伸只作用在顶栏之下的内容，顶栏本身保持固定
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(stretch.connection),
        // 壁纸铺在页面外的最底层，有壁纸时底色必须透明
        containerColor = pageContainerColor(),
        topBar = {
            ScheduleTopBar(
                startDate = startDate,
                pageWeek = pageWeek,
                totalWeeks = totalWeeks,
                currentWeek = nowWeek,
                today = today,
                gridLines = style.gridLines || style.timeline,
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
        // 时间轴视图切回本页、或停在本页时再点一次本 Tab，都把当前时刻翻到屏幕中间
        if (style.timeline) {
            ScrollToNowOnShow(
                pageScroll = pageScroll,
                sectionTimes = sectionTimes,
                timelineHourHeight = style.timelineHourHeight,
                now = now,
                topPadding = paddingValues.calculateTopPadding(),
                pageIsCurrent = pageIsCurrent,
                reselectTick = LocalPageReselect.current,
            )
        }
        // 拖动浮层与撤销按钮都挂在这一层
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords -> dragLayerBounds = coords.boundsInRoot() }
                    // 采集整块内容，顶栏模糊才带得上页面内容
                    .captureForBlur(blurBackdrop)
                    // 拉伸落在顶栏之下
                    .stretchOverscroll(stretch),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { pagerOrigin = it.boundsInRoot().topLeft }
                        // 横滑翻周自己接管
                        .schedulePagerGesture(
                            pagerState = pagerState,
                            settleSlot = settleSlot,
                            enabled = { !tabNavigating },
                            blockedPointer = { touchState.pointerId },
                            // 手指带着课卡时只许停在学期内
                            pages = {
                                if (editor.drag != null) pageOfWeek(1)..pageOfWeek(totalWeeks) else 0..lastPage
                            },
                            onVerticalScroll = { dy -> pageScroll.dispatchRawDelta(-dy) },
                        )
                        // 跨周拖课
                        .scheduleDragOwner(
                            editor = editor,
                            touchState = touchState,
                            pagerState = pagerState,
                            settleSlot = settleSlot,
                            allCourses = { allCourses },
                            validWeeks = { 1..totalWeeks },
                            origin = { pagerOrigin },
                            onMove = { centerX ->
                                edgePager.update(
                                    centerX,
                                    dragLayerBounds,
                                    pageOfWeek(1)..pageOfWeek(totalWeeks),
                                    pagerState,
                                )
                            },
                            onEnd = { edgePager.stop() },
                        ),
                // 手势由上面接管
                userScrollEnabled = false,
                // 邻页不必预组合
                beyondViewportPageCount = 1,
            ) { page ->
                ScheduleWeekPage(
                    page = page,
                    layout = layout,
                    sectionTimes = sectionTimes,
                    style = style,
                    startDate = startDate,
                    totalWeeks = totalWeeks,
                    currentWeek = nowWeek,
                    now = now,
                    holidayPlan = holidayPlan,
                    allCourses = allCourses,
                    showNonCurrentWeek = showNonCurrentWeek,
                    topPadding = paddingValues.calculateTopPadding(),
                    pageScroll = pageScroll,
                    fling = fling,
                    pendingCell = pendingCell,
                    onPendingCellChange = { pendingCell = it },
                    editor = editor,
                    touchState = touchState,
                    onGridPositioned = { rect ->
                        // 落点要按当前这一页的网格算
                        if (page == pagerState.currentPage) editor.updateLiveGrid(rect)
                    },
                    onCourseClick = { clicked, shownWeek, mark ->
                        detailAnchor = clicked
                        detailWeek = shownWeek
                        detailMark = mark
                        detailVisible = true
                    },
                    onAddCourse = onAddCourse,
                )
            }

            ScheduleDragLayer(
                visualProvider = { editor.dragVisual(dragLayerBounds.topLeft) },
                cardOrigin = { editor.cardOrigin(dragLayerBounds.topLeft) },
                style = style,
            )

            // 不做淡入/缩放
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

    // 两个抽屉共用一个浮层槽
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
                    wallpaper = wallpaper,
                    wallpaperBlur = wallpaperBlur,
                    onWallpaperPick = onWallpaperPick,
                    onWallpaperClear = onWallpaperClear,
                    onWallpaperBlurChange = onWallpaperBlurChange,
                    onWallpaperBlurCommit = onWallpaperBlurCommit,
                )
            }
            if (detailItems.isNotEmpty()) {
                CourseDetailSheet(
                    items = detailItems,
                    mark = detailMark,
                    show = detailVisible,
                    onEdit = onEditCourse,
                    onAdd = onAddCourse,
                    onDismiss = { detailVisible = false },
                    // 收起动画结束后再丢掉数据
                    onDismissFinished = {
                        detailAnchor = null
                        detailWeek = 0
                        detailMark = null
                    },
                )
            }
        }
    }

    ProvideBackToNow(
        visible = showBackToNow,
        onClick = { scope.launch { pagerState.animateScrollToPage(todayPage.coerceIn(0, lastPage)) } },
    )

    // 落点已有课
    editor.conflict?.let { pending ->
        ConflictDialog(
            appBlur = appBlur,
            canSwap = pending.canSwap,
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
            label = { "第${it}周".cjkDigitGap() },
            appBlur = appBlur,
            onDismiss = { showJumpDialog = false },
            onConfirm = {
                scope.launch { pagerState.animateScrollToPage(pageOfWeek(jumpWeek).coerceIn(0, lastPage)) }
                showJumpDialog = false
            },
        )
    }
}
