package com.miuix.hyperschedule.data.backup

import android.content.SharedPreferences
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseTable
import com.miuix.hyperschedule.data.model.CourseTableConfig
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import com.miuix.hyperschedule.data.model.CourseWeek
import com.miuix.hyperschedule.data.model.SpecialTimeBlock
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.model.TimeTable
import com.miuix.hyperschedule.data.model.TimeTableCombo
import com.miuix.hyperschedule.data.model.TimeTableComboRule
import kotlinx.serialization.Serializable

/** 备份格式标识 */
const val BACKUP_FORMAT = "hyperschedule-backup"

/** 当前备份格式版本；读到更高版本直接拒绝 */
const val BACKUP_FORMAT_VERSION = 1

/** 备份文件，编码时不写默认值与 null */
@Serializable
data class BackupPayload(
    val format: String,
    val formatVersion: Int,
    val appVersion: String = "",
    val appVersionCode: Long = 0,
    val createdAt: String = "",
    val tables: List<TableRow> = emptyList(),
    val configs: List<ConfigRow> = emptyList(),
    val courses: List<CourseRow> = emptyList(),
    val courseWeeks: List<WeekRow> = emptyList(),
    val timeTables: List<TimeTableRow> = emptyList(),
    val timeSlots: List<SlotRow> = emptyList(),
    val specialBlocks: List<SpecialRow> = emptyList(),
    val bindings: List<BindingRow> = emptyList(),
    val combos: List<ComboRow> = emptyList(),
    val comboRules: List<RuleRow> = emptyList(),
    val prefs: Map<String, PrefsSnapshot> = emptyMap(),
) {
    /** 课表张数 */
    val tableCount: Int get() = tables.size

    /** 课程门数 */
    val courseCount: Int get() = courses.size

    /** 指纹只看内容，不看时间与版本号 */
    fun contentKey(): BackupPayload = copy(appVersion = "", appVersionCode = 0, createdAt = "")
}

@Serializable
data class TableRow(
    val id: String,
    val name: String,
    val createdAt: Long,
) {
    fun toEntity() = CourseTable(id = id, name = name, createdAt = createdAt)

    companion object {
        fun of(row: CourseTable) = TableRow(id = row.id, name = row.name, createdAt = row.createdAt)
    }
}

@Serializable
data class ConfigRow(
    val courseTableId: String,
    val showWeekends: Boolean = false,
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int = 0,
) {
    fun toEntity() =
        CourseTableConfig(
            courseTableId = courseTableId,
            showWeekends = showWeekends,
            semesterStartDate = semesterStartDate,
            semesterTotalWeeks = semesterTotalWeeks,
        )

    companion object {
        fun of(row: CourseTableConfig) =
            ConfigRow(
                courseTableId = row.courseTableId,
                showWeekends = row.showWeekends,
                semesterStartDate = row.semesterStartDate,
                semesterTotalWeeks = row.semesterTotalWeeks,
            )
    }
}

@Serializable
data class CourseRow(
    val id: String,
    val courseTableId: String,
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
) {
    fun toEntity() =
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

    companion object {
        fun of(row: Course) =
            CourseRow(
                id = row.id,
                courseTableId = row.courseTableId,
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
            )
    }
}

@Serializable
data class WeekRow(
    val courseId: String,
    val weekNumber: Int,
) {
    fun toEntity() = CourseWeek(courseId = courseId, weekNumber = weekNumber)

    companion object {
        fun of(row: CourseWeek) = WeekRow(courseId = row.courseId, weekNumber = row.weekNumber)
    }
}

@Serializable
data class TimeTableRow(
    val id: String,
    val name: String? = null,
    val createdAt: Long,
    val defaultClassDuration: Int = 0,
    val morningAfternoonSplit: String = "",
    val afternoonEveningSplit: String = "",
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
        fun of(row: TimeTable) =
            TimeTableRow(
                id = row.id,
                name = row.name,
                createdAt = row.createdAt,
                defaultClassDuration = row.defaultClassDuration,
                morningAfternoonSplit = row.morningAfternoonSplit,
                afternoonEveningSplit = row.afternoonEveningSplit,
            )
    }
}

@Serializable
data class SlotRow(
    val timeTableId: String,
    val number: Int,
    val startTime: String = "",
    val endTime: String = "",
    val alias: String? = null,
) {
    fun toEntity() =
        TimeSlot(timeTableId = timeTableId, number = number, startTime = startTime, endTime = endTime, alias = alias)

    companion object {
        fun of(row: TimeSlot) =
            SlotRow(
                timeTableId = row.timeTableId,
                number = row.number,
                startTime = row.startTime,
                endTime = row.endTime,
                alias = row.alias,
            )
    }
}

@Serializable
data class SpecialRow(
    val id: String,
    val timeTableId: String,
    val name: String,
    val startTime: String = "",
    val endTime: String = "",
    val days: String = "",
) {
    fun toEntity() =
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
                timeTableId = row.timeTableId,
                name = row.name,
                startTime = row.startTime,
                endTime = row.endTime,
                days = row.days,
            )
    }
}

@Serializable
data class BindingRow(
    val courseTableId: String,
    val targetType: String = CourseTimeBinding.TARGET_SINGLE,
    val targetId: String,
) {
    fun toEntity() =
        CourseTimeBinding(courseTableId = courseTableId, targetType = targetType, targetId = targetId)

    companion object {
        fun of(row: CourseTimeBinding) =
            BindingRow(courseTableId = row.courseTableId, targetType = row.targetType, targetId = row.targetId)
    }
}

@Serializable
data class ComboRow(
    val id: String,
    val name: String,
    val baseTimeTableId: String? = null,
    val createdAt: Long,
) {
    fun toEntity() = TimeTableCombo(id = id, name = name, baseTimeTableId = baseTimeTableId, createdAt = createdAt)

    companion object {
        fun of(row: TimeTableCombo) =
            ComboRow(id = row.id, name = row.name, baseTimeTableId = row.baseTimeTableId, createdAt = row.createdAt)
    }
}

@Serializable
data class RuleRow(
    val id: String,
    val comboId: String,
    val targetTimeTableId: String,
    val startDate: String,
    val endDate: String,
) {
    fun toEntity() =
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
                comboId = row.comboId,
                targetTimeTableId = row.targetTimeTableId,
                startDate = row.startDate,
                endDate = row.endDate,
            )
    }
}

/** 一份 SharedPreferences 的类型化快照；分类型存，回写时不用猜类型 */
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
        /** [keys] 为 null 表示整份备份；未知类型跳过 */
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
