package top.jursin.hyperschedule.ui.component

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.schedule.weekDatesOf
import top.jursin.hyperschedule.theme.TimeLabelFontSize
import top.jursin.hyperschedule.theme.isDarkTheme
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 左侧节次/刻度列宽 */
internal val SectionColumnWidth = 36.dp

/** 课表第二列（周一）的左边界 */
internal val ScheduleDayColumnStart = SectionColumnWidth

/** 线条宽度 */
internal val GridLineWidth = 0.6.dp

/** 强调线宽度 */
internal val AccentLineHeight = 1.5.dp

/** 分界线名称的高度 */
private val BreakLabelSlot = 14.dp

/** 星期行，年份 + 周一~周日 + 日期，今天蓝色加粗 */
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
    // 列宽跟随网格
    Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
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

/** 左侧节次列，特殊节次只显示起止时间，当前节次跟随主题色 */
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
            val sectionHeight = layout.sectionHeightOf(section)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(sectionHeight)
                        .offset(y = layout.topOf(section)),
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
                // 节次太矮排不下起止两行，只留开始时间
                if (sectionHeight >= 44.dp) {
                    listOf(startTime, endTime).forEach {
                        Text(text = it, style = timeStyle, color = timeColor)
                    }
                } else {
                    Text(text = startTime, style = timeStyle, color = timeColor)
                }
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

@Composable
internal fun gridLineColor(): Color {
    val isDark = isDarkTheme()
    return MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.20f else 0.07f)
}

private fun DrawScope.drawDayColumnLines(color: Color) {
    val start = SectionColumnWidth.toPx()
    val columnWidth = (size.width - start) / DAY_NAMES.size
    val stroke = GridLineWidth.toPx()
    for (day in DAY_NAMES.indices) {
        val x = start + columnWidth * day
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
    }
}

@Composable
internal fun ScheduleGridLines(layout: ScheduleLayout, modifier: Modifier = Modifier) {
    val color = gridLineColor()
    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = GridLineWidth.toPx()
        drawDayColumnLines(color)
        for (section in 1..layout.sectionCount) {
            val top = layout.topOf(section).toPx()
            drawLine(color, Offset(0f, top), Offset(size.width, top), strokeWidth = stroke)
            // 末节把底边补上，整张表才封口
            if (section == layout.sectionCount) {
                val bottom = top + layout.sectionHeightOf(section).toPx()
                drawLine(color, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = stroke)
            }
        }
    }
}

@Composable
internal fun SpecialBlockBands(
    layout: ScheduleLayout,
    margin: Dp,
    modifier: Modifier = Modifier,
) {
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
                                .padding(margin)
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

/** 主题色横线 */
@Composable
internal fun AccentLine(
    top: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    start: Dp = 0.dp,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .offset(y = top - AccentLineHeight / 2)
                .height(AccentLineHeight)
                .padding(start = start)
                .background(color),
    )
}

/** 午休/晚休分界线 */
@Composable
internal fun BreakDividerOverlay(
    top: Dp,
    label: String,
    modifier: Modifier = Modifier,
) {
    val accent = MiuixTheme.colorScheme.primary
    Box(modifier = modifier.fillMaxWidth()) {
        AccentLine(top = top, color = accent, start = SectionColumnWidth)
        Box(
            modifier =
                Modifier
                    .width(SectionColumnWidth)
                    .offset(y = top - BreakLabelSlot / 2)
                    .height(BreakLabelSlot),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = TimeLabelFontSize),
                color = accent,
                maxLines = 1,
            )
        }
    }
}