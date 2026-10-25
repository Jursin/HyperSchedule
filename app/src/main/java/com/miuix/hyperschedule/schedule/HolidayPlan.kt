package com.miuix.hyperschedule.schedule

import androidx.compose.runtime.Immutable
import com.miuix.hyperschedule.data.model.HolidayEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 某一天（周次 + 星期）在放假 / 调休规则下怎么画 */
@Immutable
data class HolidayDay(
    /** 放假当天：卡片一律灰显并标「假」 */
    val holiday: Boolean = false,
    /** 补课当天：标「调」 */
    val workSwap: Boolean = false,
    /** 该列要显示哪一天的课；0 = 就是本列星期 */
    val displayDay: Int = 0,
    /** 用哪一周的课；跨周调课时可能与所在页不同，0 = 就是本页周次 */
    val displayWeek: Int = 0,
) {
    /** 补课列显示的是别的星期（甚至别的周）的课 */
    val remapped: Boolean get() = displayDay > 0 && displayWeek > 0

    /** 这一列实际要画的星期；没换列时就是本列 */
    fun dayOr(fallback: Int): Int = if (displayDay > 0) displayDay else fallback

    /** 这一列实际要画的周次；没换周时就是本页 */
    fun weekOr(fallback: Int): Int = if (displayWeek > 0) displayWeek else fallback
}

/**
 * 整个学期的放假 / 调休结论，按（周次, 星期）查表。
 *
 * 课表只存「星期 + 周次」，日期要靠开学日现算，所以这里一次性把整学期算完：
 * - 放假：日期落在假期区间内 → [HolidayDay.holiday]
 * - 调休：当天有补班条目 → [HolidayDay.workSwap]，并按「补哪一天」把该列换成那一天的课
 */
@Immutable
class HolidayPlan private constructor(private val days: Map<Int, HolidayDay>) {
    fun of(week: Int, day: Int): HolidayDay = days[week * 10 + day] ?: HolidayDay()

    companion object {
        val Empty = HolidayPlan(emptyMap())

        fun build(startDate: LocalDate, totalWeeks: Int, entries: List<HolidayEntry>): HolidayPlan {
            if (entries.isEmpty() || totalWeeks <= 0) return Empty
            // 只认学期范围内的条目：同一个节日名跨年各有一段，混在一起会把补课映射算串
            val semesterStart = semesterStartMonday(startDate)
            val semesterEnd = semesterStart.plusWeeks(totalWeeks.toLong()).minusDays(1)
            val scoped =
                entries.filter { entry ->
                    val start = parseDateOrNull(entry.date) ?: return@filter false
                    val end = parseDateOrNull(entry.endDate) ?: start
                    start <= semesterEnd && end >= semesterStart
                }
            if (scoped.isEmpty()) return Empty
            val holidays = holidayDates(scoped, semesterEnd)
            val swaps =
                scoped
                    .filter { it.type == HolidayEntry.TYPE_WORKSWAP }
                    .mapNotNull { entry -> parseDateOrNull(entry.date)?.let { it to entry } }
                    .toMap()
            if (holidays.isEmpty() && swaps.isEmpty()) return Empty
            // 远端条目没有「补哪一天」时按假期块推一个，只用于显示
            val suggested = suggestFollowDates(scoped, semesterEnd)

            val result = HashMap<Int, HolidayDay>(totalWeeks * 7)
            for (week in 1..totalWeeks) {
                weekDatesOf(startDate, week).forEachIndexed { index, date ->
                    val swap = swaps[date]
                    // 优先用条目里的「补哪一天」，远端条目没有就按假期块推出来的建议值
                    val follow = swap?.let { parseDateOrNull(it.followDate) ?: suggested[it.date] }
                    val followWeek = follow?.let { currentWeekOf(startDate, it) } ?: 0
                    val followDay = follow?.dayOfWeek?.value ?: 0
                    // 映射落在学期外时不换列：只标「调」，课还是本列的
                    val remap = followWeek in 1..totalWeeks && followDay in 1..7
                    val flags =
                        HolidayDay(
                            holiday = date in holidays,
                            workSwap = swap != null,
                            displayDay = if (remap) followDay else 0,
                            displayWeek = if (remap) followWeek else 0,
                        )
                    if (flags.holiday || flags.workSwap) result[week * 10 + index + 1] = flags
                }
            }
            return HolidayPlan(result)
        }
    }
}

/** 假期覆盖的每一个日期（区间展开，最多展开到学期末，防止脏数据把区间拉得很长） */
private fun holidayDates(entries: List<HolidayEntry>, semesterEnd: LocalDate): Set<LocalDate> =
    buildSet {
        entries.forEach { entry ->
            if (entry.type == HolidayEntry.TYPE_HOLIDAY) addAll(entry.datesUntil(semesterEnd))
        }
    }

/** 条目覆盖的日期区间；日期非法或区间倒置时为空 */
private fun HolidayEntry.datesUntil(semesterEnd: LocalDate): List<LocalDate> {
    val start = parseDateOrNull(date) ?: return emptyList()
    val end = (parseDateOrNull(endDate) ?: start).coerceAtMost(semesterEnd)
    if (end < start) return emptyList()
    return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
}

/** 补班条目没写「补哪一天」时，按所属假期块推一个日期，只用于显示 */
private fun suggestFollowDates(entries: List<HolidayEntry>, semesterEnd: LocalDate): Map<String, LocalDate> {
    val blocks = LinkedHashMap<String, MutableList<LocalDate>>()
    entries.forEach { entry ->
        if (entry.type != HolidayEntry.TYPE_HOLIDAY) return@forEach
        val days = entry.datesUntil(semesterEnd)
        if (days.isNotEmpty()) blocks.getOrPut(entry.name) { mutableListOf() } += days
    }
    val pending =
        entries.filter { it.type == HolidayEntry.TYPE_WORKSWAP && parseDateOrNull(it.followDate) == null }
    if (blocks.isEmpty() || pending.isEmpty()) return emptyMap()

    // 补班日归属哪段假期：先按名字（去掉「补班 / 调休」后缀）匹配，否则取最近的一段
    val grouped = LinkedHashMap<String, MutableList<Pair<String, LocalDate>>>()
    pending.forEach { swap ->
        val date = parseDateOrNull(swap.date) ?: return@forEach
        val baseName = swap.name.removeSuffix("补班").removeSuffix("调休")
        val blockName =
            blocks.keys.firstOrNull { it == baseName || it.startsWith(baseName) || baseName.startsWith(it) }
                ?: blocks.entries.minByOrNull { distanceTo(date, it.value) }?.key
        if (blockName != null) grouped.getOrPut(blockName) { mutableListOf() } += swap.date to date
    }

    val result = HashMap<String, LocalDate>()
    grouped.forEach { (blockName, swaps) ->
        val days = blocks.getValue(blockName).sorted()
        val lost = days.filter { it.dayOfWeek.value <= 5 }
        if (lost.isEmpty()) return@forEach
        // 假期两侧的补班日都算，越晚的补班日补越靠后的课
        swaps
            .filter { it.second < days.first() || it.second > days.last() }
            .sortedByDescending { it.second }
            .forEachIndexed { index, (key, _) ->
                lost.getOrNull(lost.lastIndex - index)?.let { result[key] = it }
            }
    }
    return result
}

private fun distanceTo(date: LocalDate, days: List<LocalDate>): Long {
    val first = days.min()
    val last = days.max()
    return when {
        date < first -> ChronoUnit.DAYS.between(date, first)
        date > last -> ChronoUnit.DAYS.between(last, date)
        else -> 0L
    }
}
