package com.miuix.hyperschedule.data.importer.share

import com.miuix.hyperschedule.data.importer.CourseJson
import com.miuix.hyperschedule.data.importer.CourseTableJson
import com.miuix.hyperschedule.data.importer.TimeSlotJson
import com.miuix.hyperschedule.data.importer.requireValidForImport
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.MAX_TOTAL_WEEKS
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 分享码来源：三种第三方课表的口令导入 */
enum class ShareCodeSource(
    val label: String,
    val codeName: String,
    val hint: String,
) {
    WakeUp("WakeUp 课程表", "分享口令", "粘贴分享口令或整段分享文本"),
    StarLink("星链课表", "分享码", "粘贴分享码或整段分享文本"),
    Nexio("Nexio 课程表", "分享口令", "粘贴分享口令"),
}

/** 统一用同一套宽松解码 */
internal val ShareJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

object ShareCodeImport {

    private val WakeUpLabelled =
        Regex("分享口令为?\\s*[:：]?\\s*[「“\"]?\\s*([A-Za-z0-9_-]{1,200})", RegexOption.IGNORE_CASE)
    private val WrappedCode = Regex("[「“\"]\\s*([A-Za-z0-9_-]{8,200})\\s*[」”\"]")
    private val WakeUpKey = Regex("[A-Za-z0-9_-]{8,200}")
    private val StarLinkStructured = Regex("输入：\\s*([^（\\s]+)")
    private val StarLinkCode = Regex("[A-Za-z0-9-]{5,20}")

    /** 取回并解析分享码；网络与格式错误都以带可读 message 的异常抛出 */
    suspend fun fetch(
        source: ShareCodeSource,
        input: String,
    ): CourseTableJson =
        withContext(Dispatchers.IO) {
            val code = extractCode(source, input)
            require(code.isNotBlank()) { "没有识别到${source.label}的${source.codeName}" }
            val payload =
                when (source) {
                    ShareCodeSource.WakeUp -> WakeUpShare.fetch(code)
                    ShareCodeSource.StarLink -> StarLinkShare.fetch(code)
                    ShareCodeSource.Nexio -> NexioShare.fetch(code)
                }
            payload.also { it.requireValidForImport() }
        }

    /** 从整段分享文本里挑出口令本体，识别不出时按原文处理 */
    private fun extractCode(
        source: ShareCodeSource,
        input: String,
    ): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        return when (source) {
            ShareCodeSource.WakeUp -> extractWakeUpKey(text)
            ShareCodeSource.StarLink -> extractStarLinkCode(text)
            ShareCodeSource.Nexio -> text.trim('「', '」', '“', '”', '"', '\'').substringBefore(' ').trim()
        }
    }

    private fun extractWakeUpKey(text: String): String {
        WakeUpLabelled.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }
        WrappedCode.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }
        // 分享文本里口令通常排在最后，取最后一个长串比取第一个靠谱
        return WakeUpKey.findAll(text).lastOrNull()?.value ?: text
    }

    private fun extractStarLinkCode(text: String): String =
        StarLinkStructured.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: StarLinkCode.find(text)?.value?.trim()
            ?: text
}

/** 学期总周数收敛到本应用能接受的区间 */
internal fun clampTotalWeeks(weeks: Int?): Int =
    (weeks ?: DEFAULT_TOTAL_WEEKS).coerceIn(1, MAX_TOTAL_WEEKS)

private val EXTERNAL_DATE = DateTimeFormatter.ofPattern("u-M-d")

/** 日期统一成 yyyy-MM-dd；识别不了返回 null（交给仓库层回落到本周一） */
internal fun normalizeDate(raw: String?): String? {
    val text =
        raw?.trim()?.substringBefore(' ')?.substringBefore('T')
            ?.replace('/', '-')?.replace('.', '-')
            ?: return null
    if (text.isEmpty()) return null
    return runCatching { LocalDate.parse(text, EXTERNAL_DATE) }.getOrNull()?.toString()
}

/** 统一成两段补零写法 */
internal fun normalizeTime(raw: String?): String? {
    val parts = raw?.trim()?.split(':') ?: return null
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return "%02d:%02d".format(hour, minute)
}

/**
 * 节次编号必须从 1 连续，否则整份作息会被校验整体拒绝。
 * 不连续时宁可不带作息导入课程，也不让整个文件被拒。
 */
internal fun continuousSlots(slots: List<TimeSlotJson>): List<TimeSlotJson> {
    val sorted = slots.sortedBy { it.number }
    return if (sorted.withIndex().all { (index, slot) -> slot.number == index + 1 }) sorted else emptyList()
}

/** 合并重复课程：去掉首尾空白后按节次与周次归并，自定义时间的课程原样保留 */
internal fun mergeCourses(courses: List<CourseJson>): List<CourseJson> {
    val normalized =
        courses.map {
            it.copy(
                name = it.name.trim(),
                teacher = it.teacher.trim(),
                position = it.position.trim(),
                weeks = it.weeks.distinct().sorted(),
            )
        }
    return mergeSectionCourses(normalized.filterNot { it.isCustomTime }) + normalized.filter { it.isCustomTime }
}

/** 课程相同的判定维度，两轮归并的排序都以它开头 */
private val CourseKey = compareBy<CourseJson>({ it.name }, { it.teacher }, { it.position }, { it.day })

private fun mergeSectionCourses(courses: List<CourseJson>): List<CourseJson> {
    if (courses.size <= 1) return courses

    // 第一步：同课程周次相同且节次相连的合成一条
    val byWeeks = courses.sortedWith(CourseKey.thenBy { it.weeks.joinToString(",") }.thenBy { it.startSection ?: 0 })
    val joined = mutableListOf<CourseJson>()
    var current = byWeeks.first()
    for (index in 1 until byWeeks.size) {
        val next = byWeeks[index]
        val sameWeeks = sameCourse(current, next) && current.weeks == next.weeks
        val joinedSections =
            current.endSection != null && next.startSection != null && current.endSection + 1 == next.startSection
        val sameSections = current.startSection == next.startSection && current.endSection == next.endSection
        when {
            sameWeeks && joinedSections -> current = current.copy(endSection = next.endSection)
            sameWeeks && sameSections -> Unit
            else -> {
                joined += current
                current = next
            }
        }
    }
    joined += current

    // 第二步：同课程同节次的周次取并集
    val bySection = joined.sortedWith(CourseKey.thenBy { it.startSection ?: 0 }.thenBy { it.endSection ?: 0 })
    val merged = mutableListOf<CourseJson>()
    var head = bySection.first()
    for (index in 1 until bySection.size) {
        val next = bySection[index]
        val sameSection =
            sameCourse(head, next) && head.startSection == next.startSection && head.endSection == next.endSection
        if (sameSection) {
            head = head.copy(weeks = (head.weeks + next.weeks).distinct().sorted())
        } else {
            merged += head
            head = next
        }
    }
    merged += head
    return merged
}

private fun sameCourse(
    a: CourseJson,
    b: CourseJson,
): Boolean =
    a.name == b.name && a.teacher == b.teacher && a.position == b.position && a.day == b.day
