package com.hyperschedule.app.data.backup

import android.content.SharedPreferences
import com.hyperschedule.app.data.db.entity.Course
import com.hyperschedule.app.data.db.entity.CourseTable
import com.hyperschedule.app.data.db.entity.CourseTableConfig
import com.hyperschedule.app.data.db.entity.CourseTimeBinding
import com.hyperschedule.app.data.db.entity.CourseWeek
import com.hyperschedule.app.data.db.entity.SpecialTimeBlock
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.db.entity.TimeTable
import com.hyperschedule.app.data.db.entity.TimeTableCombo
import com.hyperschedule.app.data.db.entity.TimeTableComboRule
import com.hyperschedule.app.schedule.DEFAULT_TOTAL_WEEKS
import kotlinx.serialization.Serializable

const val BACKUP_FORMAT = "hyperschedule-backup"

/** 当前备份格式版本，读到更高版本直接拒绝 */
const val BACKUP_FORMAT_VERSION = 1

/** 备份文件，编码时不写默认值与 null */
@Serializable
data class BackupPayload(
    val format: String,
    val formatVersion: Int,
    val appVersion: String = "",
    val createdAt: String = "",
    val tables: List<TableRow> = emptyList(),
    val timeTables: List<TimeTableRow> = emptyList(),
    val combos: List<ComboRow> = emptyList(),
    val prefs: Map<String, PrefsSnapshot> = emptyMap(),
) {
    val tableCount: Int get() = tables.size

    val courseCount: Int get() = tables.sumOf { it.courses.size }

    /** 指纹只看内容，不看时间与版本号 */
    fun contentKey(): BackupPayload = copy(appVersion = "", createdAt = "")
}

/** 课表 */
@Serializable
data class TableRow(
    val id: String,
    val name: String,
    val createdAt: Long,
    val showWeekends: Boolean = false,
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int = DEFAULT_TOTAL_WEEKS,
    val binding: BindingRow? = null,
    val courses: List<CourseRow> = emptyList(),
) {
    fun toEntity() = CourseTable(id = id, name = name, createdAt = createdAt)

    fun toConfig() =
        CourseTableConfig(
            courseTableId = id,
            showWeekends = showWeekends,
            semesterStartDate = semesterStartDate,
            semesterTotalWeeks = semesterTotalWeeks,
        )

    companion object {
        fun of(
            row: CourseTable,
            config: CourseTableConfig?,
            binding: CourseTimeBinding?,
            courses: List<CourseRow>,
        ) = TableRow(
            id = row.id,
            name = row.name,
            createdAt = row.createdAt,
            showWeekends = config?.showWeekends ?: false,
            semesterStartDate = config?.semesterStartDate,
            semesterTotalWeeks = config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS,
            binding = binding?.let(BindingRow::of),
            courses = courses,
        )
    }
}

/** 课表当前指向的作息或组合 */
@Serializable
data class BindingRow(
    val targetType: String = CourseTimeBinding.TARGET_SINGLE,
    val targetId: String,
) {
    fun toEntity(courseTableId: String) =
        CourseTimeBinding(courseTableId = courseTableId, targetType = targetType, targetId = targetId)

    companion object {
        fun of(row: CourseTimeBinding) = BindingRow(targetType = row.targetType, targetId = row.targetId)
    }
}

/** 课程 */
@Serializable
data class CourseRow(
    val id: String,
    val name: String,
    val teacher: String = "",
    val position: String = "",
    val day: Int,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val colorInt: Int,
    val colorArgb: Int? = null,
    val remark: String? = null,
    val credit: Float? = null,
    val weeks: List<Int> = emptyList(),
) {
    fun toEntity(courseTableId: String) =
        Course(
            id = id,
            courseTableId = courseTableId,
            name = name,
            teacher = teacher,
            position = position,
            day = day,
            startSection = startSection,
            endSection = endSection,
            isCustomTime = isCustomTime,
            customStartTime = customStartTime,
            customEndTime = customEndTime,
            colorInt = colorInt,
            colorArgb = colorArgb,
            remark = remark,
            credit = credit,
        )

    fun toWeeks(): List<CourseWeek> = weeks.distinct().map { CourseWeek(courseId = id, weekNumber = it) }

    companion object {
        fun of(
            row: Course,
            weeks: List<Int>,
        ) = CourseRow(
            id = row.id,
            name = row.name,
            teacher = row.teacher,
            position = row.position,
            day = row.day,
            startSection = row.startSection,
            endSection = row.endSection,
            isCustomTime = row.isCustomTime,
            customStartTime = row.customStartTime,
            customEndTime = row.customEndTime,
            colorInt = row.colorInt,
            colorArgb = row.colorArgb,
            remark = row.remark,
            credit = row.credit,
            weeks = weeks,
        )
    }
}

/** 作息 */
@Serializable
data class TimeTableRow(
    val id: String,
    val name: String? = null,
    val createdAt: Long,
    val defaultClassDuration: Int = 0,
    val morningAfternoonSplit: String = "",
    val afternoonEveningSplit: String = "",
    val slots: List<SlotRow> = emptyList(),
    val specials: List<SpecialRow> = emptyList(),
) {
    fun toEntity() =
        TimeTable(
            id = id,
            name = name,
            createdAt = createdAt,
            defaultClassDuration = defaultClassDuration,
            morningAfternoonSplit = morningAfternoonSplit,
            afternoonEveningSplit = afternoonEveningSplit,
        )

    companion object {
        fun of(
            row: TimeTable,
            slots: List<SlotRow>,
            specials: List<SpecialRow>,
        ) = TimeTableRow(
            id = row.id,
            name = row.name,
            createdAt = row.createdAt,
            defaultClassDuration = row.defaultClassDuration,
            morningAfternoonSplit = row.morningAfternoonSplit,
            afternoonEveningSplit = row.afternoonEveningSplit,
            slots = slots,
            specials = specials,
        )
    }
}

@Serializable
data class SlotRow(
    val number: Int,
    val startTime: String = "",
    val endTime: String = "",
    val alias: String? = null,
) {
    fun toEntity(timeTableId: String) =
        TimeSlot(timeTableId = timeTableId, number = number, startTime = startTime, endTime = endTime, alias = alias)

    companion object {
        fun of(row: TimeSlot) =
            SlotRow(number = row.number, startTime = row.startTime, endTime = row.endTime, alias = row.alias)
    }
}

@Serializable
data class SpecialRow(
    val id: String,
    val name: String,
    val startTime: String = "",
    val endTime: String = "",
    val days: String = "",
) {
    fun toEntity(timeTableId: String) =
        SpecialTimeBlock(
            id = id,
            timeTableId = timeTableId,
            name = name,
            startTime = startTime,
            endTime = endTime,
            days = days,
        )

    companion object {
        fun of(row: SpecialTimeBlock) =
            SpecialRow(
                id = row.id,
                name = row.name,
                startTime = row.startTime,
                endTime = row.endTime,
                days = row.days,
            )
    }
}

/** 组合作息 */
@Serializable
data class ComboRow(
    val id: String,
    val name: String,
    val baseTimeTableId: String? = null,
    val createdAt: Long,
    val rules: List<RuleRow> = emptyList(),
) {
    fun toEntity() = TimeTableCombo(id = id, name = name, baseTimeTableId = baseTimeTableId, createdAt = createdAt)

    companion object {
        fun of(
            row: TimeTableCombo,
            rules: List<RuleRow>,
        ) = ComboRow(
            id = row.id,
            name = row.name,
            baseTimeTableId = row.baseTimeTableId,
            createdAt = row.createdAt,
            rules = rules,
        )
    }
}

/** 组合内某段日期使用的作息 */
@Serializable
data class RuleRow(
    val id: String,
    val targetTimeTableId: String,
    val startDate: String,
    val endDate: String,
) {
    fun toEntity(comboId: String) =
        TimeTableComboRule(
            id = id,
            comboId = comboId,
            targetTimeTableId = targetTimeTableId,
            startDate = startDate,
            endDate = endDate,
        )

    companion object {
        fun of(row: TimeTableComboRule) =
            RuleRow(
                id = row.id,
                targetTimeTableId = row.targetTimeTableId,
                startDate = row.startDate,
                endDate = row.endDate,
            )
    }
}

/** 一份 SharedPreferences 的类型化快照，分类型存，回写时不用猜类型 */
@Serializable
data class PrefsSnapshot(
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val longs: Map<String, Long> = emptyMap(),
    val strings: Map<String, String> = emptyMap(),
    val stringSets: Map<String, List<String>> = emptyMap(),
) {
    companion object {
        /** [keys] 为 null 表示整份备份，未知类型跳过 */
        fun of(all: Map<String, *>, keys: Set<String>? = null): PrefsSnapshot {
            val booleans = sortedMapOf<String, Boolean>()
            val ints = sortedMapOf<String, Int>()
            val floats = sortedMapOf<String, Float>()
            val longs = sortedMapOf<String, Long>()
            val strings = sortedMapOf<String, String>()
            val stringSets = sortedMapOf<String, List<String>>()
            all.forEach { (key, value) ->
                if (keys != null && key !in keys) return@forEach
                when (value) {
                    is Boolean -> booleans[key] = value
                    is Int -> ints[key] = value
                    is Float -> floats[key] = value
                    is Long -> longs[key] = value
                    is String -> strings[key] = value
                    is Set<*> -> stringSets[key] = value.filterIsInstance<String>().sorted()
                    else -> Unit
                }
            }
            return PrefsSnapshot(booleans, ints, floats, longs, strings, stringSets)
        }
    }
}

/** 把快照整体写入 editor */
internal fun SharedPreferences.Editor.putAll(snapshot: PrefsSnapshot) {
    snapshot.booleans.forEach(::putBoolean)
    snapshot.ints.forEach(::putInt)
    snapshot.floats.forEach(::putFloat)
    snapshot.longs.forEach(::putLong)
    snapshot.strings.forEach(::putString)
    snapshot.stringSets.forEach { (key, value) -> putStringSet(key, value.toSet()) }
}
