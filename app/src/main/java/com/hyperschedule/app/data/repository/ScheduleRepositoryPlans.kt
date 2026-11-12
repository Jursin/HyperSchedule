package com.hyperschedule.app.data.repository

import androidx.room.withTransaction
import com.hyperschedule.app.data.db.entity.CourseTimeBinding
import com.hyperschedule.app.data.db.entity.SpecialTimeBlock
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.db.entity.TimeTable
import com.hyperschedule.app.data.db.entity.TimeTableCombo
import com.hyperschedule.app.data.db.entity.TimeTableComboRule
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun ScheduleRepository.loadPlan(planId: String): PlanDetail? =
    withContext(Dispatchers.IO) {
        val plan = timeTableDao.getTimeTableOnce(planId) ?: return@withContext null
        PlanDetail(
            plan = plan,
            slots = timeTableDao.getSlotsOnce(planId),
            specials = timeTableDao.getSpecialsOnce(planId),
        )
    }

/** 保存作息，节次全量替换，特殊课程按 id 增删改 */
suspend fun ScheduleRepository.saveSingleSchedule(
    plan: TimeTable,
    slots: List<TimeSlot>,
    specials: List<SpecialTimeBlock>,
    deletedSpecialIds: List<String>,
) = withContext(Dispatchers.IO) {
    db.withTransaction {
        timeTableDao.upsert(plan)
        timeTableDao.deleteSlotsAbove(plan.id, slots.size)
        if (slots.isNotEmpty()) timeTableDao.upsertSlots(slots)
        if (deletedSpecialIds.isNotEmpty()) timeTableDao.deleteSpecialsByIds(deletedSpecialIds)
        if (specials.isNotEmpty()) timeTableDao.upsertSpecials(specials)
        if (plan.isExclusive) {
            timeTableDao.upsertBinding(CourseTimeBinding(plan.id, CourseTimeBinding.TARGET_SINGLE, plan.id))
        }
    }
}

/** 新建公共作息草稿，复制当前课表生效作息的设置、节次与特殊课程，保存时才写入 */
suspend fun ScheduleRepository.newPublicScheduleDraft(courseTableId: String): PlanDetail =
    withContext(Dispatchers.IO) {
        val source = timeTableDao.getTimeTableOnce(effectiveScheduleIdOnce(courseTableId))
        val id = UUID.randomUUID().toString()
        val defaults =
            TimeTable(
                id = id,
                name = "公共作息 ${timeTableDao.getPublicTimeTablesOnce().size + 1}",
                createdAt = System.currentTimeMillis(),
            )
        val plan =
            defaults.copy(
                defaultClassDuration = source?.defaultClassDuration ?: defaults.defaultClassDuration,
                morningAfternoonSplit = source?.morningAfternoonSplit ?: defaults.morningAfternoonSplit,
                afternoonEveningSplit = source?.afternoonEveningSplit ?: defaults.afternoonEveningSplit,
            )
        val sourceSlots = source?.let { timeTableDao.getSlotsOnce(it.id) }.orEmpty()
        val sourceSpecials = source?.let { timeTableDao.getSpecialsOnce(it.id) }.orEmpty()
        PlanDetail(
            plan = plan,
            slots = if (sourceSlots.isEmpty()) defaultSlots(id) else sourceSlots.map { it.copy(timeTableId = id) },
            specials = sourceSpecials.map { it.copy(id = UUID.randomUUID().toString(), timeTableId = id) },
        )
    }

/** 删除公共作息并级联解除引用，组合基准、组合规则、课表绑定 */
suspend fun ScheduleRepository.deletePublicSchedule(planId: String) =
    withContext(Dispatchers.IO) {
        db.withTransaction { purgeSchedule(planId) }
    }

suspend fun ScheduleRepository.loadCombo(comboId: String): ComboDetail? =
    withContext(Dispatchers.IO) {
        val combo = comboDao.getComboOnce(comboId) ?: return@withContext null
        ComboDetail(combo, comboDao.getRulesOnce(comboId))
    }

suspend fun ScheduleRepository.publicSchedulesOnce(): List<TimeTable> =
    withContext(Dispatchers.IO) {
        timeTableDao.getPublicTimeTablesOnce()
    }

/** 新建组合作息草稿，保存时才写入 */
suspend fun ScheduleRepository.newComboDraft(): ComboDetail =
    withContext(Dispatchers.IO) {
        ComboDetail(
            combo =
                TimeTableCombo(
                    id = UUID.randomUUID().toString(),
                    name = "组合作息 ${comboDao.countCombos() + 1}",
                    baseTimeTableId = null,
                    createdAt = System.currentTimeMillis(),
                ),
            rules = emptyList(),
        )
    }

suspend fun ScheduleRepository.saveCombo(combo: TimeTableCombo, rules: List<TimeTableComboRule>) =
    withContext(Dispatchers.IO) {
        db.withTransaction {
            comboDao.upsertCombo(combo)
            comboDao.deleteRulesByComboId(combo.id)
            if (rules.isNotEmpty()) comboDao.insertRules(rules)
        }
    }

suspend fun ScheduleRepository.deleteCombo(comboId: String) =
    withContext(Dispatchers.IO) {
        db.withTransaction {
            timeTableDao.deleteBindingsByTargetId(comboId)
            comboDao.deleteRulesByComboId(comboId)
            comboDao.deleteComboById(comboId)
        }
    }
