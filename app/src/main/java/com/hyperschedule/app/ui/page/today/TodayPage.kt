package com.hyperschedule.app.ui.page.today

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperschedule.app.data.local.ReminderSettings
import com.hyperschedule.app.data.local.TodayStyle
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.data.repository.ScheduleRepository
import com.hyperschedule.app.schedule.DEFAULT_TOTAL_WEEKS
import com.hyperschedule.app.schedule.HolidayDay
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.schedule.currentWeekOf
import com.hyperschedule.app.schedule.parseDateOrNull
import com.hyperschedule.app.theme.PageBottomInset
import com.hyperschedule.app.theme.subtleFillColor
import com.hyperschedule.app.ui.component.BlurredTopAppBar
import com.hyperschedule.app.ui.component.DatePickerDialog
import com.hyperschedule.app.ui.component.MarkHoliday
import com.hyperschedule.app.ui.component.MarkWorkSwap
import com.hyperschedule.app.ui.component.PageScrollColumn
import com.hyperschedule.app.ui.component.ProvideBackToNow
import com.hyperschedule.app.ui.component.SectionPill
import com.hyperschedule.app.ui.component.TodayAppearanceSheet
import com.hyperschedule.app.ui.component.TodayCourseList
import com.hyperschedule.app.ui.component.TopBarIconButton
import com.hyperschedule.app.ui.component.dayLabel
import com.hyperschedule.app.ui.component.markOrNull
import com.hyperschedule.app.ui.effect.captureForBlur
import com.hyperschedule.app.ui.effect.pageContainerColor
import com.hyperschedule.app.ui.effect.rememberAppBlurBackdrop
import com.hyperschedule.app.ui.navigation.LocalMainTabNavigating
import com.hyperschedule.app.ui.navigation.LocalPageIsCurrent
import com.hyperschedule.app.ui.navigation.ProvideOverlay
import com.hyperschedule.app.ui.state.CourseTableData
import com.hyperschedule.app.ui.state.DatePager
import com.hyperschedule.app.ui.state.rememberDatePagerState
import com.hyperschedule.app.ui.state.rememberHolidayPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
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
    val density = LocalDensity.current
    // 空状态居中的文案有多高：内容被拉上来时，文案顶端到栏底还有一段看不到的距离
    var hintHeight by remember { mutableStateOf(0.dp) }
    // 当前这一页的内容顶端离栏底多远才算出材质的起点
    var materialStart by remember { mutableStateOf(TodayContentStart) }

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
    val pagePlan =
        remember(holidayPlan, pageWeek, pageDate.dayOfWeek.value) {
            pageWeek?.let { holidayPlan.of(it, pageDate.dayOfWeek.value) } ?: HolidayDay()
        }

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
        // 壁纸铺在页面外的最底层，有壁纸时底色必须透明
        containerColor = pageContainerColor(),
        topBar = {
            BlurredTopAppBar(
                title = "今日课表",
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                actions = {
                    TopBarIconButton(
                        onClick = {
                            appearanceVisible = true
                            appearanceMounted = true
                        },
                    ) {
                        Icon(MiuixIcons.Background, contentDescription = "今日课表外观")
                    }
                },
                bottomContent = {
                    DateTitleRow(
                        date = pageDate,
                        plan = pagePlan,
                        onJumpClick = {
                            dateDraft = pageDate
                            showJumpDialog = true
                        },
                    )
                },
                pinnedTitle = true,
                // 内容顶端贴着栏底，只有列表自己那段留白要先滚过去（空状态另算，见下）
                materialStart = materialStart,
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
                // 文案在内容区正中，上面那半截是空的：拉伸到文案之前不该出材质
                val emptyStart =
                    if (hintHeight > 0.dp) ((contentHeight - hintHeight) / 2).coerceAtLeast(0.dp) else 0.dp
                SideEffect {
                    if (date == dateState.date) {
                        materialStart = if (emptyDay) emptyStart else TodayContentStart
                    }
                }

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
                            modifier = Modifier.padding(top = TodayContentStart),
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
                                modifier =
                                    Modifier.onSizeChanged {
                                        hintHeight = with(density) { it.height.toDp() }
                                    },
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
                onWallpaperPick = onWallpaperPick,
                onWallpaperClear = onWallpaperClear,
                onWallpaperBlurChange = onWallpaperBlurChange,
                onWallpaperBlurCommit = onWallpaperBlurCommit,
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
                .padding(start = 28.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${dayLabel(date.dayOfWeek.value)}".cjkDigitGap(),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 日期太长时省略，别把后面的按钮和角标挤走
            modifier = Modifier.weight(1f, fill = false),
        )
        TopBarIconButton(
            onClick = onJumpClick,
            modifier = Modifier.padding(start = 6.dp),
            minWidth = 34.dp,
            minHeight = 34.dp,
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowUpDown,
                contentDescription = "跳转日期",
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
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

/** 课程列表自带的顶留白：内容要滚过这一段才真正压到栏下 */
private val TodayContentStart = 8.dp

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
