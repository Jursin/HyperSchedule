package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.TimeSlot
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MorningEndHour = 12
private const val AfternoonEndHour = 18

/** 已结束课程整卡文字的透明度 */
private const val EndedTextAlpha = 0.45f

/** 今日课程卡片：按上午/下午/晚上分组，行内左侧信息、右侧时间与状态 */
@Composable
fun TodayCourseList(
    courses: List<CourseWithWeeks>,
    slots: List<TimeSlot>,
    pageDate: LocalDate,
    modifier: Modifier = Modifier,
    onCourseClick: (Course) -> Unit = {},
) {
    val slotMap = slots.associateBy { it.number }
    val now by
        produceState(LocalTime.now()) {
            while (true) {
                delay(30.seconds)
                value = LocalTime.now()
            }
        }

    val groups =
        listOf("上午", "下午", "晚上").mapIndexed { index, title ->
            title to courses.filter { coursePeriod(it.course, slotMap) == index }
        }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { (title, items) ->
            if (items.isEmpty()) return@forEach
            SectionPill(title)
            Spacer(modifier = Modifier.height(2.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    items.forEach { item ->
                        TodayCourseRow(item, slotMap, pageDate, now, onCourseClick)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TodayCourseRow(
    item: CourseWithWeeks,
    slotMap: Map<Int, TimeSlot>,
    pageDate: LocalDate,
    now: LocalTime,
    onCourseClick: (Course) -> Unit,
) {
    val course = item.course
    val startTime = startTimeOf(course, slotMap)
    val endTime = endTimeOf(course, slotMap)
    val startClock = startTime?.let(::parseTime)
    val endClock = endTime?.let(::parseTime)
    val timeRange =
        if (startTime != null && endTime != null) {
            "$startTime - $endTime"
        } else {
            course.sectionLabel()
        }
    val status =
        when {
            pageDate != LocalDate.now() -> if (pageDate < LocalDate.now()) "已结束" else "未开始"
            startClock == null || endClock == null -> ""
            now.isBefore(startClock) -> "未开始"
            now.isAfter(endClock) -> "已结束"
            else -> "正在上课"
        }
    // 已结束的课整卡文字一起变暗（课名、信息行、时间、状态）
    val dim: (Color) -> Color =
        if (status == "已结束") {
            { it.copy(alpha = EndedTextAlpha) }
        } else {
            { it }
        }
    val scheme = MiuixTheme.colorScheme

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCourseClick(course) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = dim(scheme.onSurface),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = infoLine(course),
                fontSize = 14.sp,
                color = dim(scheme.onSurfaceVariantSummary),
                inlineContent = courseInfoIcons(dim(scheme.onSurfaceVariantSummary)),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = timeRange, fontSize = 15.sp, color = dim(scheme.primary))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = status,
                fontSize = 14.sp,
                // 状态与信息行同色，只有正在上课用强调色
                color = if (status == "正在上课") scheme.primary else dim(scheme.onSurfaceVariantSummary),
            )
        }
    }
}

@Composable
internal fun SectionPill(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val cardColor = CardDefaults.defaultColors().color
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = textAlign,
        modifier =
            modifier
                .padding(vertical = 6.dp)
                .background(color = cardColor, shape = RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 0 = 上午，1 = 下午，2 = 晚上 */
private fun coursePeriod(course: Course, slotMap: Map<Int, TimeSlot>): Int {
    val start = startTimeOf(course, slotMap)?.let(::parseTime)
    if (start != null) {
        return when {
            start.hour < MorningEndHour -> 0
            start.hour < AfternoonEndHour -> 1
            else -> 2
        }
    }
    val section = course.startSection ?: return 0
    return when {
        section <= 4 -> 0
        section <= 8 -> 1
        else -> 2
    }
}

/** 信息行：节次 + 地点 + 教师，各自带图标（与课程详情卡同一套） */
private fun infoLine(course: Course): AnnotatedString =
    buildAnnotatedString {
        infoIcon(InfoIconId.SECTION, "节次")
        fieldValue(course.sectionLabel())
        if (course.position.isNotBlank()) {
            fieldGap()
            infoIcon(InfoIconId.POSITION, "地点")
            fieldValue(course.position)
        }
        if (course.teacher.isNotBlank()) {
            fieldGap()
            infoIcon(InfoIconId.TEACHER, "教师")
            fieldValue(course.teacher)
        }
    }
