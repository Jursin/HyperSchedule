package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.miuix.hyperschedule.theme.SelectCellCorner
import com.miuix.hyperschedule.theme.SelectCellFontSize
import com.miuix.hyperschedule.theme.SelectCellHeight
import com.miuix.hyperschedule.theme.SelectCellSpacing
import com.miuix.hyperschedule.theme.selectCellIdleColor
import kotlin.math.max
import kotlin.math.min
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 星期的单字写法，格子窄，只放得下一个字 */
private val WeekdayShortNames = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 星期多选格子：一行七天，点一下切换；按住滑过一串格子时，按下格未选就整段选中，已选就整段取消。
 * [selected] 为空时不会有任何格子亮起，调用方自行决定是否允许保存。
 */
@Composable
fun WeekdaySelectGrid(
    selected: Set<Int>,
    onToggle: (day: Int) -> Unit,
    onReplace: (days: Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selectedColor = MiuixTheme.colorScheme.primary
    val selectedContent = MiuixTheme.colorScheme.onPrimary
    val idleColor = selectCellIdleColor()
    val idleContent =
        if (enabled) {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        } else {
            MiuixTheme.colorScheme.outline
        }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var rowWidth by remember { mutableIntStateOf(0) }

    fun dayAt(pos: Offset): Int? {
        if (rowWidth <= 0) return null
        val gap = with(density) { SelectCellSpacing.toPx() }
        val cellWidth = (rowWidth - gap * (WeekdayShortNames.size - 1)) / WeekdayShortNames.size
        val col = ((pos.x + gap * 0.5f) / (cellWidth + gap)).toInt()
        return if (col in WeekdayShortNames.indices) col + 1 else null
    }

    fun rangeSelection(anchor: Int, target: Int): Set<Int> = (min(anchor, target)..max(anchor, target)).toSet()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidth = it.width }
                .cellDragSelect(
                    enabled = enabled,
                    haptics = haptics,
                    hitTest = { dayAt(it) },
                    selection = { selected },
                    onTap = onToggle,
                    onRange = { anchor, target, add, base ->
                        val range = rangeSelection(anchor, target)
                        onReplace(if (add) base + range else base - range)
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(SelectCellSpacing),
    ) {
        for (day in 1..WeekdayShortNames.size) {
            val isSelected = day in selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(SelectCellHeight)
                        .squircleClip(SelectCellCorner)
                        .background(if (isSelected) selectedColor else idleColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = WeekdayShortNames[day - 1],
                    fontSize = SelectCellFontSize,
                    color = if (isSelected) selectedContent else idleContent,
                )
            }
        }
    }
}

/** 星期集合 → 「每天」或「周一、周三」 */
fun formatWeekdays(days: Set<Int>): String =
    if (days.size >= DAY_NAMES.size) "每天" else days.sorted().joinToString("、", transform = ::dayLabel)
