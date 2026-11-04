package com.hyperschedule.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.model.Course
import com.hyperschedule.app.schedule.formatMinutes
import com.hyperschedule.app.schedule.parseMinutes
import com.hyperschedule.app.theme.ScheduleStyle
import com.hyperschedule.app.theme.TimeLabelFontSize
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 极短的课也要点得中 */
private val MinCardHeight = 22.dp

/** 时间轴的疏密 */
private val MinuteHeight = 2.dp

/** 整点刻度占的高度 */
private val HourLabelSlot = 16.dp

/** 时间轴的一列 */
internal data class TimelineDay(
    val column: Int,
    val week: Int,
    val courses: List<WeekCourse>,
    val isHoliday: Boolean = false,
    val isWorkSwap: Boolean = false,
    val mark: String? = null,
)

/** 时间轴版周页 */
@Composable
internal fun TimelineWeekGrid(
    days: List<TimelineDay>,
    sectionTimes: List<Pair<String, String>>,
    style: ScheduleStyle,
    /** 当前时刻，不在窗口内就不画那条线 */
    nowMinute: Int,
    /** 画当前时刻线的列（1..7），0 表示不画 */
    todayColumn: Int,
    onCourseClick: (Course, TimelineDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val axis = remember(sectionTimes) { buildTimelineAxis(sectionTimes) }
    val lineColor = gridLineColor()
    val tickColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val tickStyle = MiuixTheme.textStyles.footnote2.copy(fontSize = TimeLabelFontSize)
    val showNow = todayColumn != 0 && nowMinute in axis.startMinute until axis.endMinute
    // 正好整点时直接给那个刻度着色，不再另画一条当前时刻
    val nowAtHour = showNow && nowMinute % 60 == 0
    val accent = MiuixTheme.colorScheme.primary

    Row(modifier = modifier.fillMaxWidth()) {
        // 整点刻度
        Box(modifier = Modifier.width(SectionColumnWidth).height(axis.height)) {
            for (hour in axis.startMinute / 60..axis.endMinute / 60) {
                val minute = hour * 60
                if (minute > axis.endMinute) break
                HourLabel(
                    text = formatMinutes(minute),
                    slotTop = axis.slotTopOf(minute),
                    style = tickStyle,
                    color = if (showNow && minute == nowMinute) accent else tickColor,
                )
            }
            if (showNow && !nowAtHour) {
                HourLabel(
                    text = formatMinutes(nowMinute),
                    slotTop = axis.nowSlotTop(nowMinute),
                    style = tickStyle,
                    color = accent,
                )
            }
        }
        Box(modifier = Modifier.weight(1f).height(axis.height)) {
            // 横线按整点，竖线按天
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawHourLines(axis, lineColor)
                drawDayLines(days.size, lineColor)
            }
            Row(modifier = Modifier.fillMaxSize()) {
                days.forEach { day ->
                    TimelineDayColumn(
                        day = day,
                        axis = axis,
                        sectionTimes = sectionTimes,
                        style = style,
                        nowMinute = nowMinute,
                        showNow = showNow && day.column == todayColumn,
                        onCourseClick = onCourseClick,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** 刻度文字，给的是整个槽的顶边：[HourLabelSlot] 一格，文字在槽里垂直居中 */
@Composable
private fun HourLabel(
    text: String,
    slotTop: Dp,
    style: TextStyle,
    color: Color,
) {
    Box(
        modifier = Modifier.offset(y = slotTop).height(HourLabelSlot).fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(end = 3.dp),
        )
    }
}

/** 一列时间轴 */
@Composable
private fun TimelineDayColumn(
    day: TimelineDay,
    axis: TimelineAxis,
    sectionTimes: List<Pair<String, String>>,
    style: ScheduleStyle,
    nowMinute: Int,
    showNow: Boolean,
    onCourseClick: (Course, TimelineDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        day.courses
            .mapNotNull { item -> minuteRangeOf(item.course, sectionTimes)?.let { item to it } }
            .groupBy { it.second.first }
            .forEach { (start, group) ->
                val ranges = group.associate { it.first to it.second }
                // 和网格视图同一套卡片行，左右留白一致
                CourseCardRow(
                    items = group.map { it.first },
                    style = style,
                    heightOf = { item ->
                        val range = ranges.getValue(item)
                        axis.heightOf(range.second - range.first).coerceAtLeast(MinCardHeight)
                    },
                    isCurrentWeek = { item -> item.active },
                    isHoliday = day.isHoliday,
                    isWorkSwap = day.isWorkSwap,
                    showBadge = { item -> item.hiddenCount > 0 },
                    onClick = { item -> onCourseClick(item.course, day) },
                    modifier = Modifier.offset(y = axis.topOf(start)),
                )
            }
        if (showNow) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset(y = axis.topOf(nowMinute))
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.85f)),
            )
        }
    }
}

/** 一节课在当天占的分钟区间，自定义时间课按自身起止，标准课按作息表；解析不出来就不上轴 */
private fun minuteRangeOf(course: Course, sectionTimes: List<Pair<String, String>>): Pair<Int, Int>? {
    if (course.isCustomTime) {
        val start = parseMinutes(course.customStartTime) ?: return null
        val end = parseMinutes(course.customEndTime) ?: return null
        return (start to end).takeIf { end > start }
    }
    val startSection = course.startSection ?: return null
    val first = sectionTimes.getOrNull(startSection - 1) ?: return null
    val last = sectionTimes.getOrNull((course.endSection ?: startSection) - 1) ?: return null
    val start = parseMinutes(first.first) ?: return null
    val end = parseMinutes(last.second) ?: return null
    return (start to end).takeIf { end > start }
}

/** 时间轴的纵向刻度 */
internal class TimelineAxis(
    val startMinute: Int,
    val endMinute: Int,
) {
    val height: Dp get() = MinuteHeight * (endMinute - startMinute).toFloat()

    fun topOf(minute: Int): Dp = MinuteHeight * (minute - startMinute).toFloat()

    fun heightOf(minutes: Int): Dp = MinuteHeight * minutes.toFloat()

    /** 刻度槽的顶边 */
    fun slotTopOf(minute: Int): Dp {
        val top = topOf(minute)
        return if (top < HourLabelSlot / 2) top else top - HourLabelSlot / 2
    }

    /** 当前时刻的刻度槽，离整点太近就紧贴整点文字的上/下方，两者不覆盖 */
    fun nowSlotTop(now: Int): Dp {
        val above = now / 60 * 60
        val below = above + 60
        return when {
            heightOf(now - above) < HourLabelSlot -> slotTopOf(above) + HourLabelSlot
            // 末端那个整点没画时不用避让
            below <= endMinute && heightOf(below - now) < HourLabelSlot -> slotTopOf(below) - HourLabelSlot
            else -> topOf(now) - HourLabelSlot / 2
        }
    }
}

/** 纵向范围只由作息决定 */
internal fun buildTimelineAxis(sectionTimes: List<Pair<String, String>>): TimelineAxis {
    val ranges =
        sectionTimes.mapNotNull { (start, end) ->
            val from = parseMinutes(start) ?: return@mapNotNull null
            val to = parseMinutes(end) ?: return@mapNotNull null
            (from to to).takeIf { to > from }
        }
    // 作息里取不出时间也要有刻度，否则页面一片空白
    val from = ranges.minOfOrNull { it.first } ?: (8 * 60)
    val to = ranges.maxOfOrNull { it.second } ?: (18 * 60)
    val start = from / 60 * 60
    // 末端补到下一个整点，末节的收尾线才有对应的刻度文字
    val end = ((to + 59) / 60 * 60).coerceAtMost(24 * 60)
    return TimelineAxis(start, end)
}

/** 整点横线 */
private fun DrawScope.drawHourLines(axis: TimelineAxis, color: Color) {
    for (hour in axis.startMinute / 60..axis.endMinute / 60) {
        val minute = hour * 60
        if (minute > axis.endMinute) break
        val y = axis.topOf(minute).toPx().coerceIn(0.5f, size.height - 0.5f)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

/** 每列左缘一条竖线，与上方的星期行、网格视图对齐 */
private fun DrawScope.drawDayLines(columns: Int, color: Color) {
    if (columns <= 0) return
    val columnWidth = size.width / columns
    for (index in 0 until columns) {
        // 首列的线落在画布左缘，收半像素才不会被裁掉一半
        val x = (columnWidth * index).coerceIn(0.5f, size.width - 0.5f)
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
}
