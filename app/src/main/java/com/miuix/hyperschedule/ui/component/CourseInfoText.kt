package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.TimeSlot
import java.time.LocalTime
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课程信息行的图标占位 id */
internal object InfoIconId {
    const val WEEKS = "infoWeeks"
    const val SECTION = "infoSection"
    const val POSITION = "infoPosition"
    const val TEACHER = "infoTeacher"
    const val CREDIT = "infoCredit"
    const val REMARK = "infoRemark"
}

/** 图标盒：墨迹约占盒 0.78，取 15sp 略小于 14sp 正文 */
private val IconBoxSize = 15.sp

/** 图标后跟不换行空格（NBSP）：图标与其文字粘成一块，位置不够时整块换行 */
internal fun AnnotatedString.Builder.infoIcon(id: String, alt: String) {
    appendInlineContent(id, alt)
    append('\u00A0')
}

/** 两个字段之间的间距：用可断行的空格，换行点落在这里，行尾不会留下分隔符 */
internal fun AnnotatedString.Builder.fieldGap() = append("  ")

/** 字段值整块不换行：空格转不换行空格，字符间再加 word joiner，放不下时图标与文本一起换行 */
internal fun AnnotatedString.Builder.fieldValue(text: String) {
    if (text.isEmpty()) return
    val sb = StringBuilder(text.length * 2)
    var i = 0
    while (i < text.length) {
        if (i > 0) sb.append('\u2060')
        val codePoint = text.codePointAt(i)
        sb.appendCodePoint(if (codePoint == ' '.code) 0xA0 else codePoint)
        i += Character.charCount(codePoint)
    }
    append(sb)
}

/** 课程信息行的内联图标，颜色默认取次要文字色 */
@Composable
internal fun courseInfoIcons(
    tint: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
): Map<String, InlineTextContent> =
    remember(tint) {
        fun entry(vector: ImageVector) =
            InlineTextContent(
                placeholder =
                    Placeholder(IconBoxSize, IconBoxSize, PlaceholderVerticalAlign.TextCenter),
            ) {
                Icon(imageVector = vector, contentDescription = null, tint = tint)
            }
        mapOf(
            InfoIconId.WEEKS to entry(MiuixIcons.Years),
            InfoIconId.SECTION to entry(MiuixIcons.WorldClock),
            InfoIconId.POSITION to entry(MiuixIcons.Location),
            InfoIconId.TEACHER to entry(MiuixIcons.Contacts),
            InfoIconId.CREDIT to entry(MiuixIcons.Create),
            InfoIconId.REMARK to entry(MiuixIcons.Notes),
        )
    }

/** 标准课显示「第X-Y节」，自定义时间课显示起止时间 */
internal fun Course.sectionLabel(): String {
    if (isCustomTime) {
        return customStartTime?.let { start -> customEndTime?.let { end -> "$start-$end" } } ?: "自定义时间"
    }
    val start = startSection ?: return "未设置节次"
    val end = endSection ?: start
    return if (start == end) "第${start}节" else "第${start}-${end}节"
}

internal fun startTimeOf(course: Course, slotMap: Map<Int, TimeSlot>): String? =
    if (course.isCustomTime) {
        course.customStartTime
    } else {
        course.startSection?.let { slotMap[it]?.startTime }
    }

internal fun endTimeOf(course: Course, slotMap: Map<Int, TimeSlot>): String? =
    if (course.isCustomTime) {
        course.customEndTime
    } else {
        course.endSection?.let { slotMap[it]?.endTime }
    }

internal fun parseTime(text: String): LocalTime? = runCatching { LocalTime.parse(text) }.getOrNull()

/** 周次压缩：1-8, 10, 12-16 */
internal fun formatWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "无周次"
    val sorted = weeks.distinct().sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var prev = start
    for (week in sorted.drop(1)) {
        if (week == prev + 1) {
            prev = week
            continue
        }
        parts += if (start == prev) "$start" else "$start-$prev"
        start = week
        prev = week
    }
    parts += if (start == prev) "$start" else "$start-$prev"
    return "第${parts.joinToString(", ")}周"
}

/** 学分显示：整数不带小数点 */
internal fun formatCredit(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

internal val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 1..7 → 周一..周日 */
internal fun dayLabel(day: Int): String = DAY_NAMES.getOrNull(day - 1) ?: "未设置"
