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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import com.miuix.hyperschedule.ui.component.CourseDetailItem
import com.miuix.hyperschedule.ui.component.CourseDetailSheet
import com.miuix.hyperschedule.ui.component.DAY_NAMES
import com.miuix.hyperschedule.ui.component.DayGridColumn
import com.miuix.hyperschedule.ui.component.LocalLastTapDown
import com.miuix.hyperschedule.ui.component.NumberPickerDialog
import com.miuix.hyperschedule.ui.component.ProvideBackToNow
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
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 「+」格：同一时刻只有一个，记录它属于哪一天哪一节 */
private data class PendingCell(val day: Int, val section: Int)

/** 周 pager 前后各留的页数；取大值让两边都能一直滑 */
private const val EdgePages = 1000

/** 学期总周数上限（与设置页范围一致）；pager 页数按它固定，改设置时无需重建 */
private const val MaxWeeks = 60

@Composable
fun SchedulePage(
    table: CourseTableData?,
    allCourses: List<CourseWithWeeks>,
    showNonCurrentWeek: Boolean = true,
    appBlur: Boolean = false,
    onEditCourse: (Course) -> Unit = {},
    onAddCourse: (day: Int, startSection: Int, endSection: Int) -> Unit = { _, _, _ -> },
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
                            IconButton(onClick = {}) {
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
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
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
                    // 本周生效的排前面
                    .sortedByDescending { it.activeInWeek }
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
