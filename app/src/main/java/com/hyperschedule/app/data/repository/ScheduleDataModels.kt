package com.hyperschedule.app.data.repository

import com.hyperschedule.app.data.db.entity.CourseTable
import com.hyperschedule.app.data.db.entity.CourseTimeBinding
import com.hyperschedule.app.data.db.entity.SpecialTimeBlock
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.db.entity.TimeTable
import com.hyperschedule.app.data.db.entity.TimeTableCombo
import com.hyperschedule.app.data.db.entity.TimeTableComboRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** 今天，跨零点发一次，让按日期生效的组合规则自动切换 */
internal val todayFlow: Flow<LocalDate> =
    flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L).milliseconds)
        }
    }.distinctUntilChanged()

/** 一个作息方案连同节次与特殊课程，编辑页一次读出 */
data class PlanDetail(
    val plan: TimeTable,
    val slots: List<TimeSlot>,
    val specials: List<SpecialTimeBlock>,
)

/** 一个组合作息连同它的日期规则 */
data class ComboDetail(
    val combo: TimeTableCombo,
    val rules: List<TimeTableComboRule>,
)

/** 作息管理页需要的全部数据 */
data class ScheduleOverview(
    val exclusive: TimeTable?,
    val publics: List<TimeTable>,
    val combos: List<TimeTableCombo>,
    val binding: CourseTimeBinding?,
    val slotCounts: Map<String, Int>,
    val ruleCounts: Map<String, Int>,
) {
    /** 该目标是否是当前课表正在用的作息 */
    fun isActive(targetType: String, targetId: String, courseTableId: String): Boolean {
        val current = binding
        return if (current == null) {
            targetType == CourseTimeBinding.TARGET_SINGLE && targetId == courseTableId
        } else {
            current.targetType == targetType && current.targetId == targetId
        }
    }
}

/** 课表管理页一行，课表与课程门数 */
data class TableSummary(
    val table: CourseTable,
    val courseCount: Int,
)
