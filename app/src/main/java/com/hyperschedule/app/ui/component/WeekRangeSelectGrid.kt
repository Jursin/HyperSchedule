package com.hyperschedule.app.ui.component

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hyperschedule.app.theme.SelectCellCorner
import com.hyperschedule.app.theme.SelectCellFontSize
import com.hyperschedule.app.theme.SelectCellHeight
import com.hyperschedule.app.theme.SelectCellSpacing
import com.hyperschedule.app.theme.subtleFillColor
import kotlin.math.max
import kotlin.math.min
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 周次选择网格，单击切换该周，按住滑动把起止之间并入或移出已选 */
@Composable
fun WeekRangeSelectGrid(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    enabled: Boolean,
    onToggleWeek: (week: Int) -> Unit,
    onReplaceWeeks: (weeks: Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 6,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rows = (totalWeeks + columns - 1) / columns

    var gridWidth by remember { mutableIntStateOf(0) }

    // 手势协程只按这几个 key 重启，选中集用 rememberUpdatedState 取最新值
    val selectedWeeksState = rememberUpdatedState(selectedWeeks)
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
                    onTap = { onToggleWeekState.value(it) },
                    onRange = { anchor, target, add, base ->
                        val range = (min(anchor, target)..max(anchor, target)).toSet()
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
                            enabled = enabled,
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
    enabled: Boolean,
) {
    val textColor =
        when {
            !enabled -> MiuixTheme.colorScheme.outline
            isSelected -> MiuixTheme.colorScheme.onPrimary
            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        }
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(SelectCellHeight)
                .squircleClip(SelectCellCorner)
                .background(if (isSelected) MiuixTheme.colorScheme.primary else subtleFillColor()),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "$weekNum", fontSize = SelectCellFontSize, color = textColor)
    }
}
