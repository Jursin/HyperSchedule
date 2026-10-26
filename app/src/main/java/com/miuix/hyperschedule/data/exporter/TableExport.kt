package com.miuix.hyperschedule.data.exporter

import com.miuix.hyperschedule.data.importer.ComboScheduleJson
import com.miuix.hyperschedule.data.importer.CourseJson
import com.miuix.hyperschedule.data.importer.CourseTableConfigJson
import com.miuix.hyperschedule.data.importer.CourseTableJson
import com.miuix.hyperschedule.data.importer.CourseTableJsonParser
import com.miuix.hyperschedule.data.importer.PublicScheduleJson
import com.miuix.hyperschedule.data.importer.TimeSlotJson
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.TimeSlot
import java.time.LocalDate

/** 导出用的整张课表快照；[slotsFor] 给出某一天生效的作息（组合作息按日期切换） */
data class TableExportData(
    val startDate: LocalDate?,
    val totalWeeks: Int,
    val defaultClassDuration: Int,
    val courses: List<CourseWithWeeks>,
    val slots: List<TimeSlot>,
    val slotsFor: (LocalDate) -> List<TimeSlot>,
    val combo: ComboScheduleExport? = null,
)

/** 实际要展开的最后一周：课程周次可能超出总周数（导入过长学期后又调小了总周数），多出来的也要导出 */
val TableExportData.lastWeek: Int
    get() = maxOf(totalWeeks, courses.maxOfOrNull { group -> group.weeks.maxOfOrNull { it.weekNumber } ?: 0 } ?: 0)

/** 组合作息快照：组合名 + 每条日期规则各带一套公共作息 */
data class ComboScheduleExport(
    val name: String?,
    val publicSchedules: List<PublicScheduleExport>,
)

data class PublicScheduleExport(
    val name: String,
    val startDate: String,
    val endDate: String,
    val defaultClassDuration: Int,
    val slots: List<TimeSlot>,
) {
    companion object {
        const val DEFAULT_NAME = "公共作息"
    }
}

/** 单表 JSON */
fun buildTableJson(data: TableExportData): String =
    CourseTableJsonParser.encode(
        CourseTableJson(
            courses =
                data.courses
                    .sortedWith(compareBy({ it.course.day }, { it.course.startSection ?: 0 }, { it.course.name }))
                    .map { group ->
                        val course = group.course
                        CourseJson(
                            name = course.name,
                            teacher = course.teacher,
                            position = course.position,
                            day = course.day,
                            startSection = course.startSection,
                            endSection = course.endSection,
                            weeks = group.weeks.map { it.weekNumber }.sorted(),
                            isCustomTime = course.isCustomTime,
                            customStartTime = course.customStartTime,
                            customEndTime = course.customEndTime,
                            remark = course.remark?.takeIf { it.isNotBlank() },
                            credit = course.credit,
                            color = course.colorInt,
                            colorArgb = course.colorArgb,
                        )
                    },
            timeSlots = data.slots.sortedBy { it.number }.map { it.toJson() },
            config =
                CourseTableConfigJson(
                    semesterStartDate = data.startDate?.toString(),
                    semesterTotalWeeks = data.totalWeeks,
                    defaultClassDuration = data.defaultClassDuration,
                ),
            comboSchedule =
                data.combo?.let { combo ->
                    ComboScheduleJson(
                        name = combo.name,
                        publicSchedules = combo.publicSchedules.map { schedule ->
                            PublicScheduleJson(
                                name = schedule.name,
                                startDate = schedule.startDate,
                                endDate = schedule.endDate,
                                defaultClassDuration = schedule.defaultClassDuration,
                                timeSlots = schedule.slots.sortedBy { it.number }.map { it.toJson() },
                            )
                        },
                    )
                },
        ),
    )

private fun TimeSlot.toJson() =
    TimeSlotJson(
        number = number,
        startTime = startTime,
        endTime = endTime,
        alias = alias?.takeIf { it.isNotBlank() },
    )
