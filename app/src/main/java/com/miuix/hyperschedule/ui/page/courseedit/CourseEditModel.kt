package com.miuix.hyperschedule.ui.page.courseedit

import androidx.compose.ui.graphics.Color
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.schedule.cjkDigitGap
import com.miuix.hyperschedule.ui.component.formatCredit
import java.util.UUID

/** 编辑页里的一个时段草稿：保存前只存在内存里 */
internal data class GroupDraft(
    val id: String,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val isCustomTime: Boolean,
    val customStartTime: String?,
    val customEndTime: String?,
    val position: String,
    val teacher: String,
    val weeks: Set<Int>,
    val remark: String,
)

/** 整页快照，用于判断是否有未保存改动 */
internal data class EditState(
    val name: String,
    val colorInt: Int?,
    val colorArgb: Int?,
    val credit: String,
    val groups: List<GroupDraft>,
)

/** 从库里读出的初始草稿；[initialName] 为空时按预填的星期与节次造一张空卡 */
internal fun buildSeed(
    initialName: String,
    prefillDay: Int,
    prefillStart: Int,
    prefillEnd: Int,
    allCourses: List<CourseWithWeeks>,
    tableId: String?,
    totalWeeks: Int,
): EditState {
    if (initialName.isBlank()) {
        val start = prefillStart.coerceAtLeast(1)
        return EditState(
            name = "",
            colorInt = null,
            colorArgb = null,
            credit = "",
            groups =
                listOf(
                    GroupDraft(
                        id = UUID.randomUUID().toString(),
                        day = prefillDay.takeIf { it in 1..7 } ?: 1,
                        startSection = start,
                        endSection = prefillEnd.coerceAtLeast(start),
                        isCustomTime = false,
                        customStartTime = null,
                        customEndTime = null,
                        position = "",
                        teacher = "",
                        weeks = (1..totalWeeks).toSet(),
                        remark = "",
                    )
                ),
        )
    }
    val rows =
        allCourses
            .filter { it.course.name == initialName && it.course.courseTableId == tableId }
            .sortedWith(
                compareBy(
                    { it.course.day },
                    { it.course.isCustomTime },
                    { it.course.startSection ?: 99 },
                    { it.course.customStartTime ?: "99:99" },
                )
            )
    return EditState(
        name = initialName,
        colorInt = rows.firstOrNull()?.course?.colorInt,
        colorArgb = rows.firstOrNull()?.course?.colorArgb,
        credit = rows.firstOrNull()?.course?.credit?.let(::formatCredit).orEmpty(),
        groups =
            rows.map { row ->
                GroupDraft(
                    id = row.course.id,
                    day = row.course.day,
                    startSection = row.course.startSection ?: 1,
                    endSection = row.course.endSection ?: row.course.startSection ?: 1,
                    isCustomTime = row.course.isCustomTime,
                    customStartTime = row.course.customStartTime,
                    customEndTime = row.course.customEndTime,
                    position = row.course.position,
                    teacher = row.course.teacher,
                    weeks = row.weeks.map { it.weekNumber }.toSet(),
                    remark = row.course.remark.orEmpty(),
                )
            },
    )
}

/** 冲突判断用的时段 */
internal data class Slot(
    val day: Int,
    val startSection: Int?,
    val endSection: Int?,
    val isCustomTime: Boolean,
    val startTime: String?,
    val endTime: String?,
    val weeks: Set<Int>,
)

internal fun GroupDraft.toSlot() =
    Slot(day, startSection, endSection, isCustomTime, customStartTime, customEndTime, weeks)

internal fun Course.toSlot(weeks: Set<Int>) =
    Slot(day, startSection, endSection, isCustomTime, customStartTime, customEndTime, weeks)

/** "HH:mm" 定长，字符串比较等价于时间比较 */
private fun Slot.timeRange(sectionTimes: List<Pair<String, String>>): Pair<String, String>? =
    if (isCustomTime) {
        val from = startTime?.takeIf { it.isNotBlank() }
        val to = endTime?.takeIf { it.isNotBlank() }
        if (from == null || to == null) null else from to to
    } else {
        val start = startSection ?: return null
        val end = endSection ?: start
        val from = sectionTimes.getOrNull(start - 1)?.first
        val to = sectionTimes.getOrNull(end - 1)?.second
        if (from == null || to == null) null else from to to
    }

internal fun Slot.overlaps(other: Slot, sectionTimes: List<Pair<String, String>>): Boolean {
    if (day != other.day) return false
    if (!isCustomTime && !other.isCustomTime) {
        val a1 = startSection ?: return false
        val a2 = endSection ?: a1
        val b1 = other.startSection ?: return false
        val b2 = other.endSection ?: b1
        return a1 <= b2 && b1 <= a2
    }
    val a = timeRange(sectionTimes) ?: return false
    val b = other.timeRange(sectionTimes) ?: return false
    return a.first < b.second && b.first < a.second
}

internal fun GroupDraft.toCourse(
    tableId: String,
    name: String,
    colorInt: Int,
    colorArgb: Int?,
    credit: Float?,
): Course =
    Course(
        id = id,
        courseTableId = tableId,
        name = name,
        teacher = teacher,
        position = position,
        day = day,
        startSection = if (isCustomTime) null else startSection,
        endSection = if (isCustomTime) null else endSection,
        isCustomTime = isCustomTime,
        customStartTime = if (isCustomTime) customStartTime else null,
        customEndTime = if (isCustomTime) customEndTime else null,
        colorInt = colorInt,
        colorArgb = colorArgb,
        remark = remark.takeIf { it.isNotBlank() },
        credit = credit,
    )

/** 时段摘要：自定义时间用「时:分 - 时:分」，否则用节次 */
internal fun timeText(group: GroupDraft): String =
    if (group.isCustomTime) {
        val from = group.customStartTime.orEmpty()
        val to = group.customEndTime.orEmpty()
        if (from.isBlank() || to.isBlank()) "" else "$from - $to"
    } else {
        val start = group.startSection
        val end = group.endSection
        (if (start == end) "第${start}节" else "第${start}-${end}节").cjkDigitGap()
    }

/** 学分只保留数字与一个小数点，最多一位小数 */
internal fun sanitizeCredit(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    if (dot < 0) return filtered.take(3)
    val intPart = filtered.substring(0, dot).take(2)
    val decimals = filtered.substring(dot + 1).filter { it.isDigit() }.take(1)
    return "$intPart.$decimals"
}

internal fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
