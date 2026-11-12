package com.hyperschedule.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.repository.TableSummary
import com.hyperschedule.app.schedule.adjustDate
import com.hyperschedule.app.schedule.cjkDigitGap
import java.time.LocalDate
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

/** 日期滚轮对话框 */
@Composable
fun DatePickerDialog(
    title: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        confirmEnabled = confirmEnabled,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    ) {
        DatePickerRow(
            year = date.year,
            onYearChange = { onDateChange(adjustDate(date, year = it)) },
            month = date.monthValue,
            onMonthChange = { onDateChange(adjustDate(date, month = it)) },
            day = date.dayOfMonth,
            onDayChange = { onDateChange(adjustDate(date, day = it)) },
        )
    }
}

/** 单个数字滚轮的对话框 */
@Composable
fun NumberPickerDialog(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: (Int) -> String,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    wrapAround: Boolean = false,
) {
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    ) {
        NumberWheelRow(
            value = value,
            range = range,
            label = label,
            onValueChange = onValueChange,
            wrapAround = wrapAround,
        )
    }
}

/** 导入到课表，文件/分享码/教务脚本共用，[onAddTable] 为空时不显示新建入口 */
@Composable
fun ImportTablePickerDialog(
    tables: List<TableSummary>,
    selectedTableId: String?,
    appBlur: Boolean,
    onSelect: (tableId: String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    busy: Boolean = false,
    onAddTable: (() -> Unit)? = null,
) {
    ConfirmDialog(
        title = "导入到课表",
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmEnabled = selectedTableId != null && !busy,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tables.forEach { summary ->
                SelectableEditRow(
                    title = summary.table.name.ifBlank { "未命名" },
                    summary =
                        if (summary.courseCount > 0) {
                            "共${summary.courseCount}门课程，导入后将被覆盖".cjkDigitGap()
                        } else {
                            "暂无课程"
                        },
                    selected = summary.table.id == selectedTableId,
                    onSelect = { onSelect(summary.table.id) },
                )
            }
        }
        if (onAddTable != null) {
            WideActionButton(
                text = "添加课表",
                icon = MiuixIcons.Add,
                onClick = onAddTable,
            )
        }
    }
}
