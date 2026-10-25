package com.miuix.hyperschedule.data.importer

import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 课程表单表导出 JSON：courses 必填，timeSlots / config 可缺省；其余字段解析时忽略 */
@Serializable
data class CourseTableJson(
    val courses: List<CourseJson> = emptyList(),
    val timeSlots: List<TimeSlotJson>? = null,
    val config: CourseTableConfigJson? = null,
)

@Serializable
data class CourseJson(
    val name: String,
    val teacher: String = "",
    val position: String = "",
    val day: Int,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val weeks: List<Int> = emptyList(),
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val remark: String? = null,
    val credit: Float? = null,
)

@Serializable
data class TimeSlotJson(
    val number: Int,
    val startTime: String,
    val endTime: String,
    val alias: String? = null,
)

@Serializable
data class CourseTableConfigJson(
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int = DEFAULT_TOTAL_WEEKS,
    val firstDayOfWeek: Int = 1,
)

object CourseTableJsonParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    fun parse(text: String): CourseTableJson = json.decodeFromString(text)
}
