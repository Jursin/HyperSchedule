package top.jursin.hyperschedule.ui.page.today

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.jursin.hyperschedule.data.local.ReminderSettings
import top.jursin.hyperschedule.data.local.TodayStyle
import top.jursin.hyperschedule.data.repository.HolidayRepository
import top.jursin.hyperschedule.data.repository.ScheduleRepository
import top.jursin.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import top.jursin.hyperschedule.schedule.HolidayDay
import top.jursin.hyperschedule.schedule.cjkDigitGap
import top.jursin.hyperschedule.schedule.currentWeekOf
import top.jursin.hyperschedule.schedule.parseDateOrNull
import top.jursin.hyperschedule.theme.PageBottomInset
import top.jursin.hyperschedule.theme.PageTextStart
import top.jursin.hyperschedule.theme.subtleFillColor
import top.jursin.hyperschedule.ui.component.BlurredTopAppBar
import top.jursin.hyperschedule.ui.component.DatePickerDialog
import top.jursin.hyperschedule.ui.component.MarkHoliday
import top.jursin.hyperschedule.ui.component.MarkWorkSwap
import top.jursin.hyperschedule.ui.component.PageScrollColumn
import top.jursin.hyperschedule.ui.component.ProvideBackToNow
import top.jursin.hyperschedule.ui.component.SectionPill
import top.jursin.hyperschedule.ui.component.TodayAppearanceSheet
import top.jursin.hyperschedule.ui.component.TodayCourseList
import top.jursin.hyperschedule.ui.component.TopBarIconButton
import top.jursin.hyperschedule.ui.component.TopBarLeadingLabel
import top.jursin.hyperschedule.ui.component.dayLabel
import top.jursin.hyperschedule.ui.component.markOrNull
import top.jursin.hyperschedule.ui.effect.captureForBlur
import top.jursin.hyperschedule.ui.effect.pageContainerColor
import top.jursin.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.jursin.hyperschedule.ui.navigation.LocalMainTabNavigating
import top.jursin.hyperschedule.ui.navigation.LocalPageCovered
import top.jursin.hyperschedule.ui.navigation.LocalPageIsCurrent
import top.jursin.hyperschedule.ui.navigation.ProvideOverlay
import top.jursin.hyperschedule.ui.state.CourseTableData
import top.jursin.hyperschedule.ui.state.DatePager
import top.jursin.hyperschedule.ui.state.rememberDatePagerState
import top.jursin.hyperschedule.ui.state.rememberHolidayPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Background
import top.yukonga.miuix.kmp.icon.glass.JumpPrevious
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TodayPage(
    repository: ScheduleRepository,
    table: CourseTableData?,
    style: TodayStyle = TodayStyle.Default,
    onStyleChange: (TodayStyle) -> Unit = {},
    reminder: ReminderSettings = ReminderSettings.Default,
    holidayRepository: HolidayRepository? = null,
    appBlur: Boolean = false,
    wallpaper: String = "",
    wallpaperBlur: Int = 0,
    onWallpaperPick: (Uri) -> Unit = {},
    onWallpaperClear: () -> Unit = {},
    onWallpaperBlurChange: (Int) -> Unit = {},
    onWallpaperBlurCommit: () -> Unit = {},
    onCourseClick: (name: String, week: Int) -> Unit = { _, _ -> },
) {
    val scrollBehavior = MiuixScrollBehavior()
    // 顶栏糊带采的是这一层内容，滚到栏下的课程要从这里取
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

    val preClassMinutes =
        if (reminder.enabled) reminder.preMinutes else ReminderSettings.Default.preMinutes

    // 放假/调休，与课表页共用同一张（周次, 星期）表，翻日期只查表
    val holidayPlan = rememberHolidayPlan(holidayRepository, startDate, totalWeeks)

    var dateDraft by remember { mutableStateOf(dateState.today) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var appearanceVisible by remember { mutableStateOf(false) }
    // 收起动画跑完才卸载抽屉
    var appearanceMounted by remember { mutableStateOf(false) }

    val pageDate = dateState.date
    val pageWeek = startDate?.let { currentWeekOf(it, pageDate) }
    // 与本周课程页同一套文案
    val weekLabel =
        when {
            startDate == null -> ""
            pageWeek == null || pageWeek < 1 -> "未开学"
            pageWeek > totalWeeks -> "学期已结束"
            else -> "第${pageWeek}周".cjkDigitGap()
        }
    val pagePlan =
        remember(holidayPlan, pageWeek, pageDate.dayOfWeek.value) {
            pageWeek?.let { holidayPlan.of(it, pageDate.dayOfWeek.value) } ?: HolidayDay()
        }

    val pageIsCurrent = LocalPageIsCurrent.current
    val pageCovered = LocalPageCovered.current
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

    // 切到别的 Tab 或被二级页盖住时抽屉跟着收起
    LaunchedEffect(pageIsCurrent, pageCovered) {
        if (!pageIsCurrent || pageCovered) {
            appearanceVisible = false
            appearanceMounted = false
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
        // 壁纸铺在页面外的最底层，有壁纸时底色必须透明
        containerColor = pageContainerColor(),
        topBar = {
            BlurredTopAppBar(
                title = "今日课程",
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                leading = { TopBarLeadingLabel(weekLabel) },
                leadingStart = PageTextStart,
                actions = {
                    TopBarIconButton(
                        onClick = {
                            dateDraft = pageDate
                            showJumpDialog = true
                        },
                        tooltip = "跳转日期",
                    ) {
                        Icon(MiuixIcons.Glass.JumpPrevious, contentDescription = "跳转日期")
                    }
                    TopBarIconButton(
                        onClick = {
                            appearanceVisible = true
                            appearanceMounted = true
                        },
                        modifier = Modifier.padding(start = 8.dp),
                        tooltip = "今日课程外观",
                    ) {
                        Icon(MiuixIcons.Glass.Background, contentDescription = "今日课程外观")
                    }
                },
                bottomContent = {
                    DateTitleRow(date = pageDate, plan = pagePlan)
                },
                pinnedTitle = true,
            )
        },
    ) { paddingValues ->
        val topInset = paddingValues.calculateTopPadding()
        // 采样点包住整个翻页容器，否则每页各自往同一层采样会采到被换走的邻页
        BoxWithConstraints(modifier = Modifier.fillMaxSize().captureForBlur(blurBackdrop)) {
            // 栏下真正露出来的内容区高度：空状态要正好铺满它，居中的文案才和以前一样在正中
            val contentHeight = (maxHeight - topInset - PageBottomInset).coerceAtLeast(0.dp)
            DatePager(
                dateState = dateState,
                modifier = Modifier.fillMaxSize(),
                // 底部导航切页动画期间禁用手势，避免和过渡动画互抢
                userScrollEnabled = !tabNavigating,
            ) { date ->
                val week = startDate?.let { currentWeekOf(it, date) }
                val day = date.dayOfWeek.value
                // 这一天在放假/调休规则下的结论，补课时要看的可能是被调那一天的课（甚至别的周）
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
                val emptyDay = courses.isEmpty() || dayPlan.holiday

                PageScrollColumn(
                    // 采样点在外层，每页不再各自录制
                    blurBackdrop = null,
                    scrollBehavior = scrollBehavior,
                    // 顶栏高度作为内容上内边距，内容才能滚到它下方
                    topInset = topInset,
                    topPadding = 0.dp,
                    // 每页的滚动位置要互不影响，只有当前页决定顶栏按钮底色
                    syncMaterial = date == dateState.date,
                ) {
                    if (!emptyDay) {
                        TodayCourseList(
                            courses = courses,
                            slots = slots,
                            pageDate = date,
                            style = style,
                            mark = mark,
                            preClassMinutes = preClassMinutes,
                            modifier = Modifier.padding(top = 8.dp),
                            onCourseClick = { course -> shownWeek?.let { onCourseClick(course.name, it) } },
                        )
                    } else {
                        // 空状态也是滚动内容：能被拖动、钻到栏下被糊住；横屏文案更高时用 min 让它可滚
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = contentHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyCourseHint(
                                isToday = date == dateState.today,
                                isHoliday = dayPlan.holiday,
                                noStartDate = startDate == null,
                            )
                        }
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
    }
}

@Composable
private fun DateTitleRow(date: LocalDate, plan: HolidayDay) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = PageTextStart, end = 16.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${dayLabel(date.dayOfWeek.value)}".cjkDigitGap(),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 日期太长时省略，别把后面的角标挤走
            modifier = Modifier.weight(1f, fill = false),
        )
        // 放假/调休标在日期旁边，整页都是同一天，标在这里比每行重复更清楚
        if (plan.holiday || plan.workSwap) {
            val accent = plan.workSwap
            val colors = MiuixTheme.colorScheme
            val chipColor =
                if (accent) colors.primary.copy(alpha = 0.12f) else subtleFillColor()
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

/** 空状态文案，垂直位置由调用方的剩余区域居中决定 */
@Composable
private fun EmptyCourseHint(
    isToday: Boolean,
    isHoliday: Boolean,
    noStartDate: Boolean,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            noStartDate -> "未设置开学日期"
            isHoliday -> if (isToday) "今天放假，好好休息吧！" else "这天是假期"
            isToday -> "今天没有课程，好好休息吧！"
            else -> "这天没有课程"
        }
    SectionPill(
        text = text,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}
