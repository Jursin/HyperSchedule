package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import com.miuix.hyperschedule.schedule.formatMinutes
import com.miuix.hyperschedule.schedule.parseMinutes
import com.miuix.hyperschedule.ui.component.CheckLabel
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.DAY_NAMES
import com.miuix.hyperschedule.ui.component.NumberWheelRow
import com.miuix.hyperschedule.ui.component.TimeWheelLabel
import com.miuix.hyperschedule.ui.component.TimeWheelRow
import com.miuix.hyperschedule.ui.component.WeekRangeSelectGrid
import com.miuix.hyperschedule.ui.page.courseedit.GroupDraft

/** 自定义时间的兜底时刻 */
private const val DefaultStartMinutes = 8 * 60
private const val DefaultEndMinutes = 9 * 60

/** 上课星期：周一~周日滚轮 */
@Composable
internal fun DayPickerDialog(
    current: Int,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var day by remember { mutableIntStateOf(current) }
    ConfirmDialog(
        title = "上课星期",
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(day) },
    ) {
        NumberWheelRow(
            value = day - 1,
            range = 0..6,
            label = { DAY_NAMES[it] },
            onValueChange = { day = it + 1 },
            wrapAround = true,
        )
    }
}

/** 上课时间：默认选节次，勾选「自定义时间」后改成时:分双滚轮 */
@Composable
internal fun TimeDialog(
    group: GroupDraft,
    sectionCount: Int,
    sectionTimes: List<Pair<String, String>>,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int, Int, String?, String?) -> Unit,
) {
    var isCustomTime by remember { mutableStateOf(group.isCustomTime) }
    var startSection by remember { mutableIntStateOf(group.startSection.coerceIn(1, sectionCount)) }
    var endSection by remember { mutableIntStateOf(group.endSection.coerceIn(startSection, sectionCount)) }

    val startSeed =
        parseMinutes(group.customStartTime?.takeIf { it.isNotBlank() })
            ?: parseMinutes(sectionTimes.getOrNull(group.startSection - 1)?.first.orEmpty())
            ?: DefaultStartMinutes
    val endSeed =
        parseMinutes(group.customEndTime?.takeIf { it.isNotBlank() })
            ?: parseMinutes(sectionTimes.getOrNull(group.endSection - 1)?.second.orEmpty())
            ?: DefaultEndMinutes
    var startHour by remember { mutableIntStateOf(startSeed / 60) }
    var startMinute by remember { mutableIntStateOf(startSeed % 60) }
    var endHour by remember { mutableIntStateOf(endSeed / 60) }
    var endMinute by remember { mutableIntStateOf(endSeed % 60) }

    LaunchedEffect(startSection) {
        if (endSection < startSection) endSection = startSection
    }

    // 自定义时间必须结束晚于开始，否则确定按钮不可用
    val timeOrderOk = !isCustomTime || startHour * 60 + startMinute < endHour * 60 + endMinute

    ConfirmDialog(
        title = "上课时间",
        appBlur = appBlur,
        confirmEnabled = timeOrderOk,
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(
                isCustomTime,
                startSection,
                endSection,
                if (isCustomTime) formatMinutes(startHour * 60 + startMinute) else null,
                if (isCustomTime) formatMinutes(endHour * 60 + endMinute) else null,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CheckLabel(
                state = if (isCustomTime) ToggleableState.On else ToggleableState.Off,
                label = "自定义时间",
                onClick = { isCustomTime = !isCustomTime },
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            SectionPickColumn(
                label = if (isCustomTime) "开始时间" else "开始节次",
                modifier = Modifier.weight(1f),
            ) {
                if (isCustomTime) {
                    TimeWheelRow(
                        hour = startHour,
                        minute = startMinute,
                        onHourChange = { startHour = it },
                        onMinuteChange = { startMinute = it },
                    )
                } else {
                    NumberWheelRow(
                        value = startSection,
                        range = 1..sectionCount,
                        label = { "第${it}节" },
                        onValueChange = { startSection = it },
                    )
                }
            }
            SectionPickColumn(
                label = if (isCustomTime) "结束时间" else "结束节次",
                modifier = Modifier.weight(1f),
            ) {
                if (isCustomTime) {
                    TimeWheelRow(
                        hour = endHour,
                        minute = endMinute,
                        onHourChange = { endHour = it },
                        onMinuteChange = { endMinute = it },
                    )
                } else {
                    NumberWheelRow(
                        value = endSection,
                        range = startSection..sectionCount,
                        label = { "第${it}节" },
                        onValueChange = { endSection = it },
                    )
                }
            }
        }
    }
}

/** 上课周次：全部 / 单周 / 双周 + 格子拖选，冲突周灰显 */
@Composable
internal fun WeeksDialog(
    initial: Set<Int>,
    totalWeeks: Int,
    occupied: Set<Int>,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
) {
    val selected = remember { mutableStateSetOf<Int>().apply { addAll(initial) } }
    val allWeeks = remember(totalWeeks) { (1..totalWeeks).toList() }
    val selectableWeeks = remember(allWeeks, occupied) { allWeeks.filter { it !in occupied } }
    val oddWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 1 } }
    val evenWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 0 } }

    val allSelected = selectableWeeks.isNotEmpty() && selectableWeeks.all { it in selected }
    val allOddSelected = oddWeeks.isNotEmpty() && oddWeeks.all { it in selected }
    val allEvenSelected = evenWeeks.isNotEmpty() && evenWeeks.all { it in selected }
    val someOddSelected = oddWeeks.any { it in selected }
    val someEvenSelected = evenWeeks.any { it in selected }
    val mixed = someOddSelected && someEvenSelected

    ConfirmDialog(
        title = "上课周次",
        appBlur = appBlur,
        confirmEnabled = selected.isNotEmpty(),
        onDismiss = onDismiss,
        onConfirm = { if (selected.isNotEmpty()) onConfirm(selected.toSet()) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckLabel(
                state =
                    when {
                        allSelected -> ToggleableState.On
                        selected.isNotEmpty() -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                label = "全部",
                onClick = {
                    selected.clear()
                    if (!allSelected) selected.addAll(selectableWeeks)
                },
            )
            CheckLabel(
                state =
                    when {
                        allOddSelected && !mixed -> ToggleableState.On
                        someOddSelected -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                label = "单周",
                onClick = {
                    val wasAll = allOddSelected && !mixed
                    selected.clear()
                    if (!wasAll) selected.addAll(oddWeeks)
                },
            )
            CheckLabel(
                state =
                    when {
                        allEvenSelected && !mixed -> ToggleableState.On
                        someEvenSelected -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                label = "双周",
                onClick = {
                    val wasAll = allEvenSelected && !mixed
                    selected.clear()
                    if (!wasAll) selected.addAll(evenWeeks)
                },
            )
        }
        WeekRangeSelectGrid(
            totalWeeks = totalWeeks,
            selectedWeeks = selected.toSet(),
            occupiedWeeks = occupied,
            enabled = true,
            onToggleWeek = { week ->
                if (week in selected) selected.remove(week) else selected.add(week)
            },
            onReplaceWeeks = { weeks ->
                selected.clear()
                selected.addAll(weeks)
            },
        )
    }
}

/** 一组「标题 + 滚轮」，与其它对话框的滚轮标签使用同一套样式 */
@Composable
private fun SectionPickColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TimeWheelLabel(label)
        content()
    }
}
