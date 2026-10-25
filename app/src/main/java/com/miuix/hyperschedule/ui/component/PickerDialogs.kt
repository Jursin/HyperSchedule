package com.miuix.hyperschedule.ui.component

import androidx.compose.runtime.Composable
import com.miuix.hyperschedule.schedule.adjustDate
import java.time.LocalDate

/** 日期滚轮对话框：跳转日期、开始上课日期、组合规则、节假日日期共用 */
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

/** 单个数字滚轮的对话框：节次数量、每节课时长、总周数等共用 */
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
