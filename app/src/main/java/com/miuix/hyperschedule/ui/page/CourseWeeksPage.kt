package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.weekDatesOf
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.DAY_NAMES
import com.miuix.hyperschedule.ui.component.HolidayMarkBadge
import com.miuix.hyperschedule.ui.component.InfoIconId
import com.miuix.hyperschedule.ui.component.SectionTitle
import com.miuix.hyperschedule.ui.component.courseInfoIcons
import com.miuix.hyperschedule.ui.component.dayLabel
import com.miuix.hyperschedule.ui.component.endTimeOf
import com.miuix.hyperschedule.ui.component.fieldGap
import com.miuix.hyperschedule.ui.component.fieldValue
import com.miuix.hyperschedule.ui.component.infoIcon
import com.miuix.hyperschedule.ui.component.markOrNull
import com.miuix.hyperschedule.ui.component.pageContentPadding
import com.miuix.hyperschedule.ui.component.pageScroll
import com.miuix.hyperschedule.ui.component.sectionLabel
import com.miuix.hyperschedule.ui.component.startTimeOf
import com.miuix.hyperschedule.ui.rememberHolidayPlan
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 同名课程的全部周次：每周一张卡，直接以进入时浏览的那一周作为初始位置 */
@Composable
fun CourseWeeksPage(
    courseName: String,
    week: Int,
    table: CourseTableData?,
    courses: List<CourseWithWeeks>,
    holidayRepository: HolidayRepository? = null,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val slotMap = remember(table?.slots) { table?.slots.orEmpty().associateBy { it.number } }
    val startDate =
        remember(table?.config?.semesterStartDate) {
            table?.config?.semesterStartDate?.let(::parseDateOrNull)
        }
    val totalWeeks = (table?.config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS).coerceAtLeast(1)

    // 放假 / 调休：与课表页共用同一张（周次, 星期）表，每行按自己的那一天标注
    val holidayPlan = rememberHolidayPlan(holidayRepository, startDate, totalWeeks)

    // 本课程每次出现落在哪一周的哪一天（同一天可能有多个节次）
    val placements =
        remember(courses, courseName) {
            courses
                .filter { it.course.name == courseName }
                .flatMap { item -> item.weeks.map { it.weekNumber to item.course } }
                .groupBy { (weekNumber, course) -> weekNumber * 10 + course.day }
                .mapValues { (_, list) ->
                    list.map { it.second }.sortedBy { it.startSection ?: Int.MAX_VALUE }
                }
        }
    // 覆盖到课程实际出现的最晚一周（周次可能被改到超出学期设置）
    val lastWeek =
        remember(placements, totalWeeks) {
            maxOf(totalWeeks, placements.keys.maxOrNull()?.div(10) ?: 0)
        }
    // 每周列出「这一列当天上的课」：放假照本列、补课换成被调那一天的课，与课表网格同一套规则
    val weekGroups =
        remember(placements, holidayPlan, startDate, lastWeek) {
            (1..lastWeek).mapNotNull { weekNumber ->
                val rows =
                    (1..DAY_NAMES.size).flatMap { day ->
                        val plan = holidayPlan.of(weekNumber, day)
                        val shownWeek = plan.weekOr(weekNumber)
                        val shownDay = plan.dayOr(day)
                        val mark = plan.markOrNull()
                        val date = startDate?.let { weekDatesOf(it, weekNumber).getOrNull(day - 1) }
                        placements[shownWeek * 10 + shownDay]
                            .orEmpty()
                            .map { course -> WeekRowData(date, course, mark) }
                    }
                if (rows.isEmpty()) null else weekNumber to rows
            }
        }
    // 首帧就定位到目标周，避免进入后再跳一次
    val initialIndex =
        remember(weekGroups, week) {
            weekGroups.indexOfFirst { it.first == week }.coerceIn(0, weekGroups.lastIndex.coerceAtLeast(0))
        }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    // 数据晚到（节假日还在加载）时补一次定位；之后用户自己滚动过就不再拉回
    val positioned = remember { mutableStateOf(false) }
    LaunchedEffect(weekGroups) {
        if (positioned.value || weekGroups.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(initialIndex)
        positioned.value = true
    }

    Scaffold(
        topBar = { BlurredTopAppBar(courseName, blurBackdrop, scrollBehavior, onBack = onBack) },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().pageScroll(blurBackdrop, scrollBehavior),
            contentPadding = pageContentPadding(paddingValues, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            overscrollEffect = null,
        ) {
            if (weekGroups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "没有周次数据",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            weekGroups.forEach { (weekNumber, rows) ->
                item(key = weekNumber) {
                    // 与设置页一致：标题和卡片之间也留 12dp 列间距
                    Column(verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
                        SectionTitle("第${weekNumber}周")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                rows.forEach { row ->
                                    WeekRow(
                                        course = row.course,
                                        date = row.date,
                                        slotMap = slotMap,
                                        mark = row.mark,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 本课程页的一行：某周某一天上的这门课，以及那天是放假还是补课 */
private data class WeekRowData(
    val date: LocalDate?,
    val course: Course,
    val mark: String?,
)

/** 一周里的一次课：日期 + 星期 + 节次，右侧作息时间，放假 / 调休时名称后带角标 */
@Composable
private fun WeekRow(course: Course, date: LocalDate?, slotMap: Map<Int, TimeSlot>, mark: String?) {
    val start = startTimeOf(course, slotMap)
    val end = endTimeOf(course, slotMap)
    // 自定义时间课的起止时间已在节次位置显示，右侧不再重复
    val timeRange = if (!course.isCustomTime && start != null && end != null) "$start - $end" else ""
    val dayName = dayLabel(course.day)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        listOfNotNull(
                            date?.let { "${it.monthValue}/${it.dayOfMonth}" },
                            dayName,
                            course.sectionLabel(),
                        ).joinToString(" "),
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 太长时省略，避免挤走角标
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (mark != null) {
                    HolidayMarkBadge(
                        mark = mark,
                        course = course,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (course.position.isNotBlank() || course.teacher.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = placeLine(course),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    inlineContent = courseInfoIcons(),
                )
            }
        }
        if (timeRange.isNotEmpty()) {
            Text(text = timeRange, style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.primary)
        }
    }
}

/** 地点 + 教师，沿用信息行的图标 */
private fun placeLine(course: Course): AnnotatedString =
    buildAnnotatedString {
        if (course.position.isNotBlank()) {
            infoIcon(InfoIconId.POSITION, "地点")
            fieldValue(course.position)
        }
        if (course.teacher.isNotBlank()) {
            if (course.position.isNotBlank()) fieldGap()
            infoIcon(InfoIconId.TEACHER, "教师")
            fieldValue(course.teacher)
        }
    }
