package com.hyperschedule.app.ui.page.schedule

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.hyperschedule.app.data.model.Course
import com.hyperschedule.app.data.model.CourseWithWeeks
import com.hyperschedule.app.schedule.HolidayDay
import com.hyperschedule.app.schedule.HolidayPlan
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.schedule.currentSectionAt
import com.hyperschedule.app.theme.PageBottomInset
import com.hyperschedule.app.theme.ScheduleStyle
import com.hyperschedule.app.theme.SectionSpacing
import com.hyperschedule.app.theme.WideLayoutMinWidth
import com.hyperschedule.app.ui.component.BreakDividerRow
import com.hyperschedule.app.ui.component.DAY_NAMES
import com.hyperschedule.app.ui.component.DayGridColumn
import com.hyperschedule.app.ui.component.ScheduleGridLines
import com.hyperschedule.app.ui.component.ScheduleLayout
import com.hyperschedule.app.ui.component.SectionTimesColumn
import com.hyperschedule.app.ui.component.SpecialBlockBands
import com.hyperschedule.app.ui.component.StretchOverscrollState
import com.hyperschedule.app.ui.component.TimelineDay
import com.hyperschedule.app.ui.component.TimelineWeekGrid
import com.hyperschedule.app.ui.component.WeekCourse
import com.hyperschedule.app.ui.component.markOrNull
import com.hyperschedule.app.ui.component.resolveWeekCourses
import com.hyperschedule.app.ui.component.stretchOverscroll
import com.hyperschedule.app.ui.effect.captureForBlur
import com.hyperschedule.app.ui.navigation.NavBarMetrics
import java.time.LocalDate
import java.time.LocalDateTime
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 课表的一页（一周） */
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
    // 纵向滚动与拉伸全周共用一份
    pageScroll: ScrollState,
    stretch: StretchOverscrollState,
    pendingCell: PendingCell?,
    onPendingCellChange: (PendingCell?) -> Unit,
    editor: ScheduleEditor,
    touchState: ScheduleTouchState,
    onGridPositioned: (rect: Rect) -> Unit,
    onCourseClick: (Course, Int, String?) -> Unit,
    onAddCourse: (Int, Int, Int) -> Unit,
) {
    val week = page - EdgePages + 1
    val draggingSlot = editor.draggingSlot
    // 已经有一指在拖
    val dragInProgress = editor.drag?.released == false
    // 未开学/学期已结束
    val inSemester = week in 1..totalWeeks
    val currentSection = if (week == currentWeek) currentSectionAt(sectionTimes, now.toLocalTime()) else -1
    // 7 天各自的放假/调休
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

    // 网格下方统计行
    val weekCourseCount =
        remember(dayPlans, coursesByWeek, week) {
            dayPlans.withIndex().sumOf { (index, plan) ->
                val day = plan.dayOr(index + 1)
                coursesByWeek[plan.weekOr(week)].orEmpty().count { it.active && it.course.day == day }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // 只让当前页往 backdrop 里录
                .captureForBlur(blurBackdrop.takeIf { page == pagerState.currentPage })
                .scrollEndHaptic()
                // 顶端下拉用原地拉伸
                .nestedScroll(stretch.connection)
                .verticalScroll(pageScroll, overscrollEffect = null)
                // 顶栏高度作为滚动内容的内边距
                .padding(top = topPadding),
    ) {
        if (style.timeline) {
            TimelineWeekPage(
                week = week,
                currentWeek = currentWeek,
                now = now,
                dayPlans = dayPlans,
                coursesByWeek = coursesByWeek,
                sectionTimes = sectionTimes,
                style = style,
                stretch = stretch,
                onCourseClick = onCourseClick,
            )
        } else {
            // 网格线铺在时间列之后
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
                    // 7 个日期列这块矩形
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned { coords -> onGridPositioned(coords.boundsInRoot()) },
                    ) {
                        // 特殊节次铺在课程卡后面
                        SpecialBlockBands(layout = layout, modifier = Modifier.matchParentSize())
                        Row {
                            for (day in 1..DAY_NAMES.size) {
                                val plan = dayPlans[day - 1]
                                // 补课列画被调那天的课
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
                                    // 灰卡只认源周那一张
                                    draggingId = draggingSlot?.takeIf { it.second == shownWeek }?.first,
                                    isHoliday = plan.holiday,
                                    isWorkSwap = plan.workSwap,
                                    // 补课列与拖拽中都不起拖
                                    dragEnabled = !plan.remapped && !dragInProgress,
                                    // 放假/补课列不落「+」
                                    pendingSection =
                                        if (plan.holiday || plan.remapped) {
                                            -1
                                        } else {
                                            pendingCell?.takeIf { it.day == day }?.section ?: -1
                                        },
                                    // 没开学或放补课列不给添加
                                    onCellTap =
                                        if (startDate != null && !plan.holiday && !plan.remapped) {
                                            { section -> onPendingCellChange(PendingCell(day, section)) }
                                        } else {
                                            null
                                        },
                                    // 点卡片只带一门课，顺带这一格的放假/调休角标
                                    onCourseClick = { clicked -> onCourseClick(clicked, shownWeek, plan.markOrNull()) },
                                    // 再点一次「+」直接进新建课程页
                                    onPendingTap = { section -> onAddCourse(day, section, section) },
                                    onDragStart = { course, position, pointerId ->
                                        // 起拖成功才登记指针
                                        if (editor.startDrag(week, course, position, allCourses)) {
                                            touchState.begin(pointerId, position)
                                        }
                                    },
                                )
                            }
                        }
                        // 午休/晚休分界线
                        layout.dividers.forEach { divider -> BreakDividerRow(divider = divider) }
                    }
                }
            }
        }
        // 学期外的空白周不显示统计
        if (inSemester) {
            Spacer(modifier = Modifier.height(SectionSpacing))
            Text(
                text = "本周共${weekCourseCount}节课".cjkDigitGap(),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val bottomInset =
            if (inSemester && LocalWindowInfo.current.containerDpSize.width < WideLayoutMinWidth) {
                NavBarMetrics.occupiedHeight + SectionSpacing
            } else {
                PageBottomInset
            }
        Spacer(modifier = Modifier.height(bottomInset))
    }
}

/** 时间轴视图 */
@Composable
private fun TimelineWeekPage(
    week: Int,
    currentWeek: Int?,
    now: LocalDateTime,
    dayPlans: List<HolidayDay>,
    coursesByWeek: Map<Int, List<WeekCourse>>,
    sectionTimes: List<Pair<String, String>>,
    style: ScheduleStyle,
    stretch: StretchOverscrollState,
    onCourseClick: (Course, Int, String?) -> Unit,
) {
    val isThisWeek = week == currentWeek
    val todayColumn = now.dayOfWeek.value
    // 调休当日那列画的是别的星期的课，时间线不往上面压
    val todayRemapped = dayPlans.getOrNull(todayColumn - 1)?.remapped == true
    val days =
        remember(week, dayPlans, coursesByWeek) {
            (1..DAY_NAMES.size).map { day ->
                val plan = dayPlans[day - 1]
                val shownWeek = plan.weekOr(week)
                val shownDay = plan.dayOr(day)
                TimelineDay(
                    column = day,
                    week = shownWeek,
                    courses = coursesByWeek[shownWeek].orEmpty().filter { it.course.day == shownDay },
                    isHoliday = plan.holiday,
                    isWorkSwap = plan.workSwap,
                    mark = plan.markOrNull(),
                )
            }
        }
    TimelineWeekGrid(
        days = days,
        sectionTimes = sectionTimes,
        style = style,
        nowMinute = if (isThisWeek) now.hour * 60 + now.minute else -1,
        todayColumn = if (isThisWeek && !todayRemapped) todayColumn else 0,
        onCourseClick = { course, day -> onCourseClick(course, day.week, day.mark) },
        modifier = Modifier.fillMaxWidth().stretchOverscroll(stretch),
    )
}
