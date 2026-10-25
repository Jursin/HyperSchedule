package com.miuix.hyperschedule.ui.page

import com.miuix.hyperschedule.data.repository.PlanDetail
import com.miuix.hyperschedule.schedule.SECTION_COUNT_RANGE
import com.miuix.hyperschedule.schedule.SectionPeriod
import com.miuix.hyperschedule.schedule.parseMinutes

/** 编辑页里的一个节次草稿；起止时间为空串表示未设置 */
internal data class SlotDraft(val start: String, val end: String)

/** 编辑页里的一个特殊节次草稿；[days] 为空时不允许保存 */
internal data class SpecialDraft(
    val id: String,
    val name: String,
    val start: String,
    val end: String,
    val days: Set<Int> = (1..7).toSet(),
)

/** 整页快照，用于判断是否有未保存改动 */
internal data class PlanDraft(
    val name: String,
    val sectionCount: Int,
    val splitMorningAfternoon: String,
    val splitAfternoonEvening: String,
    val classMinutes: Int,
    val slots: List<SlotDraft>,
    val specials: List<SpecialDraft>,
)

/** 编辑页的分组：同一时段的连续节次放一张卡 */
internal data class SectionGroup(val period: SectionPeriod, val indices: List<Int>)

/** 每节课时长：40..100 分钟，5 分钟一档 */
internal val ClassDurationValues = (40..100 step 5).toList()

/** 时段分隔未设置时的兜底时刻，保证节次卡片仍有归属 */
internal const val DefaultMorningSplit = 12 * 60
internal const val DefaultEveningSplit = 18 * 60

/** 特殊课程未设置时间时给的默认区间 */
internal const val DefaultSpecialStart = 7 * 60 + 30
internal const val DefaultSpecialSpan = 20

internal fun SectionPeriod.label(): String =
    when (this) {
        SectionPeriod.MORNING -> "上午"
        SectionPeriod.AFTERNOON -> "下午"
        SectionPeriod.EVENING -> "晚上"
    }

internal fun classDurationIndex(minutes: Int): Int =
    ClassDurationValues.indexOf(minutes).takeIf { it >= 0 }
        ?: ((minutes - ClassDurationValues.first()) / 5).coerceIn(0, ClassDurationValues.lastIndex)

internal fun buildDraft(detail: PlanDetail): PlanDraft {
    val plan = detail.plan
    val count = detail.slots.size.takeIf { it > 0 }?.coerceIn(SECTION_COUNT_RANGE) ?: SECTION_COUNT_RANGE.first
    val byNumber = detail.slots.associateBy { it.number }
    return PlanDraft(
        name = plan.name.orEmpty(),
        sectionCount = count,
        splitMorningAfternoon = plan.morningAfternoonSplit,
        splitAfternoonEvening = plan.afternoonEveningSplit,
        classMinutes = plan.defaultClassDuration,
        slots =
            List(count) { index ->
                val slot = byNumber[index + 1]
                SlotDraft(slot?.startTime.orEmpty(), slot?.endTime.orEmpty())
            },
        specials = detail.specials.map { SpecialDraft(it.id, it.name, it.startTime, it.endTime, it.daySet) },
    )
}

/** 节次按开始时间落到上午/下午/晚上；未设置的节次跟随上一节 */
internal fun groupSections(slots: List<SlotDraft>, splitAM: Int, splitAE: Int): List<SectionGroup> {
    val groups = mutableListOf<SectionGroup>()
    var last = SectionPeriod.MORNING
    slots.forEachIndexed { index, slot ->
        val start = parseMinutes(slot.start)
        val period =
            when {
                start == null -> last
                start < splitAM -> SectionPeriod.MORNING
                start < splitAE -> SectionPeriod.AFTERNOON
                else -> SectionPeriod.EVENING
            }
        last = period
        val tail = groups.lastOrNull()
        if (tail != null && tail.period == period) {
            groups[groups.lastIndex] = tail.copy(indices = tail.indices + index)
        } else {
            groups += SectionGroup(period, listOf(index))
        }
    }
    return groups
}
