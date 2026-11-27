package top.jursin.hyperschedule.ui.page.schedule

import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.data.db.entity.Course
import top.jursin.hyperschedule.data.db.entity.CourseWithWeeks
import top.jursin.hyperschedule.data.local.ScheduleStyle
import top.jursin.hyperschedule.data.repository.HolidayRepository
import top.jursin.hyperschedule.schedule.DEFAULT_SECTION_TIMES
import top.jursin.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import top.jursin.hyperschedule.schedule.MAX_TOTAL_WEEKS
import top.jursin.hyperschedule.schedule.currentWeekOf
import top.jursin.hyperschedule.schedule.parseDateOrNull
import top.jursin.hyperschedule.schedule.parseMinutes
import top.jursin.hyperschedule.ui.component.ConflictDialog
import top.jursin.hyperschedule.ui.component.NumberPickerDialog
import top.jursin.hyperschedule.ui.component.ProvideBackToNow
import top.jursin.hyperschedule.ui.component.rememberStretchFling
import top.jursin.hyperschedule.ui.component.rememberStretchOverscrollState
import top.jursin.hyperschedule.ui.component.stretchOverscroll
import top.jursin.hyperschedule.ui.effect.LocalFabBackdrop
import top.jursin.hyperschedule.ui.effect.captureForBlur
import top.jursin.hyperschedule.ui.effect.pageContainerColor
import top.jursin.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.jursin.hyperschedule.ui.navigation.LocalMainTabNavigating
import top.jursin.hyperschedule.ui.navigation.LocalPageCovered
import top.jursin.hyperschedule.ui.navigation.LocalPageIsCurrent
import top.jursin.hyperschedule.ui.navigation.LocalPageReselect
import top.jursin.hyperschedule.ui.navigation.ProvideOverlay
import top.jursin.hyperschedule.ui.state.CourseTableData
import top.jursin.hyperschedule.ui.state.millisToNextTick
import top.jursin.hyperschedule.ui.state.rememberHolidayPlan
import top.jursin.hyperschedule.ui.state.rememberSystemTimeChanges
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbar
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 空格「+」出现后自动收起的时长 */
private const val PendingCellLifeMs = 3_000L

/** 长按课程卡后工具栏的目标 */
private data class CourseActionTarget(val course: Course, val week: Int, val anchor: Rect)

@Composable
fun SchedulePage(
    table: CourseTableData?,
    allCourses: List<CourseWithWeeks>,
    showNonCurrentWeek: Boolean = true,
    onShowNonCurrentWeekChange: (Boolean) -> Unit = {},
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
    onDeleteCourseWeek: (courseId: String, week: Int) -> Unit = { _, _ -> },
    onAddCourse: (day: Int, startSection: Int, endSection: Int) -> Unit = { _, _, _ -> },
    onManageCourses: () -> Unit = {},
    onManageTables: () -> Unit = {},
    // 拖动改位置用
    onSaveCourses: (deletedIds: List<String>, rows: List<Pair<Course, List<Int>>>) -> Unit = { _, _ -> },
) {
    val scrollBehavior = MiuixScrollBehavior()
    // 顶栏糊带采的是这一层内容，滚到栏下的课表要从这里取
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val scope = rememberCoroutineScope()

    // 每分钟走一格，轮询落在整分上，时间被改或回到前台时立刻重算
    val timeVersion = rememberSystemTimeChanges()
    val nowState =
        produceState(LocalDateTime.now(), timeVersion) {
            while (true) {
                val current = LocalDateTime.now()
                value = current
                delay(millisToNextTick(60, current.toLocalTime()).milliseconds)
            }
        }
    val today by remember(nowState) { derivedStateOf { nowState.value.toLocalDate() } }

    // 节次列与网格高度跟着作息走
    val sectionTimes = table?.sectionTimes ?: DEFAULT_SECTION_TIMES
    val config = table?.config

    // 网格几何
    val specialBlocks = table?.specialBlocks.orEmpty()
    val morningSplit = table?.morningAfternoonSplit.orEmpty()
    val eveningSplit = table?.afternoonEveningSplit.orEmpty()
    // 午休/晚休的分界，网格与时间轴共用一份
    val breaks =
        remember(sectionTimes, morningSplit, eveningSplit, style.breakDividers) {
            if (style.breakDividers) breakMarks(sectionTimes, morningSplit, eveningSplit) else emptyList()
        }
    val layout =
        remember(sectionTimes, specialBlocks, style.hourHeight) {
            ScheduleLayout.build(
                sectionTimes = sectionTimes,
                specialBlocks = specialBlocks,
                hourHeight = style.hourHeight.dp,
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

    // 课表数据异步到达，首帧算不出本周，拿到后补一次定位
    var weekLocated by remember(table?.tableId) { mutableStateOf(false) }
    LaunchedEffect(currentWeek, table?.tableId) {
        if (weekLocated || currentWeek == null) return@LaunchedEffect
        weekLocated = true
        pagerState.scrollToPage(todayPage.coerceIn(0, lastPage))
    }

    var pendingCell by remember { mutableStateOf<PendingCell?>(null) }
    var detailAnchor by remember { mutableStateOf<Course?>(null) }
    var detailWeek by remember { mutableIntStateOf(0) }
    var detailMark by remember { mutableStateOf<String?>(null) }
    var detailVisible by remember { mutableStateOf(false) }
    var appearanceVisible by remember { mutableStateOf(false) }
    var appearanceMounted by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpWeek by remember { mutableIntStateOf(1) }
    var historyVisible by remember { mutableStateOf(false) }
    var dragLayerBounds by remember { mutableStateOf(Rect.Zero) }
    val menuAnchor = rememberGlassPopupAnchor()
    var menuOpen by remember { mutableStateOf(false) }
    var courseAction by remember { mutableStateOf<CourseActionTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<CourseActionTarget?>(null) }
    var pageWindowBounds by remember { mutableStateOf(Rect.Zero) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    var topPaddingDp by remember { mutableStateOf(0.dp) }

    LaunchedEffect(viewingPage) {
        pendingCell = null
        courseAction = null
        deleteTarget = null
    }

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
    // 顶栏按钮的底色跟着课表滚动浮现，滚回顶部就消失
    LaunchedEffect(pageScroll, scrollBehavior) {
        snapshotFlow { pageScroll.value }
            .collect { scrollBehavior.state.contentOffset = -it.toFloat() }
    }

    // 「+」停 3 秒自动收起，重复点同一格也会重新计时
    LaunchedEffect(pendingCell) {
        if (pendingCell != null) {
            delay(PendingCellLifeMs.milliseconds)
            pendingCell = null
        }
    }

    val pageIsCurrent = LocalPageIsCurrent.current
    val pageCovered = LocalPageCovered.current
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

    // 切走或被二级页盖住时收起菜单与抽屉，回来不会还开着
    LaunchedEffect(pageIsCurrent, pageCovered) {
        if (!pageIsCurrent || pageCovered) {
            menuOpen = false
            courseAction = null
            deleteTarget = null
            appearanceVisible = false
            appearanceMounted = false
            detailVisible = false
        }
    }

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
        if (dragging) courseAction = null
        else if (touchState.pointerId != null) touchState.end()
    }

    // 采集放在周 pager 之外
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { pageWindowBounds = it.boundsInWindow() },
    ) {
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
                    menuAnchor = menuAnchor,
                    onMenuOpenChange = { menuOpen = it },
                    onJumpClick = { week ->
                        jumpWeek = week.coerceIn(1, totalWeeks)
                        showJumpDialog = true
                    },
                )
            },
        ) { paddingValues ->
            topPaddingDp = paddingValues.calculateTopPadding()
            // 时间轴视图切回本页、或停在本页时再点一次本 Tab，都把当前时刻翻到屏幕中间
            if (style.timeline) {
                ScrollToNowOnShow(
                    pageScroll = pageScroll,
                    scrollBehavior = scrollBehavior,
                    sectionTimes = sectionTimes,
                    timelineHourHeight = style.timelineHourHeight,
                    nowState = nowState,
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
                        .stretchOverscroll(stretch, topInset = paddingValues.calculateTopPadding()),
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
                                blockedPointer = { touchState.pointerId ?: touchState.longPressPointer },
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
                        breaks = breaks,
                        startDate = startDate,
                        totalWeeks = totalWeeks,
                        currentWeek = nowWeek,
                        nowState = nowState,
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
                        onCourseLongPress = { clicked, week, bounds ->
                            courseAction = CourseActionTarget(clicked, week, bounds)
                        },
                        onAddCourse = onAddCourse,
                    )
                }

                ScheduleDragLayer(
                    visualProvider = { editor.dragVisual(dragLayerBounds.topLeft) },
                    cardOrigin = { editor.cardOrigin(dragLayerBounds.topLeft) },
                    style = style,
                )
            }
            GlassDialog(
                show = deleteTarget != null,
                onDismissRequest = { deleteTarget = null },
                backdrop = blurBackdrop,
                title = "删除课程",
                summary = "只删除选中课程本周的这个时段，确定删除？",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassDialogButton(
                        onClick = { deleteTarget = null },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消", style = MiuixTheme.textStyles.button)
                    }
                    GlassDialogButton(
                        onClick = {
                            deleteTarget?.let { onDeleteCourseWeek(it.course.id, it.week) }
                            deleteTarget = null
                        },
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Destructive,
                    ) {
                        Text("删除", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
        // 撤销/重做按钮画在采样层之外
        if (historyVisible) {
            CompositionLocalProvider(LocalFabBackdrop provides blurBackdrop) {
                HistoryFabColumn(
                    canUndo = editor.undoStack.isNotEmpty(),
                    canRedo = editor.redoStack.isNotEmpty(),
                    onUndo = { editor.undoMove() },
                    onRedo = { editor.redoMove() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
                )
            }
        }
        ScheduleTopBarMenu(
            show = menuOpen,
            onDismissRequest = { menuOpen = false },
            anchor = menuAnchor,
            blurBackdrop = blurBackdrop,
            onAppearanceClick = {
                appearanceVisible = true
                appearanceMounted = true
            },
            onManageTables = onManageTables,
            onManageCourses = onManageCourses,
        )
        val action = courseAction
        if (action != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { courseAction = null } },
            )
            val density = LocalDensity.current
            val gapPx = with(density) { 8.dp.toPx() }
            val insetX = with(density) { 12.dp.toPx() }
            val topLimitPx = with(density) { topPaddingDp.toPx() + gapPx }
            val (gapAbove, gapBelow) =
                remember(action, allCourses, sectionTimes) {
                    fun minutesOf(course: Course): Pair<Int, Int>? {
                        if (course.isCustomTime) {
                            val start = parseMinutes(course.customStartTime) ?: return null
                            val end = parseMinutes(course.customEndTime) ?: return null
                            return (start to end).takeIf { end > start }
                        }
                        val first = sectionTimes.getOrNull((course.startSection ?: 1) - 1) ?: return null
                        val last = sectionTimes.getOrNull((course.endSection ?: course.startSection ?: 1) - 1) ?: return null
                        val start = parseMinutes(first.first) ?: return null
                        val end = parseMinutes(last.second) ?: return null
                        return (start to end).takeIf { end > start }
                    }
                    val self = minutesOf(action.course) ?: return@remember true to true
                    val peers =
                        allCourses.map { it.course }.filter {
                            it.day == action.course.day && it.id != action.course.id
                        }
                    val blockedAbove =
                        peers.any { c ->
                            val r = minutesOf(c) ?: return@any false
                            r.second <= self.first && self.first - r.second < 55
                        }
                    val blockedBelow =
                        peers.any { c ->
                            val r = minutesOf(c) ?: return@any false
                            r.first >= self.second && r.first - self.second < 55
                        }
                    !blockedAbove to !blockedBelow
                }
            GlassFloatingToolbar(
                modifier =
                    Modifier
                        .onSizeChanged { toolbarSize = it }
                        .offset {
                            val w = toolbarSize.width.toFloat()
                            val h = toolbarSize.height.toFloat()
                            val cardLeft = action.anchor.left - pageWindowBounds.left
                            val cardRight = action.anchor.right - pageWindowBounds.left
                            val cardTop = action.anchor.top - pageWindowBounds.top
                            val cardBottom = action.anchor.bottom - pageWindowBounds.top
                            val maxX = (pageWindowBounds.width - gapPx - w).coerceAtLeast(gapPx)
                            val maxY = (pageWindowBounds.height - gapPx - h).coerceAtLeast(gapPx)
                            val left = cardLeft - insetX
                            val right = cardRight - w + insetX
                            val x =
                                when {
                                    left in gapPx..maxX -> left
                                    right in gapPx..maxX -> right
                                    else -> left.coerceIn(gapPx, maxX)
                                }
                            val above = cardTop - h
                            val fitsAbove = above in topLimitPx..maxY
                            val fitsBelow = cardBottom in topLimitPx..maxY
                            val hasAbove = fitsAbove && (gapAbove || !fitsBelow)
                            val hasBelow = fitsBelow && (gapBelow || !fitsAbove)
                            val y =
                                (if (hasBelow && !hasAbove) cardBottom else above)
                                    .coerceIn(topLimitPx.coerceAtMost(maxY), maxY)
                            IntOffset(x.toInt(), y.toInt())
                        },
                backdrop = blurBackdrop,
                cornerRadius = 20.dp,
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            onEditCourse(action.course)
                            courseAction = null
                        },
                    ) {
                        Icon(MiuixIcons.Glass.Edit, contentDescription = "编辑课程")
                    }
                    IconButton(
                        onClick = {
                            deleteTarget = action
                            courseAction = null
                        },
                    ) {
                        Icon(MiuixIcons.Glass.Delete, contentDescription = "删除时段")
                    }
                }
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
                    showNonCurrentWeek = showNonCurrentWeek,
                    onShowNonCurrentWeekChange = onShowNonCurrentWeekChange,
                    style = style,
                    onStyleChange = onStyleChange,
                    onStyleCommit = onStyleCommit,
                    onDismiss = { appearanceVisible = false },
                    onDismissFinished = { appearanceMounted = false },
                    wallpaper = wallpaper,
                    wallpaperBlur = wallpaperBlur,
                    onWallpaperPick = { uri ->
                        appearanceVisible = false
                        appearanceMounted = false
                        onWallpaperPick(uri)
                    },
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
