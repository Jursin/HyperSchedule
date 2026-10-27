package com.miuix.hyperschedule.data.importer.share

import com.miuix.hyperschedule.data.importer.CourseJson
import com.miuix.hyperschedule.data.importer.CourseTableConfigJson
import com.miuix.hyperschedule.data.importer.CourseTableJson
import com.miuix.hyperschedule.data.importer.TimeSlotJson
import java.net.URLEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object NexioShare {
    private const val BASE_URL = "http://182.92.193.223:3000/api/share"

    fun fetch(code: String): CourseTableJson {
        val body = ShareHttp.get("$BASE_URL?code=${URLEncoder.encode(code, "UTF-8")}")
        if (body.isBlank()) throw IllegalStateException("Nexio 课程表没有返回数据")
        val envelope =
            runCatching { ShareJson.decodeFromString<NexioEnvelope>(body) }
                .getOrElse { throw IllegalStateException("Nexio 课程表的返回格式无法识别") }
        if (!envelope.ok) {
            throw IllegalStateException(envelope.error?.takeIf { it.isNotBlank() } ?: "分享口令无效或已过期")
        }
        val data = envelope.scheduleData ?: throw IllegalStateException("分享口令里没有课表数据")

        val settings = data.settings
        val totalWeeks = clampTotalWeeks(settings?.totalWeeks)
        val courses =
            data.courses.orEmpty().mapNotNull { course ->
                if (course.name.isBlank()) return@mapNotNull null
                CourseJson(
                    name = course.name.trim(),
                    teacher = course.teacher.trim(),
                    position = course.classroom.trim(),
                    day = course.dayOfWeek,
                    startSection = if (course.isCustomTime) null else course.startSection ?: 1,
                    endSection = if (course.isCustomTime) null else course.endSection ?: course.startSection ?: 1,
                    weeks = course.resolveWeeks(totalWeeks),
                    isCustomTime = course.isCustomTime,
                    customStartTime = course.customStartTime?.takeIf { course.isCustomTime },
                    customEndTime = course.customEndTime?.takeIf { course.isCustomTime },
                )
            }
        if (courses.isEmpty()) throw IllegalStateException("分享口令里没有课程数据")

        return CourseTableJson(
            courses = mergeCourses(courses),
            timeSlots = data.times.toSlots(settings).ifEmpty { null },
            config =
                CourseTableConfigJson(
                    semesterStartDate = normalizeDate(settings?.classStartTime),
                    semesterTotalWeeks = totalWeeks,
                ),
        )
    }
}

/** 单双周标记：0 每周、1 单周、2 双周 */
private const val WEEK_ODD = 1
private const val WEEK_EVEN = 2

/** 显式周次优先，否则按起止周 + 单双周展开；都对不上时按整学期处理 */
private fun NexioCourse.resolveWeeks(totalWeeks: Int): List<Int> {
    val maxWeek = totalWeeks.coerceAtLeast(1)
    val selected = selectedWeeks.orEmpty().filter { it in 1..maxWeek }.distinct().sorted()
    if (selected.isNotEmpty()) return selected
    val start = (startWeek ?: 1).coerceIn(1, maxWeek)
    val end = (endWeek ?: maxWeek).coerceIn(start, maxWeek)
    return (start..end)
        .filter { week ->
            when (weekType) {
                WEEK_ODD -> week % 2 == 1
                WEEK_EVEN -> week % 2 == 0
                else -> true
            }
        }.ifEmpty { (1..maxWeek).toList() }
}

/** 拼成全局节次 */
private fun NexioTimes?.toSlots(settings: NexioSettings?): List<TimeSlotJson> {
    if (this == null) return emptyList()
    fun convert(
        period: Map<String, String>,
        offset: Int,
    ): List<TimeSlotJson> =
        period.mapNotNull { (key, value) ->
            val index = key.toIntOrNull() ?: return@mapNotNull null
            val start = normalizeTime(value.substringBefore('-')) ?: return@mapNotNull null
            val end = normalizeTime(value.substringAfter('-', "")) ?: return@mapNotNull null
            TimeSlotJson(number = offset + index, startTime = start, endTime = end)
        }
    val morningCount = settings?.morningSections ?: morning.size
    val afternoonCount = settings?.afternoonSections ?: afternoon.size
    return continuousSlots(
        convert(morning, 0) +
            convert(afternoon, morningCount) +
            convert(evening, morningCount + afternoonCount)
    )
}

@Serializable
private data class NexioEnvelope(
    val ok: Boolean = false,
    val error: String? = null,
    @SerialName("schedule_data") val scheduleData: NexioScheduleData? = null,
)

@Serializable
private data class NexioScheduleData(
    val settings: NexioSettings? = null,
    val times: NexioTimes? = null,
    val courses: List<NexioCourse>? = null,
)

@Serializable
private data class NexioSettings(
    @SerialName("total_weeks") val totalWeeks: Int? = null,
    @SerialName("class_start_time") val classStartTime: String? = null,
    @SerialName("morning_sections") val morningSections: Int? = null,
    @SerialName("afternoon_sections") val afternoonSections: Int? = null,
)

@Serializable
private data class NexioTimes(
    val morning: Map<String, String> = emptyMap(),
    val afternoon: Map<String, String> = emptyMap(),
    val evening: Map<String, String> = emptyMap(),
)

@Serializable
private data class NexioCourse(
    val name: String = "",
    val classroom: String = "",
    val teacher: String = "",
    val dayOfWeek: Int = 1,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val startWeek: Int? = null,
    val endWeek: Int? = null,
    val weekType: Int = 0,
    val selectedWeeks: List<Int>? = null,
)
