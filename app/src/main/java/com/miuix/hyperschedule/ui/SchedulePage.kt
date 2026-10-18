package com.miuix.hyperschedule.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.schedule.DEFAULT_SECTION_TIMES
import com.miuix.hyperschedule.schedule.currentSectionAt
import com.miuix.hyperschedule.schedule.currentWeekOf
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.weekDatesOf
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.LocalLastTapDown
import com.miuix.hyperschedule.ui.component.NumberPickerDialog
import com.miuix.hyperschedule.ui.component.ProvideBackToNow
import com.miuix.hyperschedule.ui.navigation.LocalMainTabNavigating
import com.miuix.hyperschedule.ui.navigation.LocalPageIsCurrent
import com.miuix.hyperschedule.ui.theme.captureForBlur
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.minutes

// 网格几何
private val SectionHeight = 92.dp
private val SectionColumnWidth = 36.dp
private val WeekdayRowHeight = 40.dp
private val CardCornerRadius = 10.dp
private val CardInset = 2.dp

/** 今天 / 当前节次高亮蓝 */
private val TodayHighlight = Color(0xFF3482FF)

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private val SectionCount = DEFAULT_SECTION_TIMES.size

/** 周 pager 前后各留的页数；取大值让两边都能一直滑 */
private const val EdgePages = 1000

/** 「+」格：同一时刻只有一个，记录它属于哪一天哪一节 */
private data class PendingCell(val day: Int, val section: Int)

@Composable
fun SchedulePage(store: SettingsStore, appBlur: Boolean = false) {
    val settings = store.state
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

    // 空串 = 未设置
    val startDate = remember(settings.startDate) { parseDateOrNull(settings.startDate) }
    val totalWeeks = settings.totalWeeks.coerceAtLeast(1)
    val currentWeek = remember(startDate, today) { startDate?.let { currentWeekOf(it, today) } }

    fun weekOfPage(page: Int): Int = page - EdgePages + 1

    fun pageOfWeek(week: Int): Int = EdgePages + week - 1

    val todayPage = currentWeek?.let { pageOfWeek(it) }
    val pageCount = EdgePages * 2 + totalWeeks
    val lastPage = pageCount - 1
    val pagerState =
        remember {
            PagerState(
                currentPage = (todayPage ?: pageOfWeek(1)).coerceIn(0, lastPage),
                pageCount = { pageCount },
            )
        }
    val viewingPage by remember { derivedStateOf { pagerState.currentPage } }

    var pendingCell by remember { mutableStateOf<PendingCell?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpWeek by remember { mutableIntStateOf(1) }

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

    // 换页后原来的「+」不再属于当前视图
    LaunchedEffect(viewingPage) { pendingCell = null }

    // 点击范围外的任何一处都收起「+」；底栏在页面之外，靠 MainScaffold 记的全局按下位置覆盖
    var dayColumnsBounds by remember { mutableStateOf<Rect?>(null) }
    val lastTapDown = LocalLastTapDown.current
    LaunchedEffect(Unit) {
        snapshotFlow { lastTapDown.value }.collect { position ->
            val bounds = dayColumnsBounds
            if (position != null && (bounds == null || !bounds.contains(position))) {
                pendingCell = null
            }
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
            val currentSection =
                if (week == currentWeek) currentSectionAt(now.toLocalTime()) else -1
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
                    SectionTimesColumn(currentSection = currentSection)
                    // 「网格」= 7 个日期列这块矩形；页面按它判断点击是否落在网格外
                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned { dayColumnsBounds = it.boundsInRoot() },
                    ) {
                        for (day in 1..DAY_NAMES.size) {
                            DayGridColumn(
                                modifier = Modifier.weight(1f),
                                pendingSection = pendingCell?.takeIf { it.day == day }?.section ?: -1,
                                // 没设开学日期就没有可添加的日期，不可点
                                onCellTap =
                                    if (startDate != null) {
                                        { section -> pendingCell = PendingCell(day, section) }
                                    } else {
                                        null
                                    },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
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

/** 星期行：年份 + 周一~周日 + 日期，今天蓝色加粗；未设置开学日期时只有星期名 */
@Composable
private fun WeekdayRow(startDate: LocalDate?, week: Int, currentWeek: Int?, today: LocalDate) {
    val dates =
        remember(startDate, week) {
            if (startDate != null) weekDatesOf(startDate, week) else null
        }
    Row(modifier = Modifier.fillMaxWidth().height(WeekdayRowHeight).padding(end = 2.dp)) {
        Box(
            modifier = Modifier.width(SectionColumnWidth).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dates?.first()?.year?.toString().orEmpty(),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
        DAY_NAMES.forEachIndexed { index, dayName ->
            val date = dates?.getOrNull(index)
            val isToday = date != null && week == currentWeek && date == today
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = dayName,
                    style =
                        MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        ),
                    color = if (isToday) TodayHighlight else MiuixTheme.colorScheme.onSurface,
                )
                if (date != null) {
                    Text(
                        text = "${date.monthValue}/${date.dayOfMonth}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (isToday) TodayHighlight else MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
        }
    }
}

/** 左侧节次列：序号 + 起止时间三行；当前节次整列蓝色 */
@Composable
private fun SectionTimesColumn(currentSection: Int) {
    Column(modifier = Modifier.width(SectionColumnWidth)) {
        DEFAULT_SECTION_TIMES.forEachIndexed { index, (startTime, endTime) ->
            val isCurrent = index + 1 == currentSection
            Box(
                modifier = Modifier.fillMaxWidth().height(SectionHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${index + 1}",
                        style =
                            MiuixTheme.textStyles.body2.copy(
                                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                            ),
                        color = if (isCurrent) TodayHighlight else MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = startTime,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                        color = if (isCurrent) TodayHighlight else MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                    Text(
                        text = endTime,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                        color = if (isCurrent) TodayHighlight else MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
        }
    }
}

/**
 * 单日列：整列一个点击层，按落点纵坐标反查节次，「+」格叠在对应节次上
 * [onCellTap] 为 null 表示该页不可点（未设置开学日期/未开学/已结束）
 */
@Composable
private fun DayGridColumn(
    pendingSection: Int,
    onCellTap: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val sectionPx = with(density) { SectionHeight.toPx() }
    val currentOnCellTap by rememberUpdatedState(onCellTap)
    Box(
        modifier =
            modifier
                .height(SectionHeight * SectionCount)
                .pointerInput(onCellTap != null) {
                    if (currentOnCellTap == null) return@pointerInput
                    detectTapGestures { offset ->
                        val section = (offset.y / sectionPx).toInt() + 1
                        if (section in 1..SectionCount) currentOnCellTap?.invoke(section)
                    }
                },
    ) {
        if (pendingSection in 1..SectionCount) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(SectionHeight)
                        .offset(y = SectionHeight * (pendingSection - 1)),
            ) {
                Card(
                    modifier = Modifier.fillMaxSize().padding(CardInset),
                    cornerRadius = CardCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    colors =
                        CardDefaults.defaultColors(
                            color = Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.13f else 0.15f),
                            contentColor = Color(0xFF9E9E9E).copy(alpha = 0.5f),
                        ),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true,
                    // 本次只做视觉反馈：再点无动作
                    onClick = {},
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = "添加",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
