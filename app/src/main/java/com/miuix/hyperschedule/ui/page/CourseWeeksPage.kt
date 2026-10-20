package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.weekDatesOf
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.InfoIconId
import com.miuix.hyperschedule.ui.component.courseInfoIcons
import com.miuix.hyperschedule.ui.component.dayLabel
import com.miuix.hyperschedule.ui.component.endTimeOf
import com.miuix.hyperschedule.ui.component.fieldGap
import com.miuix.hyperschedule.ui.component.fieldValue
import com.miuix.hyperschedule.ui.component.infoIcon
import com.miuix.hyperschedule.ui.component.sectionLabel
import com.miuix.hyperschedule.ui.component.startTimeOf
import com.miuix.hyperschedule.ui.theme.captureForBlur
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 同名课程的全部周次：每周一张卡，直接以进入时浏览的那一周作为初始位置 */
@Composable
fun CourseWeeksPage(
    courseName: String,
    week: Int,
    table: CourseTableData?,
    courses: List<CourseWithWeeks>,
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

    // 同名课程可能分布在多个星期，按教学周合并成组；组内按星期、节次排
    val weekGroups =
        remember(courses, courseName) {
            courses
                .filter { it.course.name == courseName }
                .flatMap { item -> item.weeks.map { it.weekNumber to item.course } }
                .sortedWith(
                    compareBy({ it.first }, { it.second.day }, { it.second.startSection ?: Int.MAX_VALUE })
                )
                .groupBy { it.first }
                .toList()
                .sortedBy { it.first }
        }
    // 首帧就定位到目标周，避免进入后再跳一次
    val initialIndex =
        remember(weekGroups, week) {
            weekGroups.indexOfFirst { it.first == week }.coerceIn(0, weekGroups.lastIndex.coerceAtLeast(0))
        }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    LaunchedEffect(weekGroups) {
        if (listState.firstVisibleItemIndex != initialIndex) listState.scrollToItem(initialIndex)
    }

    Scaffold(
        topBar = { BlurredTopAppBar(courseName, blurBackdrop, scrollBehavior, onBack = onBack) },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .captureForBlur(blurBackdrop)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    top = paddingValues.calculateTopPadding() + 4.dp,
                    end = 12.dp,
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            if (weekGroups.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "没有周次数据",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            weekGroups.forEach { (weekNumber, entries) ->
                item(key = weekNumber) {
                    // 与设置页一致：标题和卡片之间也留 12dp 列间距
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallTitle(
                            text = "第${weekNumber}周",
                            insideMargin = PaddingValues(16.dp, 8.dp, 16.dp, 0.dp),
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                entries.forEach { (_, course) ->
                                    WeekRow(
                                        course = course,
                                        date = startDate?.let { weekDatesOf(it, weekNumber).getOrNull(course.day - 1) },
                                        slotMap = slotMap,
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

/** 一周里的一次课：日期 + 星期 + 节次，右侧作息时间 */
@Composable
private fun WeekRow(course: Course, date: LocalDate?, slotMap: Map<Int, TimeSlot>) {
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
            Text(
                text =
                    listOfNotNull(
                        date?.let { "${it.monthValue}/${it.dayOfMonth}" },
                        dayName,
                        course.sectionLabel(),
                    ).joinToString(" "),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (course.position.isNotBlank() || course.teacher.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = placeLine(course),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    inlineContent = courseInfoIcons(),
                )
            }
        }
        if (timeRange.isNotEmpty()) {
            Text(text = timeRange, fontSize = 15.sp, color = MiuixTheme.colorScheme.primary)
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
