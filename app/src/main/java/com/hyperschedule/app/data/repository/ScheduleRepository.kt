package com.hyperschedule.app.data.repository

import androidx.room.withTransaction
import com.hyperschedule.app.data.db.AppDatabase
import com.hyperschedule.app.data.db.entity.Course
import com.hyperschedule.app.data.db.entity.CourseTable
import com.hyperschedule.app.data.db.entity.CourseTableConfig
import com.hyperschedule.app.data.db.entity.CourseTimeBinding
import com.hyperschedule.app.data.db.entity.CourseWeek
import com.hyperschedule.app.data.db.entity.CourseWithWeeks
import com.hyperschedule.app.data.db.entity.SpecialTimeBlock
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.db.entity.TimeTable
import com.hyperschedule.app.data.db.entity.TimeTableComboRule
import com.hyperschedule.app.data.exporter.ComboScheduleExport
import com.hyperschedule.app.data.exporter.PublicScheduleExport
import com.hyperschedule.app.data.exporter.TableExportData
import com.hyperschedule.app.schedule.DEFAULT_SECTION_TIMES
import com.hyperschedule.app.schedule.parseDateOrNull
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** 课表/课程/作息的数据入口，首启自动建默认课表与专属作息 */
class ScheduleRepository(
    internal val db: AppDatabase,
) {
    internal val tableDao = db.courseTableDao()
    internal val courseDao = db.courseDao()
    internal val timeTableDao = db.timeTableDao()
    internal val comboDao = db.timeTableComboDao()

    /** 首启初始化，优先沿用 [preferredId]，失效则取最早一张，没有才新建 */
    suspend fun ensureInitialized(preferredId: String? = null): String =
        withContext(Dispatchers.IO) {
            val existing = preferredId?.let { tableDao.getTableOnce(it) } ?: tableDao.getFirstTableOnce()
            if (existing != null) {
                ensureSideTables(existing.id)
                return@withContext existing.id
            }
            createTable(DEFAULT_TABLE_NAME)
        }

    // 课表管理

    /** 课表列表与各自的课程门数 */
    fun observeTableSummaries(): Flow<List<TableSummary>> =
        combine(tableDao.observeAll(), courseDao.observeCourseCountsByTable()) { tables, counts ->
            val byTable = counts.associate { it.courseTableId to it.count }
            tables.map { TableSummary(it, byTable[it.id] ?: 0) }
        }

    /** 新建课表，元数据 + 配置 + 专属作息 + 默认节次 */
    suspend fun createTable(name: String): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            db.withTransaction {
                tableDao.insert(CourseTable(id = id, name = name, createdAt = System.currentTimeMillis()))
                ensureSideTables(id)
            }
            id
        }

    suspend fun renameTable(id: String, name: String) =
        withContext(Dispatchers.IO) {
            tableDao.updateName(id, name)
        }

    /** 删除课表，至少保留一张，其专属作息与组合规则一并清理 */
    suspend fun deleteTable(id: String): String? =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                if (tableDao.countTables() <= 1) return@withTransaction null
                purgeSchedule(id)
                tableDao.deleteById(id)
                tableDao.getFirstTableOnce()?.id
            }
        }

    /** 删除作息并解除引用，组合基准、组合规则、课表绑定、特殊课程、节次与方案本身 */
    internal suspend fun purgeSchedule(timeTableId: String) {
        comboDao.clearBaseTimeTableId(timeTableId)
        comboDao.deleteRulesByTargetId(timeTableId)
        timeTableDao.deleteBindingsByTargetId(timeTableId)
        timeTableDao.deleteSpecialsByTimeTableId(timeTableId)
        timeTableDao.deleteSlotsByTimeTableId(timeTableId)
        timeTableDao.deleteById(timeTableId)
    }

    fun configOf(courseTableId: String): Flow<CourseTableConfig?> = tableDao.observeConfig(courseTableId)

    /** 设置页改学期信息，写入课表配置，课表页与今日页都以它为准 */
    suspend fun updateSemesterConfig(
        courseTableId: String,
        startDate: String?,
        totalWeeks: Int,
    ) = withContext(Dispatchers.IO) {
        val current = tableDao.getConfigOnce(courseTableId) ?: CourseTableConfig(courseTableId)
        tableDao.upsertConfig(
            current.copy(semesterStartDate = startDate, semesterTotalWeeks = totalWeeks)
        )
    }

    fun coursesOfDay(courseTableId: String, day: Int, week: Int): Flow<List<CourseWithWeeks>> =
        courseDao.getCoursesWithWeeksByDayAndWeek(courseTableId, day, week)

    fun coursesWithWeeksOfTable(courseTableId: String): Flow<List<CourseWithWeeks>> =
        courseDao.getCoursesWithWeeksByTableId(courseTableId)

    suspend fun existingTableIdOnce(preferredId: String?): String? =
        withContext(Dispatchers.IO) {
            preferredId?.let { tableDao.getTableOnce(it) }?.id ?: tableDao.getFirstTableOnce()?.id
        }

    suspend fun configOnce(courseTableId: String): CourseTableConfig? =
        withContext(Dispatchers.IO) { tableDao.getConfigOnce(courseTableId) }

    suspend fun coursesOnce(courseTableId: String): List<CourseWithWeeks> =
        coursesWithWeeksOfTable(courseTableId).first()

    /** 当前生效的节次（含组合规则的按日期切换），取第一帧即可 */
    suspend fun slotsOnce(courseTableId: String): List<TimeSlot> = timeSlotsOf(courseTableId).first()

    /** 编辑页保存，一次事务内删除被移除的时段，写入（新增或更新）其余时段与周次 */
    suspend fun saveCourseEdits(
        deletedIds: List<String>,
        courses: List<Pair<Course, List<Int>>>,
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (deletedIds.isNotEmpty()) courseDao.deleteCoursesByIds(deletedIds)
            courses.forEach { (course, weekNumbers) ->
                courseDao.upsert(course)
                courseDao.updateCourseWeeks(
                    course.id,
                    weekNumbers.distinct().sorted().map { CourseWeek(course.id, it) },
                )
            }
        }
    }

    /** 删除课程（连带其周次），课程管理页多选删除用 */
    suspend fun deleteCourses(ids: List<String>) =
        withContext(Dispatchers.IO) {
            if (ids.isNotEmpty()) courseDao.deleteCoursesByIds(ids)
        }

    // 作息解析

    /** 当前课表生效的节次，无绑定用专属作息，SINGLE 用绑定的表，COMBO 按日期规则对齐基准 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun timeSlotsOf(courseTableId: String): Flow<List<TimeSlot>> =
        timeTableDao.observeBinding(courseTableId).flatMapLatest { binding ->
            when {
                binding == null -> timeTableDao.observeSlots(courseTableId)
                binding.targetType == CourseTimeBinding.TARGET_SINGLE ->
                    timeTableDao.observeSlots(binding.targetId)
                else -> observeComboSlots(courseTableId, binding.targetId)
            }
        }

    /** 当前生效的是哪张作息表，特殊课程跟着它走 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun effectiveScheduleIdOf(courseTableId: String): Flow<String> =
        timeTableDao.observeBinding(courseTableId).flatMapLatest { binding ->
            when {
                binding == null -> flowOf(courseTableId)
                binding.targetType == CourseTimeBinding.TARGET_SINGLE -> flowOf(binding.targetId)
                else ->
                    combine(
                        comboDao.observeCombo(binding.targetId),
                        comboDao.observeRules(binding.targetId),
                        todayFlow,
                    ) { combo, rules, today ->
                        val baseId = combo?.baseTimeTableId ?: courseTableId
                        matchRule(rules, today) ?: baseId
                    }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun specialBlocksOf(courseTableId: String): Flow<List<SpecialTimeBlock>> =
        effectiveScheduleIdOf(courseTableId).flatMapLatest { scheduleId ->
            timeTableDao.observeSpecials(scheduleId)
        }

    /** 当前生效的作息表本身，课表页据此取上午/下午/晚上分界 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun effectiveTimeTableOf(courseTableId: String): Flow<TimeTable?> =
        effectiveScheduleIdOf(courseTableId).flatMapLatest { id -> timeTableDao.observeTimeTable(id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeComboSlots(courseTableId: String, comboId: String): Flow<List<TimeSlot>> =
        combine(
            comboDao.observeCombo(comboId),
            comboDao.observeRules(comboId),
            todayFlow,
        ) { combo, rules, today -> Triple(combo, rules, today) }
            .flatMapLatest { (combo, rules, today) ->
                val baseId = combo?.baseTimeTableId ?: courseTableId
                val baseSlots = timeTableDao.observeSlots(baseId)
                val targetId = matchRule(rules, today)
                if (targetId == null || targetId == baseId) {
                    baseSlots
                } else {
                    combine(baseSlots, timeTableDao.observeSlots(targetId)) { base, target ->
                        alignSlots(base, target)
                    }
                }
            }

    /** 以 [baseSlots] 的节次为骨架，用 [targetSlots] 的时间覆盖相同序号的节次 */
    private fun alignSlots(baseSlots: List<TimeSlot>, targetSlots: List<TimeSlot>): List<TimeSlot> {
        if (baseSlots.isEmpty()) return targetSlots
        if (targetSlots.isEmpty()) return baseSlots
        val byNumber = targetSlots.associateBy { it.number }
        return baseSlots.map { baseSlot ->
            val targetSlot = byNumber[baseSlot.number]
            if (targetSlot != null) {
                baseSlot.copy(
                    startTime = targetSlot.startTime,
                    endTime = targetSlot.endTime,
                    alias = targetSlot.alias,
                )
            } else {
                baseSlot
            }
        }
    }

    /** 第一条命中日期区间的规则，日期为 yyyy-MM-dd，直接按字符串比较 */
    private fun matchRule(rules: List<TimeTableComboRule>, date: LocalDate): String? {
        val today = date.toString()
        return rules.firstOrNull { today in it.startDate..it.endDate }?.targetTimeTableId
    }

    internal suspend fun effectiveScheduleIdOnce(courseTableId: String): String =
        effectiveScheduleIdOf(courseTableId).first()

    // 作息管理页

    fun overviewOf(courseTableId: String): Flow<ScheduleOverview> =
        combine(
            timeTableDao.observeTimeTable(courseTableId),
            timeTableDao.observePublicTimeTables(),
            comboDao.observeCombos(),
            timeTableDao.observeBinding(courseTableId),
            timeTableDao.observeSlotCounts(),
        ) { exclusive, publics, combos, binding, counts ->
            ScheduleOverview(
                exclusive = exclusive,
                publics = publics,
                combos = combos,
                binding = binding,
                slotCounts = counts.associate { it.timeTableId to it.count },
                ruleCounts = emptyMap(),
            )
        }.combine(comboDao.observeAllRules()) { overview, rules ->
            overview.copy(ruleCounts = rules.groupingBy { it.comboId }.eachCount())
        }

    suspend fun bind(courseTableId: String, targetType: String, targetId: String) =
        withContext(Dispatchers.IO) {
            require(targetType == CourseTimeBinding.TARGET_SINGLE || targetType == CourseTimeBinding.TARGET_COMBO) {
                "未知作息绑定类型"
            }
            require(
                when (targetType) {
                    CourseTimeBinding.TARGET_SINGLE -> timeTableDao.getTimeTableOnce(targetId) != null
                    else -> comboDao.getComboOnce(targetId) != null
                }
            ) {
                "作息不存在"
            }
            timeTableDao.upsertBinding(CourseTimeBinding(courseTableId, targetType, targetId))
        }

    /** 导出用的整表快照，组合作息涉及的作息一次取齐，之后按日期在内存里切 */
    suspend fun loadExportData(courseTableId: String): TableExportData =
        withContext(Dispatchers.IO) {
            val config = tableDao.getConfigOnce(courseTableId) ?: CourseTableConfig(courseTableId)
            val binding = timeTableDao.getBindingOnce(courseTableId)
            val comboId = binding?.takeIf { it.targetType == CourseTimeBinding.TARGET_COMBO }?.targetId
            val combo = comboId?.let { comboDao.getComboOnce(it) }
            val baseId =
                when {
                    combo != null -> combo.baseTimeTableId ?: courseTableId
                    binding != null && binding.targetType == CourseTimeBinding.TARGET_SINGLE -> binding.targetId
                    else -> courseTableId
                }
            val baseSlots = timeTableDao.getSlotsOnce(baseId)
            val baseDuration =
                timeTableDao.getTimeTableOnce(baseId)?.defaultClassDuration ?: TimeTable.DEFAULT_CLASS_DURATION
            val rules = comboId?.let { comboDao.getRulesOnce(it) }.orEmpty()
            // 规则引用的作息表可能重复引用，按 id 去重，表本身取不到时退回基准节次
            val publicTables =
                rules.map { it.targetTimeTableId }.distinct().associateWith { timeTableDao.getTimeTableOnce(it) }
            val slotsByTable =
                publicTables.mapValues { (id, _) -> alignSlots(baseSlots, timeTableDao.getSlotsOnce(id)) } +
                    (baseId to baseSlots)
            TableExportData(
                startDate = config.semesterStartDate?.let(::parseDateOrNull),
                totalWeeks = config.semesterTotalWeeks,
                defaultClassDuration = baseDuration,
                courses = courseDao.getCoursesWithWeeksByTableId(courseTableId).first(),
                slots = baseSlots,
                slotsFor = { date -> slotsByTable[matchRule(rules, date) ?: baseId] ?: baseSlots },
                combo =
                    combo?.let { schedule ->
                        ComboScheduleExport(
                            name = schedule.name,
                            publicSchedules =
                                rules.map { rule ->
                                    val target = publicTables[rule.targetTimeTableId]
                                    PublicScheduleExport(
                                        name = target?.name ?: PublicScheduleExport.DEFAULT_NAME,
                                        startDate = rule.startDate,
                                        endDate = rule.endDate,
                                        defaultClassDuration = target?.defaultClassDuration ?: baseDuration,
                                        slots = slotsByTable[rule.targetTimeTableId].orEmpty(),
                                    )
                                },
                        )
                    },
            )
        }

    internal suspend fun ensureSideTables(tableId: String) {
        if (tableDao.getConfigOnce(tableId) == null) {
            tableDao.upsertConfig(CourseTableConfig(courseTableId = tableId))
        }
        if (timeTableDao.getTimeTableOnce(tableId) == null) {
            timeTableDao.upsert(TimeTable(id = tableId, createdAt = System.currentTimeMillis()))
        }
        if (timeTableDao.getSlotsOnce(tableId).isEmpty()) {
            timeTableDao.upsertSlots(defaultSlots(tableId))
        }
    }

    internal fun defaultSlots(timeTableId: String): List<TimeSlot> =
        DEFAULT_SECTION_TIMES.mapIndexed { index, (start, end) ->
            TimeSlot(timeTableId = timeTableId, number = index + 1, startTime = start, endTime = end)
        }

    companion object {
        const val DEFAULT_TABLE_NAME = "我的课表"
    }
}
