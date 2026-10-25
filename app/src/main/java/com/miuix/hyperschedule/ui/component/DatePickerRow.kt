package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 日期选择器的年份范围，所有日期选择器统一取这里 */
val DatePickerYearRange = 2000..2099

/** 年/月/日三个滚轮；跳转日期、开始上课日期、节假日日期共用 */
@Composable
fun DatePickerRow(
    year: Int,
    onYearChange: (Int) -> Unit,
    month: Int,
    onMonthChange: (Int) -> Unit,
    day: Int,
    onDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NumberWheelRow(
            value = year,
            range = DatePickerYearRange,
            label = { it.toString() },
            onValueChange = onYearChange,
            modifier = Modifier.weight(1f),
        )
        NumberWheelRow(
            value = month,
            range = 1..12,
            label = { "${it}月" },
            onValueChange = onMonthChange,
            modifier = Modifier.weight(1f),
        )
        NumberWheelRow(
            value = day,
            range = 1..31,
            label = { "${it}日" },
            onValueChange = onDayChange,
            modifier = Modifier.weight(1f),
        )
    }
}
