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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.theme.TimeLabelFontSize
import com.miuix.hyperschedule.theme.TodayStyle
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

/** 右侧状态文案：只有「正在上课」用强调色 */
private const val StatusOngoing = "正在上课"
private const val StatusEnded = "已结束"

/** 今日课程卡片：按上午/下午/晚上分组，行内左侧信息、右侧时间与状态 */
@Composable
fun TodayCourseList(
    courses: List<CourseWithWeeks>,
    slots: List<TimeSlot>,
    pageDate: LocalDate,
    modifier: Modifier = Modifier,
    style: TodayStyle = TodayStyle.Default,
    // 调休当天：课是从被调那天挪过来的，课名后挂一个「调」角标
    mark: String? = null,
    onCourseClick: (Course) -> Unit = {},
) {
    val slotMap = remember(slots) { slots.associateBy { it.number } }
    val now by
        produceState(LocalTime.now()) {
            while (true) {
                delay(30.seconds)
                value = LocalTime.now()
            }
        }

    val groups =
        remember(courses, slotMap) {
            listOf("上午", "下午", "晚上").mapIndexed { index, title ->
                title to courses.filter { coursePeriod(it.course, slotMap) == index }
            }
        }
    // 每行都取一次系统日期太浪费，列表算一次即可
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { (title, items) ->
            if (items.isEmpty()) return@forEach
            SectionPill(title)
            Spacer(modifier = Modifier.height(2.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    items.forEach { item ->
                        TodayCourseRow(item, slotMap, pageDate, today, now, style, mark, onCourseClick)
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
    today: LocalDate,
    now: LocalTime,
    style: TodayStyle,
    mark: String?,
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
            pageDate != today -> if (pageDate < today) StatusEnded else "未开始"
            startClock == null || endClock == null -> ""
            now.isBefore(startClock) -> "未开始"
            now.isAfter(endClock) -> StatusEnded
            else -> StatusOngoing
        }
    // 已结束的课整卡文字一起变暗（课名、信息行、时间、状态）
    val dim: (Color) -> Color =
        if (status == StatusEnded) {
            { it.copy(alpha = EndedTextAlpha) }
        } else {
            { it }
        }
    val scheme = MiuixTheme.colorScheme
    val info = remember(course, style) { infoLine(course, style) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCourseClick(course) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.name,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = dim(scheme.onSurface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 课名太长时省略而不是把角标挤出去
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 调休角标，与课表卡片同一套取色
                if (mark != null) {
                    HolidayMarkBadge(
                        mark = mark,
                        course = course,
                        fontSize = TimeLabelFontSize,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            // 字段全被关掉时不占这一行的高度
            if (info.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = info,
                    style = MiuixTheme.textStyles.body2,
                    color = dim(scheme.onSurfaceVariantSummary),
                    inlineContent = courseInfoIcons(dim(scheme.onSurfaceVariantSummary)),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = timeRange, style = MiuixTheme.textStyles.body1, color = dim(scheme.primary))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = status,
                style = MiuixTheme.textStyles.body2,
                // 状态与信息行同色，只有正在上课用强调色
                color = if (status == StatusOngoing) scheme.primary else dim(scheme.onSurfaceVariantSummary),
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
        style = MiuixTheme.textStyles.body1,
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

/** 信息行里的一个字段：图标 id、无障碍文案、显示值 */
private data class InfoField(val icon: String, val alt: String, val value: String)

/** 信息行：按外观开关筛出要显示的字段，各自带图标（与课程详情卡同一套） */
private fun infoFields(course: Course, style: TodayStyle): List<InfoField> =
    buildList {
        // 自定义时间课的「节次」就是右侧显示的那段时间，不再重复
        if (style.showSection && !course.isCustomTime) {
            add(InfoField(InfoIconId.SECTION, "节次", course.sectionLabel()))
        }
        if (style.showPosition && course.position.isNotBlank()) {
            add(InfoField(InfoIconId.POSITION, "地点", course.position))
        }
        if (style.showTeacher && course.teacher.isNotBlank()) {
            add(InfoField(InfoIconId.TEACHER, "教师", course.teacher))
        }
        course.credit?.takeIf { style.showCredit }?.let { credit ->
            add(InfoField(InfoIconId.CREDIT, "学分", "${formatCredit(credit)}学分"))
        }
        course.remark?.takeIf { style.showRemark && it.isNotBlank() }?.let { remark ->
            add(InfoField(InfoIconId.REMARK, "备注", remark))
        }
    }

private fun infoLine(course: Course, style: TodayStyle): AnnotatedString =
    buildAnnotatedString {
        infoFields(course, style).forEachIndexed { index, field ->
            if (index > 0) fieldGap()
            infoIcon(field.icon, field.alt)
            fieldValue(field.value)
        }
    }
