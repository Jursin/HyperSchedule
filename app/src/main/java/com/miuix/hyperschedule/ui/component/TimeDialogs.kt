package com.miuix.hyperschedule.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.miuix.hyperschedule.schedule.formatMinutes
import com.miuix.hyperschedule.schedule.parseMinutes

private const val LAST_MINUTE = 23 * 60 + 59

/** 不联动结束时间时，结束时间的默认跨度 */
private const val DefaultSpanMinutes = 60

/**
 * 两个时间点并排的对话框：节次时间、时段分隔、特殊课程时间都用它。
 *
 * [durationMinutes] 非空表示「改开始时间时结束时间跟着走」（节次时间），为 null 时两端各改各的（时段分隔）。
 * [conflictRanges] 是其它已占用的时间段，命中则确定按钮不可用。
 */
@Composable
fun TimeRangeDialog(
    title: String,
    start: String,
    end: String,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (start: String, end: String) -> Unit,
    startLabel: String = "开始时间",
    endLabel: String = "结束时间",
    durationMinutes: Int? = null,
    conflictRanges: List<Pair<Int, Int>> = emptyList(),
) {
    val startMinutes = parseMinutes(start).orZeroMinute()
    val endMinutes =
        parseMinutes(end).orZeroMinute().takeIf { it > startMinutes }
            ?: (startMinutes + (durationMinutes ?: DefaultSpanMinutes)).coerceAtMost(LAST_MINUTE)
    var startHour by remember(start) { mutableIntStateOf(startMinutes / 60) }
    var startMinute by remember(start) { mutableIntStateOf(startMinutes % 60) }
    var endHour by remember(start, end) { mutableIntStateOf(endMinutes / 60) }
    var endMinute by remember(start, end) { mutableIntStateOf(endMinutes % 60) }

    fun moveStart(hour: Int, minute: Int) {
        startHour = hour
        startMinute = minute
        if (durationMinutes == null) return
        val total = (hour * 60 + minute + durationMinutes.coerceAtLeast(1)).coerceAtMost(LAST_MINUTE)
        endHour = total / 60
        endMinute = total % 60
    }

    val startTotal = startHour * 60 + startMinute
    val endTotal = endHour * 60 + endMinute
    val timeValid =
        endTotal > startTotal &&
            conflictRanges.none { (otherStart, otherEnd) -> startTotal < otherEnd && otherStart < endTotal }

    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        confirmEnabled = timeValid,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(formatMinutes(startTotal), formatMinutes(endTotal)) },
    ) {
        TimeRangeRow(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            onStartHourChange = { moveStart(it, startMinute) },
            onStartMinuteChange = { moveStart(startHour, it) },
            onEndHourChange = { endHour = it },
            onEndMinuteChange = { endMinute = it },
            startLabel = startLabel,
            endLabel = endLabel,
        )
    }
}

private fun Int?.orZeroMinute(): Int = this ?: 0
