package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.theme.BadgeFontSize
import com.miuix.hyperschedule.theme.isDarkTheme
import com.miuix.hyperschedule.schedule.HolidayDay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 放假当天：课程不上，卡片灰显并标这个字 */
internal const val MarkHoliday = "假"

/** 补课当天：这一列是挪过来的课，标这个字 */
internal const val MarkWorkSwap = "调"

/** 角标取值：放假「假」、补课「调」，都不是时为空 */
internal fun markOf(isHoliday: Boolean, isWorkSwap: Boolean): String? =
    when {
        isHoliday -> MarkHoliday
        isWorkSwap -> MarkWorkSwap
        else -> null
    }

/** 这一天的角标 */
internal fun HolidayDay.markOrNull(): String? = markOf(holiday, workSwap)

/**
 * 放假 / 调休角标，课表卡片与今日课表共用同一套取色：
 * 「调」是正常上课的一天，用课程色淡底配课程色文字；「假」用中性底，避免和灰卡抢眼。
 */
@Composable
internal fun HolidayMarkBadge(
    mark: String,
    course: Course,
    modifier: Modifier = Modifier,
    // 只有「调」且课确实在本周生效时才跟着课程色，否则走中性灰（与卡片灰显一致）
    accent: Boolean = true,
    fontSize: TextUnit = BadgeFontSize,
) {
    val isDark = isDarkTheme()
    val useAccent = accent && mark == MarkWorkSwap
    val tint = rememberCourseColor(course)
    val background =
        when {
            useAccent -> tint.copy(alpha = 0.32f)
            isDark -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        }
    val content =
        when {
            useAccent && MiuixTheme.isDynamicColor -> tint
            useAccent -> Color.White
            else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        }
    Text(
        text = mark,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        color = content,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .padding(horizontal = 2.dp, vertical = 1.dp),
    )
}
