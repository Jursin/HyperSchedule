package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.miuix.hyperschedule.theme.SelectCellCorner
import com.miuix.hyperschedule.theme.SelectCellFontSize
import com.miuix.hyperschedule.theme.SelectCellHeight
import com.miuix.hyperschedule.theme.SelectCellSpacing
import com.miuix.hyperschedule.theme.isDarkTheme
import com.miuix.hyperschedule.theme.selectCellIdleColor
import kotlin.math.max
import kotlin.math.min
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 周次选择网格：
 * - 单击切换该周；
 * - 按住滑动：按下格未选则把起止之间的周次并入已选，已选则把这些周次移出（已占用的周自动跳过）；
 * - 只是按住不动仍算单击。
 */
@Composable
fun WeekRangeSelectGrid(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    occupiedWeeks: Set<Int>,
    enabled: Boolean,
    onToggleWeek: (week: Int) -> Unit,
    onReplaceWeeks: (weeks: Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 6,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rows = (totalWeeks + columns - 1) / columns

    val primaryColor = MiuixTheme.colorScheme.primary
    val onSurfaceSummaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val idleColor = selectCellIdleColor()
    // 已占用格：深色比未选更明显，浅色与未选一致，只靠文字变弱区分
    val occupiedColor =
        if (isDarkTheme()) MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f) else idleColor
    val mutedTextColor =
        if (isDarkTheme()) MiuixTheme.colorScheme.onSurfaceContainerVariant else MiuixTheme.colorScheme.outline

    var gridWidth by remember { mutableIntStateOf(0) }

    // 手势协程只按这几个 key 重启；选中集用 rememberUpdatedState 取最新值，避免滑选拿到旧快照
    val selectedWeeksState = rememberUpdatedState(selectedWeeks)
    val occupiedWeeksState = rememberUpdatedState(occupiedWeeks)
    val onToggleWeekState = rememberUpdatedState(onToggleWeek)
    val onReplaceWeeksState = rememberUpdatedState(onReplaceWeeks)

    fun weekAt(pos: Offset): Int? {
        if (gridWidth <= 0) return null
        val spacingPx = with(density) { SelectCellSpacing.toPx() }
        val cellW = (gridWidth - spacingPx * (columns - 1)) / columns
        val cellH = with(density) { SelectCellHeight.toPx() }
        val col = ((pos.x + spacingPx * 0.5f) / (cellW + spacingPx)).toInt()
        val row = ((pos.y + spacingPx * 0.5f) / (cellH + spacingPx)).toInt()
        if (col !in 0 until columns || row !in 0 until rows) return null
        val week = row * columns + col + 1
        return week.takeIf { it in 1..totalWeeks }
    }

    fun rangeSelection(anchor: Int, target: Int): Set<Int> {
        val lo = min(anchor, target)
        val hi = max(anchor, target)
        return (lo..hi).filter { it !in occupiedWeeksState.value }.toSet()
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { gridWidth = it.width }
                .cellDragSelect(
                    enabled = enabled,
                    haptics = haptics,
                    hitTest = { weekAt(it) },
                    selection = { selectedWeeksState.value },
                    canStart = { it !in occupiedWeeksState.value },
                    onTap = { onToggleWeekState.value(it) },
                    onRange = { anchor, target, add, base ->
                        val range = rangeSelection(anchor, target)
                        onReplaceWeeksState.value(if (add) base + range else base - range)
                    },
                ),
        verticalArrangement = Arrangement.spacedBy(SelectCellSpacing),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SelectCellSpacing),
            ) {
                for (col in 0 until columns) {
                    val idx = row * columns + col
                    if (idx < totalWeeks) {
                        val weekNum = idx + 1
                        WeekRangeCell(
                            weekNum = weekNum,
                            isSelected = weekNum in selectedWeeks,
                            isOccupied = weekNum in occupiedWeeks,
                            enabled = enabled,
                            idleColor = idleColor,
                            occupiedColor = occupiedColor,
                            mutedTextColor = mutedTextColor,
                            primaryColor = primaryColor,
                            onSurfaceSummaryColor = onSurfaceSummaryColor,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.WeekRangeCell(
    weekNum: Int,
    isSelected: Boolean,
    isOccupied: Boolean,
    enabled: Boolean,
    idleColor: Color,
    occupiedColor: Color,
    mutedTextColor: Color,
    primaryColor: Color,
    onSurfaceSummaryColor: Color,
) {
    val bgColor =
        when {
            !enabled -> idleColor
            isSelected -> primaryColor
            isOccupied -> occupiedColor
            else -> idleColor
        }
    val textColor =
        when {
            !enabled -> mutedTextColor
            isSelected -> MiuixTheme.colorScheme.onPrimary
            isOccupied -> mutedTextColor
            else -> onSurfaceSummaryColor
        }
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(SelectCellHeight)
                .squircleClip(SelectCellCorner)
                .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "$weekNum", fontSize = SelectCellFontSize, color = textColor)
    }
}
