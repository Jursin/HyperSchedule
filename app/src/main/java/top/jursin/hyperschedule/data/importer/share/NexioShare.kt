package top.jursin.hyperschedule.data.importer.share

import top.jursin.hyperschedule.data.local.AppLog
import top.jursin.hyperschedule.data.importer.ComboScheduleJson
import top.jursin.hyperschedule.data.importer.CourseJson
import top.jursin.hyperschedule.data.importer.CourseTableConfigJson
import top.jursin.hyperschedule.data.importer.CourseTableJson
import top.jursin.hyperschedule.data.importer.PublicScheduleJson
import top.jursin.hyperschedule.data.importer.SpecialJson
import top.jursin.hyperschedule.data.importer.TimeSlotJson
import top.jursin.hyperschedule.schedule.parseDateOrNull
import java.net.URLEncoder
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object NexioShare {
    private const val BASE_URL = "https://nexioschedule.icu/api/share"

    fun fetch(code: String): ShareCodePayload {
        val body =
            try {
                ShareHttp.get("$BASE_URL?code=${URLEncoder.encode(code, "UTF-8")}")
            } catch (error: ShareHttpException) {
                AppLog.w("ShareImport", "Nexio 分享码请求失败 code=${error.code} body=${error.body.take(200)}")
                throw IllegalStateException(error.readableMessage())
            }
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
        val counts = data.periodCounts()
        val startDate = normalizeDate(settings?.classStartTime)
        // 空节次不落库，其余重新编号；课程遇到空节次就断开，逐段换算成新编号
        val layout = SlotLayout.of(data.times.rawSlots(counts))
        val skipped = mutableListOf<String>()
        val courses =
            data.courses.orEmpty()
                .mapNotNull { it.toCourse(totalWeeks, skipped) }
                .let(::mergeCourses)
                .flatMap { it.remapSections(layout, skipped) }
        if (courses.isEmpty()) throw IllegalStateException("分享口令里没有课程数据")

        return ShareCodePayload(
            table =
                CourseTableJson(
                    courses = courses,
                    timeSlots = layout.timeSlots.ifEmpty { null },
                    // 基础作息的特殊节次取「分享当天生效」那套，与 times 同源
                    specials = data.routines.orEmpty().activeForToday()?.specialBlocks.toSpecials(),
                    config =
                        CourseTableConfigJson(
                            semesterStartDate = startDate,
                            semesterTotalWeeks = totalWeeks,
                        ),
                    comboSchedule = data.routines.orEmpty().toCombo(layout, counts, startDate, totalWeeks),
                ),
            warnings = skipped,
            showNonCurrentWeek = settings?.showNonCurrentWeek,
        )
    }
}

/** 课程备注上限 */
private const val REMARK_MAX = 300

/** 公共作息缺单节时长时的兜底 */
private const val FALLBACK_DURATION = 90

/** 服务端的 error 字段优先，没有才按状态码兜底 */
private fun ShareHttpException.readableMessage(): String {
    val serverError =
        runCatching { ShareJson.decodeFromString<NexioEnvelope>(body).error }.getOrNull()
    return serverError?.takeIf { it.isNotBlank() }
        ?: if (code == 404) "分享口令不存在或已过期" else "服务器返回 HTTP $code"
}

/** 单双周标记，0 每周、1 单周、2 双周 */
private const val WEEK_ODD = 1
private const val WEEK_EVEN = 2

/** 显式周次优先，否则按起止周 + 单双周展开；教学周号原样当学期周用 */
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

/** 转成课程，节次保持原始编号，合并完再按空节次切段 */
private fun NexioCourse.toCourse(
    totalWeeks: Int,
    skipped: MutableList<String>,
): CourseJson? {
    if (name.isBlank()) return null
    val courseName = name.trim()

    if (isCustomTime) {
        val start = normalizeTime(customStartTime)
        val end = normalizeTime(customEndTime)
        if (start != null && end != null && start >= end) {
            skipped += "「$courseName」跨午夜，已跳过"
            return null
        }
    }

    val start = if (isCustomTime) null else (startSection ?: 1)
    val end = if (isCustomTime) null else (endSection ?: startSection ?: 1)
    if (start != null && end != null && start > end) {
        skipped += "「$courseName」的节次范围不正确，已跳过"
        return null
    }

    return CourseJson(
        name = courseName,
        teacher = teacher.trim(),
        position = classroom.trim(),
        day = dayOfWeek,
        startSection = start,
        endSection = end,
        weeks = resolveWeeks(totalWeeks),
        isCustomTime = isCustomTime,
        customStartTime = customStartTime?.takeIf { isCustomTime },
        customEndTime = customEndTime?.takeIf { isCustomTime },
        remark = resolveRemark(),
        colorArgb = colorRes?.toInt(),
    )
}

/** 原始节次区间按空节次切段，一门课可能拆成多条；整段没有有效节次时丢弃 */
private fun CourseJson.remapSections(
    layout: SlotLayout,
    skipped: MutableList<String>,
): List<CourseJson> {
    if (isCustomTime) return listOf(this)
    val start = startSection ?: return listOf(this)
    val end = endSection ?: start
    val runs = layout.remapRuns(start, end)
    if (runs.isEmpty()) {
        skipped += "「$name」所在节次没有时间，已跳过"
        return emptyList()
    }
    return runs.map { (from, to) -> copy(startSection = from, endSection = to) }
}

/** 周备注全同按单条写，不同则拼接，超上限截断 */
private fun NexioCourse.resolveRemark(): String? {
    val base = note.trim()
    val weekly = weekNotes.orEmpty().mapNotNull { (key, value) -> key.toIntOrNull()?.let { it to value.trim() } }
        .filter { it.second.isNotEmpty() }
        .toMap()
    val parts =
        buildList {
            if (base.isNotEmpty()) add(base)
            when {
                weekly.isEmpty() -> Unit
                weekly.values.distinct().size == 1 -> weekly.values.first().takeIf { it != base }?.let(::add)
                else ->
                    add(
                        weekly.entries.sortedBy { it.key }
                            .joinToString("；") { "第${it.key}周：${it.value}" }
                    )
            }
        }
    return parts.joinToString("；").take(REMARK_MAX).takeIf { it.isNotEmpty() }
}

/** 上午/下午节数，决定各时段在全局节次里的起点 */
private data class PeriodCounts(
    val morning: Int,
    val afternoon: Int,
)

private fun NexioScheduleData.periodCounts(): PeriodCounts =
    PeriodCounts(
        // 节次骨架以 settings 为准，缺省时按实际填了时间的条目数推算
        morning = settings?.morningSections?.takeIf { it > 0 } ?: times?.morning?.filledCount() ?: 0,
        afternoon = settings?.afternoonSections?.takeIf { it > 0 } ?: times?.afternoon?.filledCount() ?: 0,
    )

/** 真正有时间的时间段数 */
private fun Map<String, String>.filledCount(): Int =
    count { normalizeTime(it.value.substringBefore('-')) != null }

private data class ShareSlot(
    val number: Int,
    val start: String,
    val end: String,
    val alias: String?,
)

private const val PERIOD_MORNING = "morning"
private const val PERIOD_AFTERNOON = "afternoon"
private const val PERIOD_EVENING = "evening"

private val PeriodNames = setOf(PERIOD_MORNING, PERIOD_AFTERNOON, PERIOD_EVENING)

private fun offsetOf(
    period: String,
    counts: PeriodCounts,
): Int? =
    when (period) {
        PERIOD_MORNING -> 0
        PERIOD_AFTERNOON -> counts.morning
        PERIOD_EVENING -> counts.morning + counts.afternoon
        else -> null
    }

/** 时段内各节的时间，空串是「该节不上课」的占位，直接丢掉 */
private fun Map<String, String>.rawSlots(
    period: String,
    counts: PeriodCounts,
    names: Map<String, String>,
): List<ShareSlot> {
    val offset = offsetOf(period, counts) ?: return emptyList()
    return mapNotNull { (key, value) ->
        val index = key.toIntOrNull() ?: return@mapNotNull null
        val start = normalizeTime(value.substringBefore('-')) ?: return@mapNotNull null
        val end = normalizeTime(value.substringAfter('-', "")) ?: return@mapNotNull null
        ShareSlot(offset + index, start, end, names["${period}_$index"]?.takeIf { it.isNotBlank() })
    }
}

private fun NexioTimes?.rawSlots(counts: PeriodCounts): List<ShareSlot> {
    if (this == null) return emptyList()
    return morning.rawSlots(PERIOD_MORNING, counts, sectionNames) +
        afternoon.rawSlots(PERIOD_AFTERNOON, counts, sectionNames) +
        evening.rawSlots(PERIOD_EVENING, counts, sectionNames)
}

/** 节次骨架：空节次不落库，其余重排成 1..n，并记下原始编号到新编号的映射 */
private class SlotLayout private constructor(
    private val mapping: Map<Int, Int>,
    val timeSlots: List<TimeSlotJson>,
) {
    fun remap(number: Int): Int? = if (mapping.isEmpty()) number else mapping[number]

    fun remapRuns(
        start: Int,
        end: Int,
    ): List<Pair<Int, Int>> {
        if (mapping.isEmpty()) return listOf(start to end)
        val runs = mutableListOf<Pair<Int, Int>>()
        var runStart: Int? = null
        var runEnd: Int? = null
        fun flush() {
            val head = runStart
            val tail = runEnd
            if (head != null && tail != null) runs.add(head to tail)
            runStart = null
            runEnd = null
        }
        for (number in start..end) {
            val mapped = mapping[number]
            if (mapped == null) {
                flush()
            } else {
                if (runStart == null) runStart = mapped
                runEnd = mapped
            }
        }
        flush()
        return runs
    }

    companion object {
        fun of(raw: List<ShareSlot>): SlotLayout {
            val resolved = raw.sortedBy { it.number }.distinctBy { it.number }
            val mapping = resolved.withIndex().associate { (index, slot) -> slot.number to index + 1 }
            val slots =
                resolved.mapIndexed { index, slot ->
                    TimeSlotJson(number = index + 1, startTime = slot.start, endTime = slot.end, alias = slot.alias)
                }
            return SlotLayout(mapping, slots)
        }
    }
}

/** 作息方案里的节次时间，缺某节时用基础作息补 */
private fun NexioRoutine.slots(
    layout: SlotLayout,
    counts: PeriodCounts,
): List<TimeSlotJson> {
    val own =
        sectionTimes.mapNotNull { (key, value) ->
            val period = key.substringBefore('_')
            if (period !in PeriodNames) return@mapNotNull null
            val index = key.substringAfter('_', "").toIntOrNull() ?: return@mapNotNull null
            val number = layout.remap((offsetOf(period, counts) ?: return@mapNotNull null) + index)
                ?: return@mapNotNull null
            val start = normalizeTime(value.substringBefore('-')) ?: return@mapNotNull null
            val end = normalizeTime(value.substringAfter('-', "")) ?: return@mapNotNull null
            TimeSlotJson(number = number, startTime = start, endTime = end, alias = sectionNames[key]?.takeIf { it.isNotBlank() })
        }.associateBy { it.number }
    if (own.isEmpty()) return emptyList()
    return layout.timeSlots.map { base -> own[base.number] ?: base }
}

/** 年内序数值 */
private val NexioRoutine.ordinal: Int
    get() = effectiveMonth.coerceIn(1, 12) * 100 + effectiveDay.coerceIn(1, 31)

/** 分享当天生效的那套作息，早于全部生效日期时回绕到最后一套 */
private fun List<NexioRoutine>.activeForToday(): NexioRoutine? {
    if (isEmpty()) return null
    val today = LocalDate.now().let { it.monthValue * 100 + it.dayOfMonth }
    val sorted = sortedBy { it.ordinal }
    return sorted.lastOrNull { it.ordinal <= today } ?: sorted.last()
}

/** 特殊节次转成一条一个名字 */
private fun List<NexioSpecialBlock>?.toSpecials(): List<SpecialJson>? {
    if (this == null) return null
    return flatMap { block ->
        val start = normalizeTime(block.startTime) ?: return@flatMap emptyList()
        val end = normalizeTime(block.endTime) ?: return@flatMap emptyList()
        if (start >= end) return@flatMap emptyList()
        val blockName = block.name.trim()
        val items = block.items.orEmpty()
        if (items.isEmpty()) {
            if (blockName.isEmpty()) emptyList() else listOf(SpecialJson(blockName, start, end, ""))
        } else {
            items.mapNotNull { item ->
                val days = encodeDays(item.startDay, item.endDay) ?: return@mapNotNull null
                val name = item.name.trim().ifBlank { blockName }
                if (name.isEmpty()) return@mapNotNull null
                SpecialJson(name, start, end, days)
            }
        }
    }
}

private fun encodeDays(
    startDay: Int,
    endDay: Int,
): String? {
    if (startDay !in 1..7 || endDay !in startDay..7) return null
    val days = (startDay..endDay).toSet()
    return if (days.size >= 7) "" else days.sorted().joinToString(",")
}

/** 该作息在指定年份的生效日 */
private fun NexioRoutine.startDateIn(year: Int): LocalDate? {
    val month = effectiveMonth.takeIf { it in 1..12 } ?: return null
    val day = effectiveDay.coerceIn(1, LocalDate.of(year, month, 1).lengthOfMonth())
    return LocalDate.of(year, month, day)
}

/** 多套作息展开成本项目的组合作息 */
private fun List<NexioRoutine>.toCombo(
    layout: SlotLayout,
    counts: PeriodCounts,
    startDate: String?,
    totalWeeks: Int,
): ComboScheduleJson? {
    if (size < 2) return null
    // 没有开学日就不导入多作息
    val semesterStart = startDate?.let(::parseDateOrNull) ?: return null
    val windowEnd = semesterStart.plusDays(totalWeeks * 7L - 1)
    val sorted = sortedBy { it.ordinal }
    // 跨年学期要按年份分别算切换点
    val switches =
        (semesterStart.year..windowEnd.year)
            .flatMap { year -> sorted.mapNotNull { it.startDateIn(year) } }
            .filter { it > semesterStart && it <= windowEnd }
            .distinct()
            .sorted()
    val segmentStarts = listOf(semesterStart) + switches
    val schedules =
        segmentStarts.mapIndexed { index, from ->
            // 早于全部生效日期时回绕到最后一套
            val routine =
                sorted.lastOrNull { it.ordinal <= from.monthValue * 100 + from.dayOfMonth }
                    ?: sorted.last()
            val slots = routine.slots(layout, counts).takeIf { it.isNotEmpty() } ?: layout.timeSlots
            val end = segmentStarts.getOrNull(index + 1)?.minusDays(1) ?: windowEnd
            PublicScheduleJson(
                name = routine.name.ifBlank { "作息方案" },
                startDate = from.toString(),
                endDate = end.toString(),
                defaultClassDuration = routine.classDuration.takeIf { it > 0 } ?: FALLBACK_DURATION,
                timeSlots = slots,
                specials = routine.specialBlocks.toSpecials(),
            )
        }
    if (schedules.size < 2) return null
    return ComboScheduleJson(name = "作息方案", publicSchedules = schedules)
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
    val routines: List<NexioRoutine>? = null,
)

@Serializable
private data class NexioSettings(
    @SerialName("total_weeks") val totalWeeks: Int? = null,
    @SerialName("class_start_time") val classStartTime: String? = null,
    @SerialName("morning_sections") val morningSections: Int? = null,
    @SerialName("afternoon_sections") val afternoonSections: Int? = null,
    @SerialName("show_non_current_week") val showNonCurrentWeek: Boolean? = null,
)

@Serializable
private data class NexioTimes(
    val morning: Map<String, String> = emptyMap(),
    val afternoon: Map<String, String> = emptyMap(),
    val evening: Map<String, String> = emptyMap(),
    @SerialName("section_names") val sectionNames: Map<String, String> = emptyMap(),
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
    val colorRes: Long? = null,
    val note: String = "",
    val weekNotes: Map<String, String>? = null,
)

@Serializable
private data class NexioRoutine(
    val name: String = "",
    val effectiveMonth: Int = 1,
    val effectiveDay: Int = 1,
    val classDuration: Int = 0,
    val sectionTimes: Map<String, String> = emptyMap(),
    val sectionNames: Map<String, String> = emptyMap(),
    val specialBlocks: List<NexioSpecialBlock>? = null,
)

/** 不占节次的固定时段，items 为空表示整块每天生效 */
@Serializable
private data class NexioSpecialBlock(
    val name: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val items: List<NexioSpecialItem>? = null,
)

@Serializable
private data class NexioSpecialItem(
    val name: String = "",
    val startDay: Int = 0,
    val endDay: Int = 0,
)
