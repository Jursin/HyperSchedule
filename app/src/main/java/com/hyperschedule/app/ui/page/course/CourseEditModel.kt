package com.hyperschedule.app.ui.page.course

import androidx.compose.ui.graphics.Color
import com.hyperschedule.app.data.db.entity.Course
import com.hyperschedule.app.data.db.entity.CourseWithWeeks
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.schedule.parseMinutes
import com.hyperschedule.app.schedule.startMinutesOf
import com.hyperschedule.app.ui.component.dayLabel
import com.hyperschedule.app.ui.component.formatCredit
import java.util.UUID

/** 编辑页里的一个时段草稿，保存前只存在内存里 */
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

/** 排序用的起始周次，没选周次的排到最后 */
private val GroupDraft.firstWeek: Int
    get() = weeks.minOrNull() ?: Int.MAX_VALUE

/** 按星期、开始时间、起始周次排列，与课表里的先后一致 */
internal fun List<GroupDraft>.sortedBySchedule(sectionTimes: List<Pair<String, String>>): List<GroupDraft> {
    val sectionStart =
        sectionTimes
            .mapIndexedNotNull { index, (start, _) -> parseMinutes(start)?.let { index + 1 to it } }
            .toMap()
    return sortedWith(
        compareBy(
            { it.day },
            { startMinutesOf(it.isCustomTime, it.customStartTime, it.startSection, sectionStart) },
            { it.firstWeek },
        )
    )
}

/** 快照按时段顺序规整 */
internal fun EditState.sortedBySchedule(sectionTimes: List<Pair<String, String>>): EditState =
    copy(groups = groups.sortedBySchedule(sectionTimes))

/** 从库里读出的初始草稿，[initialName] 为空时按预填的星期与节次造一张空卡 */
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

/** 草稿内互相比，再与表内其它课程比，返回第一处冲突的说明，没有冲突返回 null */
internal fun findConflict(
    groups: List<GroupDraft>,
    courses: List<CourseWithWeeks>,
    tableId: String?,
    savedIds: Set<String>,
    sectionTimes: List<Pair<String, String>>,
): String? {
    val others =
        courses
            .filter { it.course.courseTableId == tableId && it.course.id !in savedIds }
            .map { it.course.name to it.course.toSlot(it.weeks.map { week -> week.weekNumber }.toSet()) }
    groups.forEachIndexed { index, group ->
        val slot = group.toSlot()
        for (otherIndex in index + 1 until groups.size) {
            val other = groups[otherIndex].toSlot()
            if (!slot.overlaps(other, sectionTimes)) continue
            val week = (slot.weeks intersect other.weeks).minOrNull() ?: continue
            return "第${week}周${dayLabel(group.day)}与另一个时段重叠"
        }
        others
            .firstOrNull { (_, other) -> slot.overlaps(other, sectionTimes) && slot.weeks.any { it in other.weeks } }
            ?.let { (name, other) ->
                val week = slot.weeks.filter { it in other.weeks }.min()
                return "第${week}周${dayLabel(group.day)}与「$name」冲突"
            }
    }
    return null
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

/** 时段摘要，自定义时间用「时:分 - 时:分」，否则用节次 */
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
