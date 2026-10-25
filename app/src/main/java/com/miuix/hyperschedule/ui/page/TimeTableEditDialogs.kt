package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.schedule.formatMinutes
import com.miuix.hyperschedule.schedule.parseMinutes
import com.miuix.hyperschedule.ui.component.IconHeaderDialog
import com.miuix.hyperschedule.ui.component.TimeRangeRow
import com.miuix.hyperschedule.ui.component.TimeWheelLabel
import com.miuix.hyperschedule.ui.component.WeekdaySelectGrid
import top.yukonga.miuix.kmp.basic.TextField

/** 新增/编辑特殊节次：名称、起止时间、上课星期依次往下排 */
@Composable
internal fun SpecialBlockDialog(
    initial: SpecialDraft,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (SpecialDraft) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var days by remember(initial.id) { mutableStateOf(initial.days) }
    val startMinutes = parseMinutes(initial.start) ?: DefaultSpecialStart
    val endMinutes = parseMinutes(initial.end) ?: (startMinutes + DefaultSpecialSpan)
    var startHour by remember(initial.id) { mutableIntStateOf(startMinutes / 60) }
    var startMinute by remember(initial.id) { mutableIntStateOf(startMinutes % 60) }
    var endHour by remember(initial.id) { mutableIntStateOf(endMinutes / 60) }
    var endMinute by remember(initial.id) { mutableIntStateOf(endMinutes % 60) }

    val start = startHour * 60 + startMinute
    val end = endHour * 60 + endMinute

    IconHeaderDialog(
        title = if (initial.name.isBlank()) "添加特殊节次" else "编辑特殊节次",
        appBlur = appBlur,
        confirmEnabled = name.isNotBlank() && days.isNotEmpty() && end > start,
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(
                initial.copy(
                    name = name.trim(),
                    start = formatMinutes(start),
                    end = formatMinutes(end),
                    days = days,
                )
            )
        },
    ) {
        TextField(
            value = name,
            onValueChange = { name = it.take(12) },
            label = "名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // 滚轮与「上课星期」自成一组，组内间距比对话框的块间距小一档；末尾留白避免格子贴着底边
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeRangeRow(
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                onStartHourChange = { startHour = it },
                onStartMinuteChange = { startMinute = it },
                onEndHourChange = { endHour = it },
                onEndMinuteChange = { endMinute = it },
            )
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                TimeWheelLabel("上课星期", textAlign = TextAlign.Start, gap = 8.dp)
                WeekdaySelectGrid(
                    selected = days,
                    onToggle = { day -> days = if (day in days) days - day else days + day },
                    onReplace = { days = it },
                )
            }
        }
    }
}
