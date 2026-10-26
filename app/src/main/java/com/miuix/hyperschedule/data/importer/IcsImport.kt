package com.miuix.hyperschedule.data.importer

import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.MAX_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.currentWeekOf
import com.miuix.hyperschedule.schedule.formatMinutes
import com.miuix.hyperschedule.schedule.semesterStartMonday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 把 ICS 日历反解成本应用的课表结构 */
object IcsImport {
    private val DateTimePattern = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val ShortDateTimePattern = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
    private val DatePattern = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val WeekPattern = Regex("""第\s*(\d+)\s*周""")
    private const val UTC_OFFSET_HOURS = 8L // Asia/Shanghai 无夏令时，固定偏移即可

    /** 解析失败或没有可导入课程时抛 [IllegalArgumentException]，由调用方提示用户 */
    fun parse(
        text: String,
        slots: List<TimeSlot> = emptyList(),
    ): CourseTableJson {
        val events = readEvents(text)
        require(events.isNotEmpty()) { "文件里没有找到课程事件" }

        val startMonday = resolveStartMonday(events)
        val grouped = LinkedHashMap<LessonKey, MutableList<IcsEvent>>()
        events.forEach { event ->
            // 描述里没有「第N周」时按开学周反推；早于开学的事件会落到第 0 周，下面一并过滤
            val week = event.week ?: currentWeekOf(startMonday, event.date)
            if (week !in 1..MAX_TOTAL_WEEKS) return@forEach
            val key =
                LessonKey(
                    name = event.name,
                    location = event.location,
                    day = event.date.dayOfWeek.value,
                    startMinutes = event.startMinutes,
                    endMinutes = event.endMinutes,
                )
            grouped.getOrPut(key) { mutableListOf() } += event.copy(week = week)
        }
        require(grouped.isNotEmpty()) { "文件里的课程事件都不在有效学期范围内" }

        val courses =
            grouped.map { (key, list) ->
                // 时间正好对得上目标课表的节次时还原成节次课程，对不上才留自定义时间
                val sections = matchSections(slots, key.startMinutes, key.endMinutes)
                CourseJson(
                    name = key.name,
                    position = key.location,
                    day = key.day,
                    startSection = sections?.first,
                    endSection = sections?.second,
                    weeks = list.mapNotNull { it.week }.toSortedSet().toList(),
                    isCustomTime = sections == null,
                    customStartTime = if (sections == null) formatMinutes(key.startMinutes) else null,
                    customEndTime = if (sections == null) formatMinutes(key.endMinutes) else null,
                    remark = list.firstNotNullOfOrNull { it.remark() },
                )
            }
        val totalWeeks = courses.flatMap { it.weeks }.max()

        return CourseTableJson(
            courses = courses.sortedWith(compareBy({ it.day }, { it.customStartTime }, { it.name })),
            config =
                CourseTableConfigJson(
                    semesterStartDate = startMonday.toString(),
                    semesterTotalWeeks = totalWeeks,
                ),
        )
    }

    /** 展开 RFC 5545 的折行：以空格或制表符开头的行接在上一行后面 */
    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<StringBuilder>()
        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            if (line.startsWith(" ") || line.startsWith("\t")) {
                lines.lastOrNull()?.append(line.substring(1))
            } else {
                lines += StringBuilder(line)
            }
        }
        return lines.map { it.toString() }
    }

    /** 只收 VEVENT 直接层级的属性，VALARM 里的 DESCRIPTION 不能盖掉事件自己的描述 */
    private fun readEvents(text: String): List<IcsEvent> {
        val events = mutableListOf<IcsEvent>()
        var inEvent = false
        var inAlarm = false
        var properties = mutableMapOf<String, MutableList<Pair<String, String>>>()

        unfold(text).forEach { line ->
            when (line.trim().uppercase()) {
                "BEGIN:VEVENT" -> {
                    inEvent = true
                    properties = mutableMapOf()
                }
                "END:VEVENT" -> {
                    inEvent = false
                    events += toEvents(properties)
                }
                "BEGIN:VALARM" -> inAlarm = true
                "END:VALARM" -> inAlarm = false
                else -> {
                    if (!inEvent || inAlarm) return@forEach
                    val colon = line.indexOf(':')
                    if (colon <= 0) return@forEach
                    val head = line.substring(0, colon)
                    // head 形如 DTSTART;TZID=Asia/Shanghai，冒号前的内容整体留着判参数
                    val name = head.substringBefore(';').uppercase()
                    properties.getOrPut(name) { mutableListOf() } += head to line.substring(colon + 1)
                }
            }
        }
        return events
    }

    private fun toEvents(properties: Map<String, List<Pair<String, String>>>): List<IcsEvent> {
        val name = properties.firstValue("SUMMARY")?.let(::unescape)?.trim().orEmpty()
        if (name.isEmpty()) return emptyList()
        val startProperty = properties.getName("DTSTART") ?: return emptyList()
        val endProperty = properties.getName("DTEND") ?: return emptyList()
        val start = parseDateTime(startProperty.first, startProperty.second) ?: return emptyList()
        val end = parseDateTime(endProperty.first, endProperty.second) ?: return emptyList()
        // 跨天与全天事件不是正常一节课，直接跳过
        if (end.toLocalDate() != start.toLocalDate()) return emptyList()
        val startMinutes = start.hour * 60 + start.minute
        val endMinutes = end.hour * 60 + end.minute
        if (endMinutes <= startMinutes) return emptyList()
        val detail = properties.firstValue("DESCRIPTION")?.let(::unescape).orEmpty().trim()

        val base =
            IcsEvent(
                name = name,
                location = properties.firstValue("LOCATION")?.let(::unescape)?.trim().orEmpty(),
                detail = detail,
                date = start.toLocalDate(),
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                week = WeekPattern.find(detail)?.groupValues?.get(1)?.toIntOrNull(),
            )
        return expandRepeat(base, properties.firstValue("RRULE"), excludedDates(properties))
    }

    /** 展开每周重复规则 */
    private fun expandRepeat(base: IcsEvent, rule: String?, excluded: Set<LocalDate>): List<IcsEvent> {
        if (rule.isNullOrBlank()) return listOf(base)
        val parts =
            rule.split(';').mapNotNull { token ->
                val at = token.indexOf('=')
                if (at <= 0) null else token.substring(0, at).trim().uppercase() to token.substring(at + 1).trim()
            }.toMap()
        if (parts["FREQ"]?.uppercase() != "WEEKLY") return listOf(base)

        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()?.coerceIn(1, MAX_TOTAL_WEEKS)
        val until = parts["UNTIL"]?.let { parseDateTime("UNTIL", it)?.toLocalDate() }
        // 既没有 COUNT 也没有 UNTIL 说明是无限重复，按一个默认学期展开
        val limit = count ?: if (until == null) DEFAULT_TOTAL_WEEKS else MAX_TOTAL_WEEKS

        val result = mutableListOf<IcsEvent>()
        var date = base.date
        var generated = 0
        while (generated < limit) {
            if (until != null && date.isAfter(until)) break
            if (date !in excluded) result += base.copy(date = date)
            generated++
            date = date.plusWeeks(interval.toLong())
        }
        return result
    }

    /** EXDATE：被排除的日期（假期、调休等），可能有多行，每行逗号分隔；可能只写日期，也可能带时刻 */
    private fun excludedDates(properties: Map<String, List<Pair<String, String>>>): Set<LocalDate> =
        properties["EXDATE"].orEmpty().flatMap { (head, value) ->
            value.split(',').mapNotNull { token ->
                if (isDateOnly(head)) {
                    runCatching { LocalDate.parse(token.trim(), DatePattern) }.getOrNull()
                } else {
                    parseDateTime(head, token)?.toLocalDate()
                }
            }
        }.toSet()

    /** 带 VALUE=DATE 的是全天事件/纯日期，没有时刻 */
    private fun isDateOnly(head: String): Boolean = head.contains("VALUE=DATE", ignoreCase = true)

    private fun parseDateTime(head: String, rawValue: String): LocalDateTime? {
        if (isDateOnly(head)) return null
        val value = rawValue.trim()
        val utc = value.endsWith("Z", ignoreCase = true)
        val core = if (utc) value.dropLast(1) else value
        val parsed =
            runCatching { LocalDateTime.parse(core, DateTimePattern) }.getOrNull()
                ?: runCatching { LocalDateTime.parse(core, ShortDateTimePattern) }.getOrNull()
                ?: return null
        // 其他时区一律按字面时间处理：跨时区换算需要完整的 VTIMEZONE 数据，收益不大
        return if (utc) parsed.plusHours(UTC_OFFSET_HOURS) else parsed
    }

    /** 开学周：有「第N周」的事件能直接反推出第 1 周的周一，取出现最多的那天 */
    private fun resolveStartMonday(events: List<IcsEvent>): LocalDate {
        val candidates =
            events.mapNotNull { event ->
                event.week?.let { semesterStartMonday(event.date.minusWeeks(it.toLong())) }
            }
        if (candidates.isEmpty()) return semesterStartMonday(events.minOf { it.date })
        return candidates
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<LocalDate, Int>> { it.value }.thenBy { it.key })
            .first()
            .key
    }

    /** 起止时刻正好落在课表节次的边界上时返回 (开始节次, 结束节次)，否则 null */
    private fun matchSections(
        slots: List<TimeSlot>,
        startMinutes: Int,
        endMinutes: Int,
    ): Pair<Int, Int>? {
        if (slots.isEmpty()) return null
        val first = slots.firstOrNull { it.startTime == formatMinutes(startMinutes) } ?: return null
        val last = slots.firstOrNull { it.endTime == formatMinutes(endMinutes) } ?: return null
        return if (first.number <= last.number) first.number to last.number else null
    }

    /** 描述里去掉「第N周」剩下的部分（我们导出的格式是「第N周 · 教师 · 备注」） */
    private fun IcsEvent.remark(): String? =
        detail
            .replace(WeekPattern, "")
            .split('·')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
            .ifBlank { null }

    /** ICS 文本转义：\, \; \\ \n */
    private fun unescape(text: String): String {
        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\' && index + 1 < text.length) {
                when (val next = text[index + 1]) {
                    'n', 'N' -> builder.append('\n')
                    else -> builder.append(next)
                }
                index += 2
            } else {
                builder.append(char)
                index++
            }
        }
        return builder.toString()
    }

    private fun Map<String, List<Pair<String, String>>>.getName(name: String): Pair<String, String>? =
        this[name]?.firstOrNull()

    private fun Map<String, List<Pair<String, String>>>.firstValue(name: String): String? = getName(name)?.second
}

private data class LessonKey(
    val name: String,
    val location: String,
    val day: Int,
    val startMinutes: Int,
    val endMinutes: Int,
)

private data class IcsEvent(
    val name: String,
    val location: String,
    val detail: String,
    val date: LocalDate,
    val startMinutes: Int,
    val endMinutes: Int,
    val week: Int? = null,
)
