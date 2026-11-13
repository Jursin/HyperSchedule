package com.hyperschedule.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperschedule.app.schedule.formatDate
import com.hyperschedule.app.theme.ContainerTintAlpha
import com.hyperschedule.app.theme.successColor
import com.hyperschedule.app.theme.successContainerColor
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CellHeight = 40.dp
private val MarkSize = 34.dp
private val CalendarPadding = 12.dp
private val ChipBorderWidth = 1.5.dp
private val ChipPlaceholderFontSize = 11.sp
private val ChipLineHeight = 17.sp
private const val DATE_WIDTH_TEMPLATE = "0000/00/00"

/** 区间日历，点第一下是起点，点第二下完成 */
@Composable
fun DateRangeCalendar(
    startDate: LocalDate,
    endDate: LocalDate,
    onRangeChange: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    yearRange: IntRange = DatePickerYearRange,
) {
    var viewYear by remember { mutableIntStateOf(startDate.year) }
    var viewMonth by remember { mutableIntStateOf(startDate.monthValue) }
    var pendingStart by remember { mutableStateOf<LocalDate?>(null) }
    val scheme = MiuixTheme.colorScheme

    CalendarFrame(
        year = viewYear,
        month = viewMonth,
        onShiftMonth = { delta ->
            shiftMonth(viewYear, viewMonth, delta, yearRange)?.let { (y, m) ->
                viewYear = y
                viewMonth = m
            }
        },
        modifier = modifier,
    ) { cellYear, cellMonth ->
        MonthGrid(
            year = cellYear,
            month = cellMonth,
            styleOf = { date ->
                val band = scheme.primary.copy(alpha = 0.12f)
                when {
                    date == startDate && date == endDate -> DayStyle(fill = scheme.primary, content = scheme.onPrimary)
                    date == startDate -> DayStyle(
                        band = band,
                        bandHalf = BandHalf.Trailing,
                        fill = scheme.primary,
                        content = scheme.onPrimary,
                    )
                    date == endDate -> DayStyle(
                        band = band,
                        bandHalf = BandHalf.Leading,
                        fill = scheme.primary,
                        content = scheme.onPrimary,
                    )
                    date > startDate && date < endDate -> DayStyle(
                        band = band,
                        bandHalf = BandHalf.Full,
                        content = scheme.primary,
                    )
                    else -> DayStyle(content = scheme.onSurface)
                }
            },
            onDayClick = { date ->
                val start = pendingStart
                if (start == null) {
                    pendingStart = date
                    onRangeChange(date, date)
                } else {
                    onRangeChange(minOf(start, date), maxOf(start, date))
                    pendingStart = null
                }
            },
        )
    }
}

/** 双色双日期日历，一次选两个互不相关的日期，点底部胶囊切换下一次改哪一侧 */
@Composable
fun DualDateCalendar(
    primaryDate: LocalDate,
    secondaryDate: LocalDate?,
    onPrimaryChange: (LocalDate) -> Unit,
    onSecondaryChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    yearRange: IntRange = DatePickerYearRange,
    primaryColor: Color = MiuixTheme.colorScheme.primary,
    secondaryColor: Color = successColor(),
    startWithSecondary: Boolean = false,
) {
    var viewYear by remember { mutableIntStateOf(primaryDate.year) }
    var viewMonth by remember { mutableIntStateOf(primaryDate.monthValue) }
    var editingPrimary by remember { mutableStateOf(!startWithSecondary) }
    val scheme = MiuixTheme.colorScheme

    CalendarFrame(
        year = viewYear,
        month = viewMonth,
        onShiftMonth = { delta ->
            shiftMonth(viewYear, viewMonth, delta, yearRange)?.let { (y, m) ->
                viewYear = y
                viewMonth = m
            }
        },
        modifier = modifier,
        footer = {
            DualDateRoleRow(
                primaryDate = primaryDate,
                secondaryDate = secondaryDate,
                editingPrimary = editingPrimary,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                onSelectPrimary = { editingPrimary = true },
                onSelectSecondary = { editingPrimary = false },
            )
        },
    ) { cellYear, cellMonth ->
        MonthGrid(
            year = cellYear,
            month = cellMonth,
            styleOf = { date ->
                when (date) {
                    primaryDate -> DayStyle(fill = primaryColor, content = Color.White)
                    secondaryDate -> DayStyle(fill = secondaryColor, content = Color.White)
                    else -> DayStyle(content = scheme.onSurface)
                }
            },
            onDayClick = { date ->
                if (editingPrimary) {
                    onPrimaryChange(date)
                    if (secondaryDate == null) editingPrimary = false
                } else {
                    onSecondaryChange(date)
                }
            },
        )
    }
}

/** 翻月，超出范围时不移动，返回新的年月 */
private fun shiftMonth(
    year: Int,
    month: Int,
    delta: Int,
    yearRange: IntRange,
): Pair<Int, Int>? {
    var targetYear = year
    var targetMonth = month + delta
    if (targetMonth < 1) {
        targetMonth = 12
        targetYear -= 1
    }
    if (targetMonth > 12) {
        targetMonth = 1
        targetYear += 1
    }
    return if (targetYear in yearRange) targetYear to targetMonth else null
}

/** 底部两个日期胶囊 */
@Composable
private fun DualDateRoleRow(
    primaryDate: LocalDate,
    secondaryDate: LocalDate?,
    editingPrimary: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    onSelectPrimary: () -> Unit,
    onSelectSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateRoleChip(
            label = formatDate(primaryDate),
            color = primaryColor,
            containerColor = primaryColor.copy(alpha = ContainerTintAlpha),
            active = editingPrimary,
            onClick = onSelectPrimary,
        )
        Text(
            text = "补",
            modifier = Modifier.padding(horizontal = 8.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        DateRoleChip(
            label = secondaryDate?.let(::formatDate) ?: "选择补课日",
            color = secondaryColor,
            containerColor = successContainerColor(),
            active = !editingPrimary,
            onClick = onSelectSecondary,
            widthTemplate = DATE_WIDTH_TEMPLATE,
            fontSize = if (secondaryDate == null) ChipPlaceholderFontSize else 13.sp,
        )
    }
}

/** 两个胶囊同一套样式，只有文字与底色不同，正在编辑的一侧加一圈同色描边 */
@Composable
private fun DateRoleChip(
    label: String,
    color: Color,
    containerColor: Color,
    active: Boolean,
    onClick: () -> Unit,
    widthTemplate: String? = null,
    fontSize: TextUnit = 13.sp,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(containerColor)
            .then(if (active) Modifier.border(ChipBorderWidth, color, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (widthTemplate != null) {
            Text(
                text = widthTemplate,
                modifier = Modifier.alpha(0f),
                maxLines = 1,
                fontSize = 13.sp,
                lineHeight = ChipLineHeight,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = label,
            maxLines = 1,
            fontSize = fontSize,
            lineHeight = ChipLineHeight,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

private enum class BandHalf { None, Leading, Trailing, Full }

private data class DayStyle(
    val band: Color? = null,
    val bandHalf: BandHalf = BandHalf.None,
    val fill: Color? = null,
    val content: Color,
)

/** 卡片外框 */
@Composable
private fun CalendarFrame(
    year: Int,
    month: Int,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    monthContent: @Composable (year: Int, month: Int) -> Unit,
) {
    Column(
        modifier = modifier.padding(CalendarPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthNavButton(
                imageVector = MiuixIcons.ChevronBackward,
                contentDescription = "上一月",
                onClick = { onShiftMonth(-1) },
            )
            Text(
                text = "${year}年${month}月",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            MonthNavButton(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "下一月",
                onClick = { onShiftMonth(1) },
            )
        }

        // 翻月时日期格连同星期头一起平移，标题不跟着抖
        AnimatedContent(
            targetState = year * 12 + (month - 1),
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                val forward = targetState > initialState
                val slide = tween<IntOffset>(durationMillis = 240, easing = FastOutSlowInEasing)
                val fade = tween<Float>(durationMillis = 200)
                if (forward) {
                    (slideInHorizontally(slide) { it } + fadeIn(fade)) togetherWith
                        (slideOutHorizontally(slide) { -it } + fadeOut(fade))
                } else {
                    (slideInHorizontally(slide) { -it } + fadeIn(fade)) togetherWith
                        (slideOutHorizontally(slide) { it } + fadeOut(fade))
                }
            },
            contentAlignment = Alignment.TopCenter,
            label = "calendar-month",
        ) { monthKey ->
            monthContent(monthKey / 12, monthKey % 12 + 1)
        }

        footer()
    }
}

@Composable
private fun MonthNavButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(40.dp).squircleClip(20.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    styleOf: (LocalDate) -> DayStyle,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstOfMonth = LocalDate.of(year, month, 1)
    val leading = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val rowCount = (leading + daysInMonth + 6) / 7

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DAY_NAMES.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(rowCount) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day = row * 7 + column - leading + 1
                        if (day in 1..daysInMonth) {
                            val date = LocalDate.of(year, month, day)
                            CalendarDayCell(
                                date = date,
                                style = styleOf(date),
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(date) },
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    style: DayStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(CellHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val band = style.band
        if (band != null) {
            val base = Modifier.fillMaxHeight().padding(vertical = 3.dp).background(band)
            when (style.bandHalf) {
                BandHalf.Full -> Box(base.fillMaxWidth())
                BandHalf.Trailing -> Box(base.fillMaxWidth(0.5f).align(Alignment.CenterEnd))
                BandHalf.Leading -> Box(base.fillMaxWidth(0.5f).align(Alignment.CenterStart))
                BandHalf.None -> Unit
            }
        }

        Box(
            modifier = Modifier
                .size(MarkSize)
                .squircleClip(MarkSize / 2)
                .background(style.fill ?: Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (style.fill != null) FontWeight.Medium else FontWeight.Normal,
                color = style.content,
            )
        }
    }
}
