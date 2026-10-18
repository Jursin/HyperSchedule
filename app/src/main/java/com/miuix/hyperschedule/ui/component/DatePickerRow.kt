package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 日期选择器的年份范围，所有日期选择器统一取这里 */
val DatePickerYearRange = 2000..2099

/** 年/月/日三滚轮；跳转日期与开始上课日期共用 */
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
    Row(modifier = modifier.fillMaxWidth()) {
        NumberPicker(value = year, onValueChange = onYearChange, range = DatePickerYearRange, visibleItemCount = 3, itemHeight = 60.dp, textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f))
        NumberPicker(value = month, onValueChange = onMonthChange, range = 1..12, visibleItemCount = 3, itemHeight = 60.dp, label = { "${it}月" }, textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f))
        NumberPicker(value = day, onValueChange = onDayChange, range = 1..31, visibleItemCount = 3, itemHeight = 60.dp, label = { "${it}日" }, textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f))
    }
}
