package com.miuix.hyperschedule.ui.page.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.schedule.HolidayPlan
import com.miuix.hyperschedule.schedule.currentSectionAt
import com.miuix.hyperschedule.theme.PageBottomInset
import com.miuix.hyperschedule.theme.ScheduleStyle
import com.miuix.hyperschedule.ui.component.BreakDividerRow
import com.miuix.hyperschedule.ui.component.DAY_NAMES
import com.miuix.hyperschedule.ui.component.DayGridColumn
import com.miuix.hyperschedule.ui.component.ScheduleGridLines
import com.miuix.hyperschedule.ui.component.ScheduleLayout
import com.miuix.hyperschedule.ui.component.SectionTimesColumn
import com.miuix.hyperschedule.ui.component.SpecialBlockBands
import com.miuix.hyperschedule.ui.component.rememberStretchOverscrollState
import com.miuix.hyperschedule.ui.component.resolveWeekCourses
import com.miuix.hyperschedule.ui.component.stretchOverscroll
import com.miuix.hyperschedule.ui.effect.captureForBlur
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 课表的一页（一周）：时间列 + 7 个日期列，网格线画在所有内容下面 */
@Composable
internal fun ScheduleWeekPage(
    page: Int,
    pagerState: PagerState,
    layout: ScheduleLayout,
    sectionTimes: List<Pair<String, String>>,
    style: ScheduleStyle,
    startDate: LocalDate?,
    totalWeeks: Int,
    currentWeek: Int?,
    now: LocalDateTime,
    holidayPlan: HolidayPlan,
    allCourses: List<CourseWithWeeks>,
    showNonCurrentWeek: Boolean,
    blurBackdrop: LayerBackdrop?,
    topPadding: Dp,
    pendingCell: PendingCell?,
    onPendingCellChange: (PendingCell?) -> Unit,
    editor: ScheduleEditor,
    dayColumnsBounds: MutableMap<Int, Rect>,
    onCourseClick: (Course, Int) -> Unit,
    onAddCourse: (Int, Int, Int) -> Unit,
) {
    val week = page - EdgePages + 1
    // 每页一套滚动 + 拉伸：左右邻页各滚各的，下拉的手感也不串
    val pageScroll = rememberScrollState()
    val stretch = rememberStretchOverscrollState()
    // 手指抬起（或回弹结束）就弹回去；新的下拉会直接打断这段动画
    LaunchedEffect(pageScroll, stretch) {
        snapshotFlow { pageScroll.isScrollInProgress }
            .collectLatest { scrolling -> if (!scrolling) stretch.settle() }
    }
    // 未开学 / 学期已结束：网格照常显示，只是不放任何课程
    val inSemester = week in 1..totalWeeks
    val currentSection = if (week == currentWeek) currentSectionAt(sectionTimes, now.toLocalTime()) else -1
    // 这 7 天各自的放假 / 调休结论：调休列要看被调那一天的课，可能落在别的周
    val dayPlans =
        remember(holidayPlan, week) {
            List(DAY_NAMES.size) { index -> holidayPlan.of(week, index + 1) }
        }
    val coursesByWeek =
        remember(allCourses, week, dayPlans, inSemester, showNonCurrentWeek, totalWeeks) {
            if (!inSemester) {
                emptyMap()
            } else {
                // 补课列取的是别的周的课，一并预取
                (listOf(week) + dayPlans.map { it.displayWeek })
                    .filter { it in 1..totalWeeks }
                    .distinct()
                    .associateWith { resolveWeekCourses(allCourses, it, showNonCurrentWeek) }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // 只让当前页往 backdrop 里录：一个 backdrop 只有一个采集点，多页同录会互相覆盖
                // （顶栏若圈进来，模糊面会采样到自己 → 原生崩溃）
                .captureForBlur(blurBackdrop.takeIf { page == pagerState.currentPage })
                .scrollEndHaptic()
                // 顶端下拉用原地拉伸（StretchOverscroll）代替默认 overscroll：
                // 顶栏为固定层，内容整体位移会和它露缝
                .nestedScroll(stretch.connection)
                .verticalScroll(pageScroll, overscrollEffect = null)
                // 顶栏高度作为滚动内容的内边距：网格才能滚到顶栏下方，顶栏有东西可模糊
                .padding(top = topPadding),
    ) {
        // 网格线要连左侧时间列一起画，所以铺在这一层的后面
        // 顶端下拉时整块网格以顶边为锚点纵向拉伸：上沿不动，不会和星期行之间裂开
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .stretchOverscroll(stretch),
        ) {
            if (style.gridLines) {
                ScheduleGridLines(layout = layout, modifier = Modifier.matchParentSize())
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                SectionTimesColumn(
                    layout = layout,
                    currentSection = currentSection,
                    sectionTimes = sectionTimes,
                )
                // 7 个日期列这块矩形，页面据它判断点击是否落在网格外
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                dayColumnsBounds[page] = coords.boundsInRoot()
                            },
                ) {
                    // 特殊节次作为背景带铺在课程卡后面
                    SpecialBlockBands(layout = layout, modifier = Modifier.matchParentSize())
                    Row {
                        for (day in 1..DAY_NAMES.size) {
                            val plan = dayPlans[day - 1]
                            // 补课列画被调那一天的课；放假列照旧画本列的课，只是灰显
                            val shownWeek = plan.weekOr(week)
                            val shownDay = plan.dayOr(day)
                            DayGridColumn(
                                modifier = Modifier.weight(1f),
                                layout = layout,
                                courses =
                                    coursesByWeek[shownWeek].orEmpty()
                                        .filter { it.course.day == shownDay },
                                sectionTimes = sectionTimes,
                                style = style,
                                isHoliday = plan.holiday,
                                isWorkSwap = plan.workSwap,
                                // 补课列里拖课会算错列（课属于别的星期），直接禁掉
                                dragEnabled = !plan.remapped,
                                // 放假 / 补课列不落「+」
                                pendingSection =
                                    if (plan.holiday || plan.remapped) {
                                        -1
                                    } else {
                                        pendingCell?.takeIf { it.day == day }?.section ?: -1
                                    },
                                // 没设开学日期、或放假 / 补课列，都不给添加入口
                                onCellTap =
                                    if (startDate != null && !plan.holiday && !plan.remapped) {
                                        { section -> onPendingCellChange(PendingCell(day, section)) }
                                    } else {
                                        null
                                    },
                                // 点卡片只带出一门课，抽屉再按「同一格」把该格所有课程取出来
                                onCourseClick = { clicked -> onCourseClick(clicked, shownWeek) },
                                // 再点一次「+」直接进新建课程页
                                onPendingTap = { section -> onAddCourse(day, section, section) },
                                // 拖动中的卡片原地按「本周不上」显示，由浮层代替它跟手
                                draggingCourse = editor.draggingCourse,
                                onDragStart = { course, position ->
                                    editor.startDrag(page, course, position, allCourses, dayColumnsBounds)
                                },
                                onDrag = { position -> editor.updateDrag(position) },
                                onDragEnd = { editor.finishDrag(allCourses) },
                            )
                        }
                    }
                    // 午休 / 晚休分界线：占一节之外的 24dp 缝，与网格共用同一套坐标
                    layout.dividers.forEach { divider -> BreakDividerRow(divider = divider) }
                }
            }
        }
        Spacer(modifier = Modifier.height(PageBottomInset))
    }
}
