package com.miuix.hyperschedule.data.importer

import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.parseMinutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 课程表单表导出 JSON：courses 必填，timeSlots / config / comboSchedule 可缺省；其余字段解析时忽略 */
@Serializable
data class CourseTableJson(
    val courses: List<CourseJson> = emptyList(),
    val timeSlots: List<TimeSlotJson>? = null,
    val config: CourseTableConfigJson? = null,
    val comboSchedule: ComboScheduleJson? = null,
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
    val color: Int? = null, // 色板索引
    val colorArgb: Int? = null, // 自定义色
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
    val defaultClassDuration: Int? = null,
)

/** 组合作息：基准作息之外，每条日期规则各带一套公共作息 */
@Serializable
data class ComboScheduleJson(
    val name: String? = null,
    val publicSchedules: List<PublicScheduleJson> = emptyList(),
)

/** 公共作息缺单节时长时的兜底，与拾光课程表的默认值一致 */
private const val FALLBACK_CLASS_DURATION = 45

@Serializable
data class PublicScheduleJson(
    val name: String,
    val startDate: String,
    val endDate: String,
    val defaultClassDuration: Int = FALLBACK_CLASS_DURATION,
    val timeSlots: List<TimeSlotJson> = emptyList(),
)

object CourseTableJsonParser {
    private val decoder =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    private val encoder =
        Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }

    fun parse(text: String): CourseTableJson = decoder.decodeFromString(text)

    fun encode(value: CourseTableJson): String = encoder.encodeToString(value)
}

/** 严格 HH:mm */
private val TimePattern = Regex("(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]")

/**
 * 导入前的结构校验，不合法直接抛 [IllegalArgumentException]，
 * 由调用方把 message 提示给用户，整个文件拒绝导入。
 */
fun CourseTableJson.requireValidForImport() {
    courses.forEach {
        it.requireValidPlacement()
        it.requireValidCustomTime()
    }
    timeSlots.orEmpty().requireValidSlots()
    comboSchedule?.publicSchedules?.forEach { it.requireValidSchedule() }
}

/** 星期与节次落在能显示出来的范围内：坏数据导入后会变成永远看不到的课 */
private fun CourseJson.requireValidPlacement() {
    require(day in 1..7) { "「$name」的上课星期必须是 1~7" }
    if (isCustomTime) return
    val start = startSection ?: return
    val end = endSection ?: return
    require(start in 1..end) { "「$name」的节次范围不正确" }
}

/** 自定义上课时间必须非空、格式合法且开始早于结束 */
private fun CourseJson.requireValidCustomTime() {
    if (!isCustomTime) return
    val start = customStartTime
    val end = customEndTime
    require(!start.isNullOrBlank() && !end.isNullOrBlank()) { "「$name」缺少自定义上课时间" }
    require(TimePattern.matches(start) && TimePattern.matches(end)) { "「$name」的自定义时间格式不正确，应为 HH:mm" }
    val startMinutes = parseMinutes(start)
    val endMinutes = parseMinutes(end)
    require(startMinutes != null && endMinutes != null && startMinutes < endMinutes) {
        "「$name」的自定义开始时间必须早于结束时间"
    }
}

/** 节次必须从 1 连续编号、时间格式合法、开始早于结束、相邻不重叠 */
private fun List<TimeSlotJson>.requireValidSlots() {
    if (isEmpty()) return
    var lastEnd = -1
    sortedBy { it.number }.forEachIndexed { index, slot ->
        require(slot.number == index + 1) { "节次编号不连续，必须从 1 开始依次递增" }
        require(TimePattern.matches(slot.startTime) && TimePattern.matches(slot.endTime)) {
            "第${slot.number}节的时间格式不正确，应为 HH:mm"
        }
        val start = parseMinutes(slot.startTime)
        val end = parseMinutes(slot.endTime)
        require(start != null && end != null && start < end) { "第${slot.number}节的开始时间必须早于结束时间" }
        require(lastEnd < 0 || start >= lastEnd) { "第${slot.number}节与上一节的时间重叠" }
        lastEnd = end
    }
}

/** 组合作息里的一节公共作息：日期区间可解析且不倒挂，节次同样要过校验 */
private fun PublicScheduleJson.requireValidSchedule() {
    val start = parseDateOrNull(startDate)
    val end = parseDateOrNull(endDate)
    require(start != null && end != null) { "「$name」的生效日期格式不正确，应为 yyyy-MM-dd" }
    require(!end.isBefore(start)) { "「$name」的生效结束日期不能早于开始日期" }
    timeSlots.requireValidSlots()
}
