package com.miuix.hyperschedule.data.importer.share

import com.miuix.hyperschedule.data.importer.CourseJson
import com.miuix.hyperschedule.data.importer.CourseTableConfigJson
import com.miuix.hyperschedule.data.importer.CourseTableJson
import com.miuix.hyperschedule.data.importer.TimeSlotJson
import com.miuix.hyperschedule.schedule.formatMinutes
import kotlinx.serialization.Serializable

internal object StarLinkShare {
    private const val BASE_URL = "https://api.starlinkkb.cn/share/curriculum/"

    fun fetch(code: String): CourseTableJson {
        val body =
            try {
                ShareHttp.get(BASE_URL + code)
            } catch (error: ShareHttpException) {
                // 对方用 404 表示口令不存在
                if (error.code == 404) throw IllegalStateException("分享码无效或已过期")
                throw error
            }
        if (body.isBlank()) throw IllegalStateException("星链课表没有返回数据")
        val data =
            runCatching { ShareJson.decodeFromString<StarLinkEnvelope>(body).data }
                .getOrElse { throw IllegalStateException("星链课表的返回格式无法识别") }
                ?: throw IllegalStateException("分享码无效或已过期")

        val totalWeeks = clampTotalWeeks(data.totalWeeks)
        val courses =
            data.courses.orEmpty().mapNotNull { course ->
                CourseJson(
                    name = course.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    teacher = course.teacher.orEmpty(),
                    // 星链的地点统一带 @ 前缀
                    position = course.location.orEmpty().removePrefix("@").trim(),
                    day = course.weekday ?: return@mapNotNull null,
                    startSection = course.startSection ?: 1,
                    endSection = course.endSection ?: course.startSection ?: 1,
                    // 分享码没带周次时按整学期处理
                    weeks =
                        course.weeks.orEmpty().filter { it in 1..totalWeeks }.distinct().sorted()
                            .ifEmpty { (1..totalWeeks).toList() },
                )
            }
        if (courses.isEmpty()) throw IllegalStateException("分享码里没有课程数据")

        val slots =
            continuousSlots(
                data.sectionMinutes.orEmpty().mapNotNull { (key, range) ->
                    val number = key.toIntOrNull() ?: return@mapNotNull null
                    if (range.size < 2) return@mapNotNull null
                    val start = range[0]
                    val end = range[1]
                    if (start !in 0..1440 || end !in 0..1440 || start >= end) return@mapNotNull null
                    TimeSlotJson(number = number, startTime = formatMinutes(start), endTime = formatMinutes(end))
                }
            )

        return CourseTableJson(
            courses = mergeCourses(courses),
            timeSlots = slots.ifEmpty { null },
            config =
                CourseTableConfigJson(
                    semesterStartDate = normalizeDate(data.startDate),
                    semesterTotalWeeks = totalWeeks,
                ),
        )
    }
}

@Serializable
private data class StarLinkEnvelope(
    val data: StarLinkData? = null,
)

@Serializable
private data class StarLinkData(
    val courses: List<StarLinkCourse>? = null,
    val startDate: String? = null,
    val totalWeeks: Int? = null,
    /** 节次 → [开始分钟, 结束分钟] */
    val sectionMinutes: Map<String, List<Int>>? = null,
)

@Serializable
private data class StarLinkCourse(
    val name: String? = null,
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int? = null,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val weeks: List<Int>? = null,
)
