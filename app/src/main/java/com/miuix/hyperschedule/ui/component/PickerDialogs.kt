package com.miuix.hyperschedule.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 日期滚轮对话框：跳转日期、开始上课日期共用 */
@Composable
fun DatePickerDialog(
    title: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        content = {
            DatePickerRow(
                year = date.year,
                onYearChange = { onDateChange(date.withYear(it)) },
                month = date.monthValue,
                onMonthChange = { onDateChange(date.withMonth(it)) },
                day = date.dayOfMonth,
                onDayChange = { onDateChange(date.withDayOfMonth(it)) },
            )
        },
    )
}

/** 单个数字滚轮的对话框：周次、总周数等共用 */
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
) {
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        content = {
            NumberPicker(
                value = value,
                onValueChange = onValueChange,
                range = range,
                visibleItemCount = 3,
                itemHeight = 60.dp,
                label = label,
                textStyle = MiuixTheme.textStyles.title2,
            )
        },
    )
}
