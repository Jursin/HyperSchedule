package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.HolidayDay
import com.miuix.hyperschedule.schedule.currentWeekOf
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.theme.PageBottomInset
import com.miuix.hyperschedule.theme.TodayStyle
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.DatePager
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.DatePickerDialog
import com.miuix.hyperschedule.ui.component.MarkHoliday
import com.miuix.hyperschedule.ui.component.MarkWorkSwap
import com.miuix.hyperschedule.ui.component.PageScrollColumn
import com.miuix.hyperschedule.ui.component.ProvideBackToNow
import com.miuix.hyperschedule.ui.component.SectionPill
import com.miuix.hyperschedule.ui.component.TodayAppearanceSheet
import com.miuix.hyperschedule.ui.component.TodayCourseList
import com.miuix.hyperschedule.ui.component.dayLabel
import com.miuix.hyperschedule.ui.component.markOrNull
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import com.miuix.hyperschedule.ui.navigation.LocalMainTabNavigating
import com.miuix.hyperschedule.ui.navigation.LocalPageIsCurrent
import com.miuix.hyperschedule.ui.navigation.ProvideOverlay
import com.miuix.hyperschedule.ui.rememberDatePagerState
import com.miuix.hyperschedule.ui.rememberHolidayPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TodayPage(
    repository: ScheduleRepository,
    table: CourseTableData?,
    style: TodayStyle = TodayStyle.Default,
    onStyleChange: (TodayStyle) -> Unit = {},
    holidayRepository: HolidayRepository? = null,
    appBlur: Boolean = false,
    onCourseClick: (name: String, week: Int) -> Unit = { _, _ -> },
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val scope = rememberCoroutineScope()
    val dateState = rememberDatePagerState()

    val tableId = table?.tableId
    val slots = table?.slots.orEmpty()
    val startDate =
        remember(table?.config?.semesterStartDate) {
            table?.config?.semesterStartDate?.let(::parseDateOrNull)
        }
    val totalWeeks = (table?.config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS).coerceAtLeast(1)

    // 放假 / 调休：与课表页共用同一张（周次, 星期）表，翻日期只查表
    val holidayPlan = rememberHolidayPlan(holidayRepository, startDate, totalWeeks)

    var dateDraft by remember { mutableStateOf(dateState.today) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var appearanceVisible by remember { mutableStateOf(false) }
    // 收起动画跑完才卸载抽屉
    var appearanceMounted by remember { mutableStateOf(false) }

    val pageIsCurrent = LocalPageIsCurrent.current
    val tabNavigating = LocalMainTabNavigating.current
    // 离开过就记一笔，切回那一刻或导航结束时复位
    var pendingReset by remember { mutableStateOf(false) }
    LaunchedEffect(pageIsCurrent, tabNavigating) {
        if (!pageIsCurrent) pendingReset = true
        // 导航结束（已在屏外）或刚切回（此刻 pager 还没动，本页仍在屏外）时复位
        if (pendingReset && (pageIsCurrent || !tabNavigating)) {
            dateState.snapToDate(dateState.today)
            pendingReset = false
        }
    }

    // 非今天时显示「今」按钮，待复位期间不显示
    val showBackToNow =
        remember(dateState.pagerState) { derivedStateOf { !pendingReset && !dateState.isToday } }

    ProvideBackToNow(
        visible = showBackToNow,
        onClick = { scope.launch { dateState.backToToday() } },
    )

    Scaffold(
        topBar = {
            BlurredTopAppBar(
                title = "今日课表",
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            appearanceVisible = true
                            appearanceMounted = true
                        },
                    ) {
                        Icon(MiuixIcons.Background, contentDescription = "今日课表外观")
                    }
                },
            )
        },
    ) { paddingValues ->
        DatePager(
            dateState = dateState,
            modifier = Modifier.fillMaxSize(),
            // 底部导航切页动画期间禁用手势，避免和过渡动画互抢
            userScrollEnabled = !tabNavigating,
        ) { pageDate ->
            val week = startDate?.let { currentWeekOf(it, pageDate) }
            val day = pageDate.dayOfWeek.value
            // 这一天在放假 / 调休规则下的结论：补课时要看的可能是被调那一天的课（甚至别的周）
            val dayPlan =
                remember(holidayPlan, week, day) {
                    week?.let { holidayPlan.of(it, day) } ?: HolidayDay()
                }
            // 没设开学日期就没有周次，课程查询直接跳过
            val shownWeek = if (dayPlan.displayWeek > 0) dayPlan.displayWeek else week
            val shownDay = dayPlan.dayOr(day)
            val coursesFlow =
                remember(tableId, shownDay, shownWeek) {
                    if (tableId == null || shownWeek == null) {
                        flowOf(emptyList())
                    } else {
                        repository.coursesOfDay(tableId, shownDay, shownWeek)
                    }
                }
            val courses by coursesFlow.collectAsState(emptyList())
            val mark = dayPlan.markOrNull()

            // 标题占掉的高度：空状态要把它下面的剩余区域平分上下留白
            var titleHeight by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current

            Box(modifier = Modifier.fillMaxSize()) {
                PageScrollColumn(
                    // 只让当前页往 backdrop 里录：同一个 backdrop 只有一个采集点
                    blurBackdrop = blurBackdrop.takeIf { pageDate == dateState.date },
                    scrollBehavior = scrollBehavior,
                    // 顶栏高度作为滚动内容的内边距：内容才能滚到顶栏下方，顶栏有东西可模糊
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                    topPadding = 0.dp,
                ) {
                    Box(
                        modifier =
                            Modifier.onGloballyPositioned {
                                titleHeight = with(density) { it.size.height.toDp() }
                            },
                    ) {
                        DateTitleRow(
                            date = pageDate,
                            plan = dayPlan,
                            onJumpClick = {
                                dateDraft = pageDate
                                showJumpDialog = true
                            },
                        )
                    }
                    // 放假这天不上课：不列课程，直接走下面的空状态
                    if (courses.isNotEmpty() && !dayPlan.holiday) {
                        TodayCourseList(
                            courses = courses,
                            slots = slots,
                            pageDate = pageDate,
                            style = style,
                            mark = mark,
                            modifier = Modifier.padding(top = 8.dp),
                            onCourseClick = { course -> shownWeek?.let { onCourseClick(course.name, it) } },
                        )
                    }
                }

                if (courses.isEmpty() || dayPlan.holiday) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    top = paddingValues.calculateTopPadding() + titleHeight,
                                    bottom = PageBottomInset,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyCourseHint(
                            isToday = pageDate == dateState.today,
                            isHoliday = dayPlan.holiday,
                        )
                    }
                }
            }
        }
    }

    if (showJumpDialog) {
        DatePickerDialog(
            title = "跳转日期",
            date = dateDraft,
            onDateChange = { dateDraft = it },
            appBlur = appBlur,
            onDismiss = { showJumpDialog = false },
            onConfirm = {
                scope.launch { dateState.animateToDate(dateDraft) }
                showJumpDialog = false
            },
        )
    }

    // 抽屉挂在跨页浮层宿主上，层级高于底部导航
    if (appearanceMounted) {
        ProvideOverlay {
            TodayAppearanceSheet(
                show = appearanceVisible,
                style = style,
                onStyleChange = onStyleChange,
                onDismiss = { appearanceVisible = false },
                onDismissFinished = { appearanceMounted = false },
            )
        }
    }
}

@Composable
private fun DateTitleRow(date: LocalDate, plan: HolidayDay, onJumpClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset(x = (-15).dp)
                .padding(start = 28.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${dayLabel(date.dayOfWeek.value)}",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 日期太长时省略，别把后面的按钮和角标挤走
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(
            onClick = onJumpClick,
            modifier = Modifier.padding(start = 6.dp),
            minWidth = 32.dp,
            minHeight = 32.dp,
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowUpDown,
                contentDescription = "跳转日期",
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        // 放假 / 调休标在日期旁边：整页都是同一天，标在这里比每行重复更清楚
        if (plan.holiday || plan.workSwap) {
            val accent = plan.workSwap
            val colors = MiuixTheme.colorScheme
            val chipColor =
                if (accent) colors.primary.copy(alpha = 0.12f) else colors.onSurface.copy(alpha = 0.08f)
            Text(
                text =
                    when {
                        // 补课带上被调的是哪一天，放假只标一个「假」
                        accent && plan.displayDay > 0 -> "$MarkWorkSwap · ${dayLabel(plan.displayDay)}"
                        accent -> MarkWorkSwap
                        else -> MarkHoliday
                    },
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                color = if (accent) colors.primary else colors.onSurfaceVariantSummary,
                maxLines = 1,
                modifier =
                    Modifier
                        .padding(start = 4.dp)
                        .background(color = chipColor, shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** 空状态文案；垂直位置由调用方的剩余区域居中决定 */
@Composable
private fun EmptyCourseHint(isToday: Boolean = false, isHoliday: Boolean = false) {
    val text =
        when {
            isHoliday -> if (isToday) "今天放假，好好休息吧！" else "这天是假期"
            isToday -> "今天没有课程，好好休息吧！"
            else -> "这天没有课程"
        }
    SectionPill(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}
