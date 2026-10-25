package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.schedule.weekDatesOf
import com.miuix.hyperschedule.theme.TimeLabelFontSize
import com.miuix.hyperschedule.theme.isDarkTheme
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 网格几何
private val SectionColumnWidth = 36.dp
private val WeekdayRowHeight = 40.dp

/** 星期行：年份 + 周一~周日 + 日期，今天蓝色加粗；未设置开学日期时只有星期名 */
@Composable
internal fun WeekdayRow(
    startDate: LocalDate?,
    week: Int,
    currentWeek: Int?,
    today: LocalDate,
    gridLines: Boolean = false,
) {
    val dates =
        remember(startDate, week) {
            if (startDate != null) weekDatesOf(startDate, week) else null
        }
    // 列宽与网格一致
    Box(modifier = Modifier.fillMaxWidth().height(WeekdayRowHeight)) {
        if (gridLines) {
            val color = gridLineColor()
            Canvas(modifier = Modifier.matchParentSize()) { drawDayColumnLines(color) }
        }
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(
                modifier = Modifier.width(SectionColumnWidth).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dates?.first()?.year?.toString().orEmpty(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
            DAY_NAMES.forEachIndexed { index, dayName ->
                val date = dates?.getOrNull(index)
                val isToday = date != null && week == currentWeek && date == today
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = dayName,
                        style =
                            MiuixTheme.textStyles.footnote1.copy(
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            ),
                        color = if (isToday) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    )
                    if (date != null) {
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            style = MiuixTheme.textStyles.footnote2,
                            color =
                                if (isToday) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                },
                        )
                    }
                }
            }
        }
    }
}

/** 左侧节次列：序号 + 起止时间三行；特殊节次只显示起止时间；当前节次整列跟随主题色 */
@Composable
internal fun SectionTimesColumn(
    layout: ScheduleLayout,
    currentSection: Int,
    sectionTimes: List<Pair<String, String>>,
) {
    val accent = MiuixTheme.colorScheme.primary
    val timeStyle = MiuixTheme.textStyles.footnote2.copy(fontSize = TimeLabelFontSize)
    Box(modifier = Modifier.width(SectionColumnWidth).height(layout.height)) {
        sectionTimes.forEachIndexed { index, (startTime, endTime) ->
            val section = index + 1
            val isCurrent = section == currentSection
            val timeColor = if (isCurrent) accent else MiuixTheme.colorScheme.onSurfaceVariantActions
            Column(
                modifier = Modifier.fillMaxWidth().height(layout.sectionHeight).offset(y = layout.topOf(section)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "$section",
                    style =
                        MiuixTheme.textStyles.body2.copy(
                            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                        ),
                    color = if (isCurrent) accent else MiuixTheme.colorScheme.onSurface,
                )
                listOf(startTime, endTime).forEach { Text(text = it, style = timeStyle, color = timeColor) }
            }
        }
        // 特殊节次没有节次编号，只标起止时间
        layout.bands.forEach { band ->
            Box(
                modifier = Modifier.width(SectionColumnWidth).height(band.height).offset(y = band.top),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    listOf(band.startTime, band.endTime).forEach {
                        Text(
                            text = it,
                            style = timeStyle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 网格线颜色 */
@Composable
private fun gridLineColor(): Color {
    val isDark = isDarkTheme()
    return MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.20f else 0.07f)
}

/** 日期列竖线 */
private fun DrawScope.drawDayColumnLines(color: Color) {
    val start = SectionColumnWidth.toPx()
    val columnWidth = (size.width - start) / DAY_NAMES.size
    for (day in DAY_NAMES.indices) {
        val x = start + columnWidth * day
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
}

/** 网格线 */
@Composable
internal fun ScheduleGridLines(layout: ScheduleLayout, modifier: Modifier = Modifier) {
    val color = gridLineColor()
    Canvas(modifier = modifier.fillMaxSize()) {
        drawDayColumnLines(color)
        for (section in 1..layout.sectionCount) {
            val top = layout.topOf(section).toPx()
            drawLine(color, Offset(0f, top), Offset(size.width, top), strokeWidth = 1f)
            // 末节把底边补上，整张表才封口
            if (section == layout.sectionCount) {
                val bottom = top + layout.sectionHeight.toPx()
                drawLine(color, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1f)
            }
        }
    }
}

/** 特殊节次横带 */
@Composable
internal fun SpecialBlockBands(layout: ScheduleLayout, modifier: Modifier = Modifier) {
    if (layout.bands.isEmpty()) return
    val background = MiuixTheme.colorScheme.secondaryContainer
    val textColor = MiuixTheme.colorScheme.onSecondaryContainer
    Box(modifier = modifier) {
        layout.bands.forEach { band ->
            Row(modifier = Modifier.fillMaxWidth().height(band.height).offset(y = band.top)) {
                var cursor = 1
                for (run in consecutiveDays(band.days)) {
                    if (run.first > cursor) {
                        Spacer(modifier = Modifier.weight((run.first - cursor).toFloat()))
                    }
                    Box(
                        modifier =
                            Modifier
                                .weight(run.count().toFloat())
                                .fillMaxHeight()
                                .padding(horizontal = CardInset, vertical = CardInset)
                                .clip(RoundedCornerShape(6.dp))
                                .background(background)
                                .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = band.name.ifBlank { "特殊节次" },
                            style = MiuixTheme.textStyles.footnote2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor,
                        )
                    }
                    cursor = run.last + 1
                }
                if (cursor <= DAY_NAMES.size) {
                    Spacer(modifier = Modifier.weight((DAY_NAMES.size + 1 - cursor).toFloat()))
                }
            }
        }
    }
}

/** 星期集合拆成连续的几段 */
private fun consecutiveDays(days: Set<Int>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = 0
    for (day in 1..DAY_NAMES.size + 1) {
        if (day <= DAY_NAMES.size && day in days) {
            if (start == 0) start = day
        } else if (start != 0) {
            runs += start until day
            start = 0
        }
    }
    return runs
}

/** 午休 / 晚休分界线：铺满日期列的一条圆角条，文字居中 */
@Composable
internal fun BreakDividerRow(divider: BreakDivider, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(DividerHeight)
                .offset(y = divider.top)
                // 与课程卡同一圈间距，左右才和卡片对齐
                .padding(horizontal = CardInset, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = divider.label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}