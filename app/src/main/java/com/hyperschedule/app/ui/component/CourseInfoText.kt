package com.hyperschedule.app.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperschedule.app.R
import com.hyperschedule.app.data.model.Course
import com.hyperschedule.app.data.model.TimeSlot
import com.hyperschedule.app.schedule.cjkDigitGap
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalTime

/** 课程信息行的字段图标 id */
internal object InfoIconId {
    const val WEEKS = "infoWeeks"
    const val SECTION = "infoSection"
    const val POSITION = "infoPosition"
    const val TEACHER = "infoTeacher"
    const val CREDIT = "infoCredit"
    const val REMARK = "infoRemark"
}

/** 图标盒，墨迹约占 0.78 盒宽 */
private val IconBoxSize = 15.sp

/** 字段之间的间距 */
private val FieldGap = 6.dp

/** 复制点击高亮的圆角 */
private val CopyHighlightShape = RoundedCornerShape(4.dp)

/** 图标与字段值之间的间距 */
private val IconTextGap = 3.dp

/** 信息行的一枚字段 */
internal data class InfoField(val icon: String, val alt: String, val value: String)

/** 字段图标，未知 id 退回文件图标 */
@Composable
private fun infoIconVector(id: String): ImageVector = when (id) {
    InfoIconId.WEEKS -> MiuixIcons.Years
    InfoIconId.SECTION -> ImageVector.vectorResource(R.drawable.ic_schedule)
    InfoIconId.POSITION -> ImageVector.vectorResource(R.drawable.ic_location_on)
    InfoIconId.TEACHER -> ImageVector.vectorResource(R.drawable.ic_person)
    InfoIconId.CREDIT -> ImageVector.vectorResource(R.drawable.ic_award_star)
    else -> MiuixIcons.File
}

/** 复制文本并弹提示；primaryClip 属性只读，只能调 setter 方法 */
@Suppress("KotlinPropertyAccess", "UsePropertyAccessSyntax")
private fun Context.copyToClipboard(alt: String, value: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(alt, value))
    toast("已复制$alt")
}

/** 点击复制该段文字，按压高亮与信息行字段一致 */
@Composable
internal fun Modifier.copyOnClick(alt: String, value: String): Modifier {
    val context = LocalContext.current
    return clip(CopyHighlightShape)
        .clickable(onClickLabel = "复制$alt") { context.copyToClipboard(alt, value) }
}

/** 课程信息行，字段各自可点，点完复制该字段的值 */
@Composable
internal fun CourseInfoFields(
    fields: List<InfoField>,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    val iconSize = with(LocalDensity.current) { IconBoxSize.toDp() }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FieldGap),
    ) {
        fields.forEach { field ->
            Row(
                modifier = Modifier.copyOnClick(field.alt, field.value),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = infoIconVector(field.icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(modifier = Modifier.width(IconTextGap))
                Text(
                    text = field.value,
                    style = MiuixTheme.textStyles.body2,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 标准课显示「第X-Y节」，自定义时间课显示起止时间 */
internal fun Course.sectionLabel(): String {
    if (isCustomTime) {
        return customStartTime?.let { start -> customEndTime?.let { end -> "$start-$end" } } ?: "自定义时间"
    }
    val start = startSection ?: return "未设置节次"
    val end = endSection ?: start
    return (if (start == end) "第${start}节" else "第${start}-${end}节").cjkDigitGap()
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

/** 周次压缩，1-8, 10, 12-16 */
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
    return "第${parts.joinToString(", ")}周".cjkDigitGap()
}

/** 学分显示，整数不带小数点 */
internal fun formatCredit(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

internal val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 1..7 → 周一..周日 */
internal fun dayLabel(day: Int): String = DAY_NAMES.getOrNull(day - 1) ?: "未设置"
