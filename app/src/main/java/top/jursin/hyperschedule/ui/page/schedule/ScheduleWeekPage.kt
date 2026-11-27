package top.jursin.hyperschedule.ui.page.schedule

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.data.db.entity.Course
import top.jursin.hyperschedule.data.db.entity.CourseWithWeeks
import top.jursin.hyperschedule.data.local.ScheduleStyle
import top.jursin.hyperschedule.schedule.HolidayDay
import top.jursin.hyperschedule.schedule.HolidayPlan
import top.jursin.hyperschedule.schedule.currentSectionAt
import top.jursin.hyperschedule.theme.SectionSpacing
import top.jursin.hyperschedule.ui.component.BackToNowOccupiedHeight
import top.jursin.hyperschedule.ui.component.CenteredPill
import top.jursin.hyperschedule.ui.component.DAY_NAMES
import top.jursin.hyperschedule.ui.component.markOrNull
import top.jursin.hyperschedule.ui.navigation.navBarBottomMargin
import java.time.LocalDate
import java.time.LocalDateTime
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 课表的一页（一周） */
@Composable
internal fun ScheduleWeekPage(
    page: Int,
    layout: ScheduleLayout,
    sectionTimes: List<Pair<String, String>>,
    style: ScheduleStyle,
    breaks: List<BreakMark>,
    startDate: LocalDate?,
    totalWeeks: Int,
    currentWeek: Int?,
    nowState: State<LocalDateTime>,
    holidayPlan: HolidayPlan,
    allCourses: List<CourseWithWeeks>,
    showNonCurrentWeek: Boolean,
    topPadding: Dp,
    // 纵向滚动全周共用一份
    pageScroll: ScrollState,
    fling: FlingBehavior,
    pendingCell: PendingCell?,
    onPendingCellChange: (PendingCell?) -> Unit,
    editor: ScheduleEditor,
    touchState: ScheduleTouchState,
    onGridPositioned: (rect: Rect) -> Unit,
    onCourseClick: (Course, Int, String?) -> Unit,
    onCourseLongPress: (Course, Int, Rect) -> Unit,
    onAddCourse: (Int, Int, Int) -> Unit,
) {
    val week = page - EdgePages + 1
    val draggingSlot = editor.draggingSlot
    // 已经有一指在拖
    val dragInProgress = editor.drag?.released == false
    // 未开学/学期已结束
    val inSemester = week in 1..totalWeeks
    val now = if (week == currentWeek) nowState.value else null
    val currentSection = now?.let { currentSectionAt(sectionTimes, it.toLocalTime()) } ?: -1
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
                .scrollEndHaptic()
                // 拉伸由外层负责，这里关闭系统 overscroll
                .verticalScroll(pageScroll, flingBehavior = fling, overscrollEffect = null)
                // 顶栏高度作为滚动内容的内边距
                .padding(top = topPadding),
    ) {
        if (style.timeline) {
            TimelineWeekPage(
                week = week,
                now = now,
                dayPlans = dayPlans,
                coursesByWeek = coursesByWeek,
                sectionTimes = sectionTimes,
                style = style,
                breaks = breaks,
                onCourseClick = onCourseClick,
                onCourseLongPress = onCourseLongPress,
            )
        } else {
            // 网格线铺在时间列之后
            Box(modifier = Modifier.fillMaxWidth()) {
                if (style.gridLines) {
                    ScheduleGridLines(layout = layout, modifier = Modifier.matchParentSize())
                }
                // 午休/晚休横线跨整行，压在最底层，课程卡盖在上面
                breaks.forEach { mark ->
                    BreakDividerOverlay(top = layout.minuteToY(mark.minute), label = mark.label)
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
                        SpecialBlockBands(
                            layout = layout,
                            margin = style.cardMargin.dp,
                            modifier = Modifier.matchParentSize(),
                        )
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
                                    onCourseLongPress = { clicked, bounds ->
                                        onCourseLongPress(clicked, shownWeek, bounds)
                                    },
                                    touchState = touchState,
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
                    }
                }
            }
        }
        // 学期外的周只隐藏文案，占位高度保持不变
        Spacer(modifier = Modifier.height(SectionSpacing))
        val countText =
            if (startDate == null) "未设置开学日期" else "本周共${weekCourseCount}节课"
        val countAlpha = if (inSemester) 1f else 0f
        CenteredPill(
            text = countText,
            pillModifier = Modifier.alpha(countAlpha),
        )
        Spacer(modifier = Modifier.height(navBarBottomMargin() + BackToNowOccupiedHeight + SectionSpacing))
    }
}

/** 时间轴视图 */
@Composable
private fun TimelineWeekPage(
    week: Int,
    now: LocalDateTime?,
    dayPlans: List<HolidayDay>,
    coursesByWeek: Map<Int, List<WeekCourse>>,
    sectionTimes: List<Pair<String, String>>,
    style: ScheduleStyle,
    breaks: List<BreakMark>,
    onCourseClick: (Course, Int, String?) -> Unit,
    onCourseLongPress: (Course, Int, Rect) -> Unit,
) {
    val todayColumn = now?.dayOfWeek?.value ?: 0
    // 调休当日那列画的是别的星期的课，时间线不往上面压
    val todayRemapped = todayColumn > 0 && dayPlans.getOrNull(todayColumn - 1)?.remapped == true
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
        breaks = breaks,
        nowMinute = now?.let { it.hour * 60 + it.minute } ?: -1,
        todayColumn = if (todayRemapped) 0 else todayColumn,
        onCourseClick = { course, day -> onCourseClick(course, day.week, day.mark) },
        onCourseLongPress = { course, day, bounds -> onCourseLongPress(course, day.week, bounds) },
    )
}
