package com.hyperschedule.app.ui.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.db.entity.SpecialTimeBlock
import com.hyperschedule.app.data.db.entity.TimeTable
import com.hyperschedule.app.schedule.SectionPeriod
import com.hyperschedule.app.schedule.parseMinutes

internal val DividerHeight = 24.dp

/** 特殊节次横带的最小高度，太短的时段也要放得下两行时间 */
private val MinBandHeight = 32.dp

/** 没有节次时长时的兜底分钟数 */
private const val FallbackSectionMinutes = 45

/** 一条特殊节次横带，首尾相接的时段已经合并，[name] 是合并后的名称 */
@Immutable
internal data class SpecialBand(
    val name: String,
    val startTime: String,
    val endTime: String,
    val days: Set<Int>,
    val top: Dp,
    val height: Dp,
)

@Immutable
internal data class BreakDivider(
    val top: Dp,
    val label: String,
)

/** 分界线落在第 [afterSection] 节之后，[label] 是这条缝的名字 */
private data class BreakMark(val afterSection: Int, val label: String)

/** 课表网格纵向几何，特殊节次插在压到的第一节之前并下推后续 */
@Immutable
internal class ScheduleLayout private constructor(
    /** 下标即节次序号（1 起），[topOf] 越界时贴到首/末节 */
    private val sectionTops: List<Dp>,
    /** 下标 0 对应第 1 节，节次的时长越长越高 */
    private val sectionHeights: List<Dp>,
    val height: Dp,
    val dividers: List<BreakDivider>,
    val bands: List<SpecialBand>,
    /** 时间锚点，分钟 → 纵向位置，用于自定义时间课与横带插值 */
    private val anchors: List<Anchor>,
) {
    val sectionCount: Int get() = sectionTops.size - 1

    fun topOf(section: Int): Dp {
        if (sectionTops.size <= 1) return 0.dp
        return sectionTops[section.coerceIn(1, sectionCount)]
    }

    /** 一节占的高度，节次列、「+」格、拖动落点都读它 */
    fun sectionHeightOf(section: Int): Dp {
        if (sectionHeights.isEmpty()) return 0.dp
        return sectionHeights[section.coerceIn(1, sectionCount) - 1]
    }

    /** 跨 [first]..[last] 节的卡片高度，含中间的横带与分界线 */
    fun heightOf(first: Int, last: Int): Dp {
        val safeLast = last.coerceAtLeast(first)
        return (topOf(safeLast) + sectionHeightOf(safeLast) - topOf(first))
            .coerceAtLeast(sectionHeightOf(first))
    }

    /** 纵坐标落在第几节，落在分界线/横带上时归到下面那一节 */
    fun sectionAt(y: Dp): Int {
        for (section in 1..sectionCount) {
            if (y < topOf(section) + sectionHeightOf(section)) return section
        }
        return sectionCount
    }

    /** 分钟 → 纵向位置，作息两端按边缘斜率外推，并夹在网格内 */
    fun minuteToY(minute: Int): Dp = yFor(anchors, minute).coerceIn(0f, height.value).dp

    private data class Anchor(val minute: Int, val y: Float)

    companion object {
        /** 空网格，没有节次时用它，调用方不必到处判空 */
        val Empty =
            ScheduleLayout(
                sectionTops = listOf(0.dp),
                sectionHeights = emptyList(),
                height = 0.dp,
                dividers = emptyList(),
                bands = emptyList(),
                anchors = emptyList(),
            )

        /** 分钟 → 纵向位置，两端按边缘斜率外推 */
        private fun yFor(anchorList: List<Anchor>, minute: Int): Float {
            if (anchorList.isEmpty()) return 0f
            val first = anchorList.first()
            if (minute <= first.minute) return extrapolate(first, anchorList.getOrNull(1), minute)
            val last = anchorList.last()
            if (minute >= last.minute) return extrapolate(last, anchorList.getOrNull(anchorList.size - 2), minute)
            for (index in 1 until anchorList.size) {
                val next = anchorList[index]
                if (minute <= next.minute) {
                    val previous = anchorList[index - 1]
                    val span = next.minute - previous.minute
                    val ratio = if (span > 0) (minute - previous.minute).toFloat() / span else 0f
                    return previous.y + (next.y - previous.y) * ratio
                }
            }
            return last.y
        }

        /** [edge] 是已知锚点，[neighbor] 提供斜率（锚点只有一个时斜率为 0） */
        private fun extrapolate(edge: Anchor, neighbor: Anchor?, minute: Int): Float {
            val other = neighbor ?: return edge.y
            val span = edge.minute - other.minute
            val rate = if (span != 0) (edge.y - other.y) / span else 0f
            return edge.y + (minute - edge.minute) * rate
        }

        /** 按作息与特殊节次算出整套几何，[showBreakDividers] 关闭时不产生分界线 */
        fun build(
            sectionTimes: List<Pair<String, String>>,
            specialBlocks: List<SpecialTimeBlock>,
            morningSplit: String,
            eveningSplit: String,
            hourHeight: Dp,
            showBreakDividers: Boolean = true,
        ): ScheduleLayout {
            val count = sectionTimes.size
            if (count == 0) return Empty

            val breaks = if (showBreakDividers) breakMarks(sectionTimes, morningSplit, eveningSplit) else emptyList()
            val dividerAfter = breaks.mapTo(mutableSetOf()) { it.afterSection }

            // 0. 取一段能算出分钟的作息作兜底，再按每节自己的时长换算高度
            val referenceMinutes =
                sectionTimes.firstNotNullOfOrNull { (start, end) ->
                    val from = parseMinutes(start)
                    val to = parseMinutes(end)
                    if (from != null && to != null) (to - from).takeIf { it > 0 } else null
                } ?: FallbackSectionMinutes
            val minuteDp = hourHeight.value / 60f
            val sectionDp =
                FloatArray(count + 1).also { heights ->
                    for (section in 1..count) {
                        val (start, end) = sectionTimes[section - 1]
                        val from = parseMinutes(start)
                        val to = parseMinutes(end)
                        val minutes = if (from != null && to != null && to > from) to - from else referenceMinutes
                        heights[section] = minutes * minuteDp
                    }
                }

            // 1. 不含横带时各节次的顶，分界缝按 24dp 撑开
            val baseTops = FloatArray(count + 1)
            var cursor = 0f
            for (section in 1..count) {
                baseTops[section] = cursor
                cursor += sectionDp[section]
                if (section in dividerAfter) cursor += DividerHeight.value
            }

            // 2. 节次起止时间作锚点，课间与课外时间按时长比例延伸
            val baseAnchors = buildAnchors(sectionTimes, baseTops, sectionDp)
            val unique = baseAnchors.distinctBy { it.minute }

            // 3. 横带，顶落在哪一节的卡片区间内，就插到那一节前面
            fun anchorSectionOf(rawTop: Float): Int {
                for (section in 1..count) {
                    if (rawTop < baseTops[section] + sectionDp[section]) return section
                }
                return count + 1
            }

            val blocks = mergeBlocks(specialBlocks)
            val rawBands =
                blocks
                    .mapIndexedNotNull { index, block ->
                        val from = parseMinutes(block.startTime) ?: return@mapIndexedNotNull null
                        val to = parseMinutes(block.endTime) ?: return@mapIndexedNotNull null
                        if (to <= from) return@mapIndexedNotNull null
                        val rawTop = yFor(unique, from)
                        val bandHeight = ((to - from) * minuteDp).coerceAtLeast(MinBandHeight.value)
                        RawBand(index, anchorSectionOf(rawTop), rawTop, bandHeight)
                    }
                    .sortedBy { it.rawTop }

            // 4. 从上到下扫一遍，节次与横带依次落位
            val sectionTops = FloatArray(count + 1)
            val bandTops = HashMap<Int, Float>()
            var y = 0f
            for (section in 1..count) {
                rawBands.forEach { band ->
                    if (band.anchorSection == section) {
                        bandTops[band.index] = y
                        y += band.height
                    }
                }
                sectionTops[section] = y
                y += sectionDp[section]
                if (section in dividerAfter) y += DividerHeight.value
            }
            // 末尾不留悬空的分界缝，否则贴底的横带会凭空上移一截
            val baseTotal = if (count in dividerAfter) y - DividerHeight.value else y
            // 锚点落在最后一节之后（count + 1），贴表底依次往下堆
            var tailY = baseTotal
            rawBands.forEach { band ->
                if (band.anchorSection == count + 1) {
                    bandTops[band.index] = tailY
                    tailY += band.height
                }
            }

            val dividers =
                breaks.map { mark ->
                    val section = mark.afterSection
                    BreakDivider(top = (sectionTops[section] + sectionDp[section]).dp, label = mark.label)
                }

            val bands =
                rawBands.map { band ->
                    val block = blocks[band.index]
                    SpecialBand(
                        name = block.name,
                        startTime = block.startTime,
                        endTime = block.endTime,
                        days = block.days,
                        top = (bandTops[band.index] ?: 0f).coerceAtLeast(0f).dp,
                        height = band.height.dp,
                    )
                }

            // 横带把节次推下去之后要按最终位置重算锚点，自定义时间课才和左侧节次列对得上
            val finalAnchors = buildAnchors(sectionTimes, sectionTops, sectionDp).distinctBy { it.minute }

            return ScheduleLayout(
                sectionTops = (0..count).map { sectionTops[it].dp },
                sectionHeights = (1..count).map { sectionDp[it].dp },
                height = maxOf(baseTotal, tailY).dp,
                dividers = dividers,
                bands = bands,
                anchors = finalAnchors.map { Anchor(it.minute, it.y) },
            )
        }

        /** 午休/晚休落在哪两节之间，两侧都得有节次 */
        private fun breakMarks(
            sectionTimes: List<Pair<String, String>>,
            morningSplit: String,
            eveningSplit: String,
        ): List<BreakMark> {
            val count = sectionTimes.size
            val periods = classifyPeriods(sectionTimes, morningSplit, eveningSplit)
            val morningCount = periods.count { it == SectionPeriod.MORNING }
            val afternoonEnd = periods.count { it == SectionPeriod.MORNING || it == SectionPeriod.AFTERNOON }
            return buildList {
                if (morningCount in 1 until count) add(BreakMark(morningCount, "午休"))
                if (afternoonEnd in (morningCount + 1) until count) add(BreakMark(afternoonEnd, "晚休"))
            }
        }

        /** 节次按开始时间落到上午/下午/晚上，未设置时间的节次跟随上一节 */
        private fun classifyPeriods(
            sectionTimes: List<Pair<String, String>>,
            morningSplit: String,
            eveningSplit: String,
        ): List<SectionPeriod> {
            val morningAt = parseMinutes(morningSplit) ?: parseMinutes(TimeTable.DEFAULT_MORNING_SPLIT) ?: 0
            val eveningAt = parseMinutes(eveningSplit) ?: parseMinutes(TimeTable.DEFAULT_EVENING_SPLIT) ?: 0
            var previous = SectionPeriod.MORNING
            return sectionTimes.map { (start, _) ->
                val minute = parseMinutes(start)
                val period =
                    when {
                        minute == null -> previous
                        minute < morningAt -> SectionPeriod.MORNING
                        minute < eveningAt -> SectionPeriod.AFTERNOON
                        else -> SectionPeriod.EVENING
                    }
                previous = period
                period
            }
        }

        private fun buildAnchors(
            sectionTimes: List<Pair<String, String>>,
            baseTops: FloatArray,
            sectionDp: FloatArray,
        ): List<Anchor> {
            val anchors = mutableListOf<Anchor>()
            sectionTimes.forEachIndexed { index, (start, end) ->
                val from = parseMinutes(start)
                val to = parseMinutes(end)
                val top = baseTops.getOrNull(index + 1)
                if (from != null && to != null && top != null) {
                    anchors += Anchor(from, top)
                    anchors += Anchor(to, top + sectionDp.getOrElse(index + 1) { 0f })
                }
            }
            return anchors.sortedBy { it.minute }
        }
    }
}

/** 未定位置的横带，先按原始坐标排序，再扫描落位 */
private data class RawBand(
    val index: Int,
    val anchorSection: Int,
    val rawTop: Float,
    val height: Float,
)

private data class MergedBlock(
    val names: List<String>,
    val startTime: String,
    val endTime: String,
    val days: Set<Int>,
) {
    /** 名称去重后用顿号连起来，两个都叫「早读」的时段不会显示成「早读、早读」 */
    val name: String
        get() = names.filter { it.isNotBlank() }.distinct().joinToString("、")
}

/** 特殊节次合并成横带，时段相同的并成一条，上课星期取并集 */
private fun mergeBlocks(blocks: List<SpecialTimeBlock>): List<MergedBlock> {
    val byTime = LinkedHashMap<Pair<Int, Int>, MutableList<SpecialTimeBlock>>()
    blocks.forEach { block ->
        val start = parseMinutes(block.startTime) ?: return@forEach
        val end = parseMinutes(block.endTime) ?: return@forEach
        if (end <= start) return@forEach
        byTime.getOrPut(start to end) { mutableListOf() } += block
    }
    return byTime.entries
        .sortedBy { it.key.first }
        .map { (_, group) ->
            MergedBlock(
                names = group.map { it.name },
                startTime = group.first().startTime,
                endTime = group.first().endTime,
                days = group.flatMapTo(mutableSetOf()) { it.daySet },
            )
        }
}
