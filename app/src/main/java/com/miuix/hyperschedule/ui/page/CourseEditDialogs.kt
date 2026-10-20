package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.DAY_NAMES
import com.miuix.hyperschedule.ui.component.WeekRangeSelectGrid
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val MinuteValues = (0..59 step 5).toList()

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
        NumberPicker(
            value = day - 1,
            onValueChange = { day = it + 1 },
            range = 0..6,
            visibleItemCount = 5,
            itemHeight = 50.dp,
            textStyle = MiuixTheme.textStyles.title2,
            label = { DAY_NAMES[it] },
        )
    }
}

/** 上课时间：默认选节次，勾选「自定义时间」后改成时:分双滚轮（分钟 5 分钟一档） */
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

    val defaultStart =
        group.customStartTime?.takeIf { it.isNotBlank() }
            ?: sectionTimes.getOrNull(group.startSection - 1)?.first
            ?: "08:00"
    val defaultEnd =
        group.customEndTime?.takeIf { it.isNotBlank() }
            ?: sectionTimes.getOrNull(group.endSection - 1)?.second
            ?: "09:00"
    var startHour by remember { mutableIntStateOf(parseHour(defaultStart)) }
    var startMinute by remember { mutableIntStateOf(parseMinute(defaultStart)) }
    var endHour by remember { mutableIntStateOf(parseHour(defaultEnd)) }
    var endMinute by remember { mutableIntStateOf(parseMinute(defaultEnd)) }

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
                if (isCustomTime) formatTime(startHour, startMinute) else null,
                if (isCustomTime) formatTime(endHour, endMinute) else null,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = if (isCustomTime) ToggleableState.On else ToggleableState.Off,
                onClick = { isCustomTime = !isCustomTime },
                modifier = Modifier.scale(0.72f),
            )
            Text(
                text = "自定义时间",
                modifier =
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isCustomTime = !isCustomTime },
                        )
                        .padding(start = 2.dp),
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            RangeColumn(label = if (isCustomTime) "开始时间" else "开始节次", modifier = Modifier.weight(1f)) {
                if (isCustomTime) {
                    TimePickerRow(
                        hour = startHour,
                        minute = startMinute,
                        onHourChange = { startHour = it },
                        onMinuteChange = { startMinute = it },
                    )
                } else {
                    NumberPicker(
                        value = startSection,
                        onValueChange = { startSection = it },
                        range = 1..sectionCount,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        textStyle = MiuixTheme.textStyles.title2,
                        label = { "第${it}节" },
                    )
                }
            }
            RangeColumn(label = if (isCustomTime) "结束时间" else "结束节次", modifier = Modifier.weight(1f)) {
                if (isCustomTime) {
                    TimePickerRow(
                        hour = endHour,
                        minute = endMinute,
                        onHourChange = { endHour = it },
                        onMinuteChange = { endMinute = it },
                    )
                } else {
                    NumberPicker(
                        value = endSection,
                        onValueChange = { endSection = it },
                        range = startSection..sectionCount,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        textStyle = MiuixTheme.textStyles.title2,
                        label = { "第${it}节" },
                    )
                }
            }
        }
    }
}

/** 起止两端的标题 + 滚轮 */
@Composable
private fun RangeColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

/** 一组「时 : 分」滚轮，冒号与数字同字号 */
@Composable
private fun TimePickerRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        NumberPicker(
            value = hour,
            onValueChange = onHourChange,
            range = 0..23,
            visibleItemCount = 3,
            itemHeight = 60.dp,
            label = { it.toString().padStart(2, '0') },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = ":",
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onSurface,
        )
        NumberPicker(
            value = MinuteValues.indexOf(minute).coerceAtLeast(0),
            onValueChange = { onMinuteChange(MinuteValues[it]) },
            range = MinuteValues.indices,
            visibleItemCount = 3,
            itemHeight = 60.dp,
            label = { MinuteValues[it].toString().padStart(2, '0') },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.width(48.dp),
        )
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
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
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
                state = if (allSelected) ToggleableState.On else ToggleableState.Off,
                label = "全部",
                onClick = {
                    selected.clear()
                    if (!allSelected) selected.addAll(selectableWeeks)
                },
            )
            CheckLabel(
                state =
                    when {
                        allOddSelected -> ToggleableState.On
                        someOddSelected && !mixed -> ToggleableState.Indeterminate
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
                        allEvenSelected -> ToggleableState.On
                        someEvenSelected && !mixed -> ToggleableState.Indeterminate
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
        Spacer(modifier = Modifier.height(10.dp))
        WeekRangeSelectGrid(
            totalWeeks = totalWeeks,
            selectedWeeks = selected.toSet(),
            occupiedWeeks = occupied,
            enabled = true,
            isDark = isDark,
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

@Composable
private fun CheckLabel(
    state: ToggleableState,
    label: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(state = state, onClick = onClick, modifier = Modifier.scale(0.72f))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

private fun parseHour(time: String?): Int {
    if (time.isNullOrBlank()) return 8
    return time.split(":").firstOrNull()?.toIntOrNull()?.coerceIn(0, 23) ?: 8
}

/** 分钟取整到 5 的倍数，避免原时间不在候选档位时被悄悄改掉 */
private fun parseMinute(time: String?): Int {
    if (time.isNullOrBlank()) return 0
    val minute = time.split(":").getOrNull(1)?.toIntOrNull() ?: return 0
    return ((minute + 2) / 5 * 5).coerceIn(0, 55)
}

private fun formatTime(hour: Int, minute: Int): String =
    hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
