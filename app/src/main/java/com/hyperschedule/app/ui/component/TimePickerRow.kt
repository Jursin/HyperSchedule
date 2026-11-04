package com.hyperschedule.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

val HourRange = 0..23
val MinuteRange = 0..59

private val MinuteSteps = (0..55 step 5).toList()

/** 通用单滚轮，星期、节次、年份等都用它 */
@Composable
fun NumberWheelRow(
    value: Int,
    range: IntRange,
    label: (Int) -> String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    wrapAround: Boolean = false,
) {
    WheelPicker(
        value = value,
        range = range,
        label = label,
        onValueChange = onValueChange,
        modifier = modifier,
        wrapAround = wrapAround,
    )
}

/** 时:分两个滚轮（24 小时制），分钟 5 分钟一档 */
@Composable
fun TimeWheelRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 初始分钟不在 5 分钟档上时保留，打开对话框不会顺手改掉原值
    val seedMinute = remember { minute.coerceIn(MinuteRange) }
    val minutes = remember(seedMinute) {
        if (seedMinute % 5 == 0) MinuteSteps else (MinuteSteps + seedMinute).sorted()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberWheelRow(
            value = hour,
            range = HourRange,
            label = { "%02d".format(it) },
            onValueChange = onHourChange,
            modifier = Modifier.weight(1f),
            wrapAround = true,
        )
        Text(
            text = ":",
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.offset(y = (-2).dp),
        )
        // 后端传的是「分钟数」，滚轮用档位下标
        val minuteIndex = minutes.indexOf(minute).takeIf { it >= 0 } ?: minutes.indexOf(minute / 5 * 5).coerceAtLeast(0)
        NumberWheelRow(
            value = minuteIndex,
            range = minutes.indices,
            label = { "%02d".format(minutes[it]) },
            onValueChange = { onMinuteChange(minutes[it]) },
            modifier = Modifier.weight(1f),
            wrapAround = true,
        )
    }
}

@Composable
fun TimeRangeRow(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartHourChange: (Int) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    onEndMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startLabel: String = "开始时间",
    endLabel: String = "结束时间",
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            TimeWheelLabel(startLabel)
            TimeWheelRow(
                hour = startHour,
                minute = startMinute,
                onHourChange = onStartHourChange,
                onMinuteChange = onStartMinuteChange,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            TimeWheelLabel(endLabel)
            TimeWheelRow(
                hour = endHour,
                minute = endMinute,
                onHourChange = onEndHourChange,
                onMinuteChange = onEndMinuteChange,
            )
        }
    }
}

/** 滚轮上方的小标题，所有对话框共用这一种样式，整块内容的标题（如上课星期）用 [textAlign] 左对齐 */
@Composable
fun TimeWheelLabel(text: String, textAlign: TextAlign = TextAlign.Center, gap: Dp = 4.dp) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(gap))
}
