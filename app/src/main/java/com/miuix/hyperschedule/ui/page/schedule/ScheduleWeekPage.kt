package com.miuix.hyperschedule.ui.page.schedule

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
import com.miuix.hyperschedule.ui.component.StretchOverscrollState
import com.miuix.hyperschedule.ui.component.resolveWeekCourses
import com.miuix.hyperschedule.ui.component.stretchOverscroll
import com.miuix.hyperschedule.ui.effect.captureForBlur
import java.time.LocalDate
import java.time.LocalDateTime
import top.yukonga.miuix.kmp.blur.LayerBackdrop
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
    // 页面级触控状态
    touchState: ScheduleTouchState,
    onGridPositioned: (rect: Rect) -> Unit,
    onCourseClick: (Course, Int) -> Unit,
    onAddCourse: (Int, Int, Int) -> Unit,
) {
    val week = page - EdgePages + 1
    val draggingSlot = editor.draggingSlot
    // 已经有一指在拖
    val dragInProgress = editor.drag?.released == false
    // 未开学 / 学期已结束
    val inSemester = week in 1..totalWeeks
    val currentSection = if (week == currentWeek) currentSectionAt(sectionTimes, now.toLocalTime()) else -1
    // 7 天各自的放假 / 调休
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
                // 只让当前页往 backdrop 里录
                .captureForBlur(blurBackdrop.takeIf { page == pagerState.currentPage })
                .scrollEndHaptic()
                // 顶端下拉用原地拉伸
                .nestedScroll(stretch.connection)
                .verticalScroll(pageScroll, overscrollEffect = null)
                // 顶栏高度作为滚动内容的内边距
                .padding(top = topPadding),
    ) {
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
                                // 放假 / 补课列不落「+」
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
                                // 点卡片只带一门课
                                onCourseClick = { clicked -> onCourseClick(clicked, shownWeek) },
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
                    // 午休 / 晚休分界线
                    layout.dividers.forEach { divider -> BreakDividerRow(divider = divider) }
                }
            }
        }
        Spacer(modifier = Modifier.height(PageBottomInset))
    }
}
