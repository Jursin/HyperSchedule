package com.miuix.hyperschedule.data.repository

import androidx.room.withTransaction
import com.miuix.hyperschedule.data.db.AppDatabase
import com.miuix.hyperschedule.data.exporter.ComboScheduleExport
import com.miuix.hyperschedule.data.exporter.PublicScheduleExport
import com.miuix.hyperschedule.data.exporter.TableExportData
import com.miuix.hyperschedule.data.importer.ComboScheduleJson
import com.miuix.hyperschedule.data.importer.CourseJson
import com.miuix.hyperschedule.data.importer.CourseTableJson
import com.miuix.hyperschedule.data.importer.CourseTableConfigJson
import com.miuix.hyperschedule.data.importer.CourseTableJsonParser
import com.miuix.hyperschedule.data.importer.IcsImport
import com.miuix.hyperschedule.data.importer.requireValidForImport
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseTable
import com.miuix.hyperschedule.data.model.CourseTableConfig
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import com.miuix.hyperschedule.data.model.CourseWeek
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.SpecialTimeBlock
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.model.TimeTable
import com.miuix.hyperschedule.data.model.TimeTableCombo
import com.miuix.hyperschedule.data.model.TimeTableComboRule
import com.miuix.hyperschedule.schedule.DEFAULT_SECTION_TIMES
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.semesterStartMonday
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** 今天：跨零点发一次，让按日期生效的组合规则自动切换 */
private val todayFlow: Flow<LocalDate> =
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

/** 课表管理页一行：课表与课程门数 */
data class TableSummary(
    val table: CourseTable,
    val courseCount: Int,
)

/** 课表/课程/作息的数据入口；首启自动建默认课表与专属作息 */
class ScheduleRepository(
    private val db: AppDatabase,
) {
    private val tableDao = db.courseTableDao()
    private val courseDao = db.courseDao()
    private val timeTableDao = db.timeTableDao()
    private val comboDao = db.timeTableComboDao()

    /** 首启初始化：优先沿用 [preferredId]，失效则取最早一张，一张都没有才新建；返回实际课表 ID */
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

    /** 新建课表：元数据 + 配置 + 专属作息 + 默认节次 */
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

    /** 删除课表：至少保留一张；该课表的专属作息、指向它的组合规则与基准一并清理，返回剩余第一张 */
    suspend fun deleteTable(id: String): String? =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                if (tableDao.countTables() <= 1) return@withTransaction null
                purgeSchedule(id)
                tableDao.deleteById(id)
                tableDao.getFirstTableOnce()?.id
            }
        }

    /** 删除作息并解除引用：组合基准、组合规则、课表绑定、特殊课程、节次与方案本身 */
    private suspend fun purgeSchedule(timeTableId: String) {
        comboDao.clearBaseTimeTableId(timeTableId)
        comboDao.deleteRulesByTargetId(timeTableId)
        timeTableDao.deleteBindingsByTargetId(timeTableId)
        timeTableDao.deleteSpecialsByTimeTableId(timeTableId)
        timeTableDao.deleteSlotsByTimeTableId(timeTableId)
        timeTableDao.deleteById(timeTableId)
    }

    fun configOf(courseTableId: String): Flow<CourseTableConfig?> = tableDao.observeConfig(courseTableId)

    /** 设置页改学期信息：写入课表配置，课表页与今日页都以它为准 */
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

    /** 编辑页保存：一次事务内删除被移除的时段，写入（新增或更新）其余时段与周次 */
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

    /** 当前课表生效的节次：无绑定用专属作息，SINGLE 用绑定的表，COMBO 按日期规则对齐基准 */
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

    /** 当前生效的作息表本身：课表页据此取上午/下午/晚上分界 */
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

    /** 第一条命中日期区间的规则；日期为 yyyy-MM-dd，直接按字符串比较 */
    private fun matchRule(rules: List<TimeTableComboRule>, date: LocalDate): String? {
        val today = date.toString()
        return rules.firstOrNull { today in it.startDate..it.endDate }?.targetTimeTableId
    }

    private suspend fun effectiveScheduleIdOnce(courseTableId: String): String =
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

    /** 切换当前课表使用的作息 */
    suspend fun bind(courseTableId: String, targetType: String, targetId: String) =
        withContext(Dispatchers.IO) {
            timeTableDao.upsertBinding(CourseTimeBinding(courseTableId, targetType, targetId))
        }

    // 单一 / 公共作息

    suspend fun loadPlan(planId: String): PlanDetail? =
        withContext(Dispatchers.IO) {
            val plan = timeTableDao.getTimeTableOnce(planId) ?: return@withContext null
            PlanDetail(
                plan = plan,
                slots = timeTableDao.getSlotsOnce(planId),
                specials = timeTableDao.getSpecialsOnce(planId),
            )
        }

    /** 保存作息：方案本身 upsert，节次全量替换，特殊课程按 id 增删改；专属作息保存后即成为当前作息 */
    suspend fun saveSingleSchedule(
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

    /** 新建公共作息草稿：复制当前课表生效作息的设置、节次与特殊课程，保存时才写入 */
    suspend fun newPublicScheduleDraft(courseTableId: String): PlanDetail =
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

    /** 删除公共作息并级联解除引用：组合基准、组合规则、课表绑定 */
    suspend fun deletePublicSchedule(planId: String) =
        withContext(Dispatchers.IO) {
            db.withTransaction { purgeSchedule(planId) }
        }

    // 组合作息

    suspend fun loadCombo(comboId: String): ComboDetail? =
        withContext(Dispatchers.IO) {
            val combo = comboDao.getComboOnce(comboId) ?: return@withContext null
            ComboDetail(combo, comboDao.getRulesOnce(comboId))
        }

    suspend fun publicSchedulesOnce(): List<TimeTable> =
        withContext(Dispatchers.IO) {
            timeTableDao.getPublicTimeTablesOnce()
        }

    /** 新建组合作息草稿：保存时才写入 */
    suspend fun newComboDraft(): ComboDetail =
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

    suspend fun saveCombo(combo: TimeTableCombo, rules: List<TimeTableComboRule>) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                comboDao.upsertCombo(combo)
                comboDao.deleteRulesByComboId(combo.id)
                if (rules.isNotEmpty()) comboDao.insertRules(rules)
            }
        }

    suspend fun deleteCombo(comboId: String) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                timeTableDao.deleteBindingsByTargetId(comboId)
                comboDao.deleteRulesByComboId(comboId)
                comboDao.deleteComboById(comboId)
            }
        }

    // 导入

    /** 导入课程表导出的单表 JSON（覆盖指定课表的课程、作息与组合作息），返回导入的课程数；结构不合法时抛异常拒绝 */
    suspend fun importCourseTableJson(
        text: String,
        tableId: String,
    ): Int =
        withContext(Dispatchers.IO) {
            val data = CourseTableJsonParser.parse(text).also { it.requireValidForImport() }
            importParsed(data, tableId)
        }

    /** 导入 ICS 日历：时间能对上目标课表的节次就还原成节次课程，否则留自定义时间；不覆盖作息，返回导入的课程数 */
    suspend fun importIcs(
        text: String,
        tableId: String,
    ): Int =
        withContext(Dispatchers.IO) {
            val data = IcsImport.parse(text, timeTableDao.getSlotsOnce(tableId)).also { it.requireValidForImport() }
            importParsed(data, tableId)
        }

    /** 分享码 / 口令导入：解析结果与 JSON 文件走同一套落库流程 */
    suspend fun importShareCode(
        payload: CourseTableJson,
        tableId: String,
    ): Int =
        withContext(Dispatchers.IO) {
            payload.requireValidForImport()
            importParsed(payload, tableId)
        }

    /** 两种导入来源共用的落库流程；没有课程的文件不导入，避免把已有课表清空 */
    private suspend fun importParsed(
        data: CourseTableJson,
        tableId: String,
    ): Int {
        require(data.courses.isNotEmpty()) { "文件里没有课程，空课表不导入" }
        db.withTransaction {
            ensureSideTables(tableId)
            importSlots(tableId, data)
            // 文件里没有组合作息（或公共作息为空）就不动现有组合，避免把用户手动配好的组合顺手拆掉
            data.comboSchedule?.takeIf { it.publicSchedules.isNotEmpty() }?.let { importCombo(tableId, it) }
            importCourses(tableId, data.courses)
            data.config?.let { importConfig(tableId, it) }
        }
        return data.courses.size
    }

    /** 文件里的作息覆盖课表专属作息 */
    private suspend fun importSlots(tableId: String, data: CourseTableJson) {
        val slots = data.timeSlots?.takeIf { it.isNotEmpty() } ?: return
        // 绑定原本指向别的公共作息时，写进课表专属作息不会生效，这里把绑定收回本表
        // （文件带了组合就不动，组合导入会自己重设绑定）
        if (data.comboSchedule == null) {
            val binding = timeTableDao.getBindingOnce(tableId)
            if (binding?.targetType == CourseTimeBinding.TARGET_SINGLE && binding.targetId != tableId) {
                timeTableDao.upsertBinding(CourseTimeBinding(tableId, CourseTimeBinding.TARGET_SINGLE, tableId))
            }
        }
        timeTableDao.deleteSlotsByTimeTableId(tableId)
        timeTableDao.upsertSlots(slots.map { TimeSlot(tableId, it.number, it.startTime, it.endTime, it.alias) })
    }

    /** 覆盖课程与周次；颜色按「课程名首次出现顺序」分配，同名同色，超出色板容量时循环取色 */
    private suspend fun importCourses(tableId: String, courses: List<CourseJson>) {
        courseDao.deleteCoursesByTableId(tableId)
        val colorIndexByName = mutableMapOf<String, Int>()
        val entities =
            courses.map { json ->
                val index = colorIndexByName.getOrPut(json.name) { colorIndexByName.size } % Course.COLOR_COUNT
                json.toEntity(tableId, index)
            }
        courseDao.insertAll(entities)
        // 文件里同一周重复出现会撞主键，去重后再写
        val weeks =
            courses.flatMapIndexed { index, course ->
                course.weeks.distinct().map { CourseWeek(entities[index].id, it) }
            }
        if (weeks.isNotEmpty()) courseDao.insertWeeks(weeks)
    }

    /** 学期设置；文件里带单节时长时落到课表专属作息上，导出时会一起写回 config */
    private suspend fun importConfig(tableId: String, config: CourseTableConfigJson) {
        val current = tableDao.getConfigOnce(tableId)
        // 没带开学日期就落到本周一，否则今日页算不出周次、导入后看不到课
        val startDate =
            config.semesterStartDate?.takeIf { parseDateOrNull(it) != null }
                ?: current?.semesterStartDate
                ?: semesterStartMonday(LocalDate.now()).toString()
        tableDao.upsertConfig(
            (current ?: CourseTableConfig(courseTableId = tableId)).copy(
                semesterStartDate = startDate,
                semesterTotalWeeks = config.semesterTotalWeeks,
            )
        )
        config.defaultClassDuration?.let { duration ->
            timeTableDao.getTimeTableOnce(tableId)?.let { timeTableDao.upsert(it.copy(defaultClassDuration = duration)) }
        }
    }

    /** 用文件里的组合覆盖目标课表的组合作息 */
    private suspend fun importCombo(tableId: String, json: ComboScheduleJson) {
        val binding = timeTableDao.getBindingOnce(tableId)
        val bound = binding?.takeIf { it.targetType == CourseTimeBinding.TARGET_COMBO }?.targetId
        val existing = bound?.let { comboDao.getComboOnce(it) }
        val comboId = existing?.id ?: UUID.randomUUID().toString()
        val oldRules = existing?.let { comboDao.getRulesOnce(it.id) }.orEmpty()

        comboDao.upsertCombo(
            TimeTableCombo(
                id = comboId,
                name = json.name?.takeIf { it.isNotBlank() } ?: existing?.name ?: "组合作息",
                baseTimeTableId = null,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
        )
        comboDao.deleteRulesByComboId(comboId)

        val rules =
            json.publicSchedules.mapIndexed { index, schedule ->
                // 同序号的旧公共作息直接复用，重复导入同一文件不会越导越多
                val target = oldRules.getOrNull(index)?.targetTimeTableId?.let { timeTableDao.getTimeTableOnce(it) }
                val targetId = target?.id ?: UUID.randomUUID().toString()
                // 只覆盖文件带得动的字段，时段分隔等本地设置保持不动
                timeTableDao.upsert(
                    (target ?: TimeTable(id = targetId, createdAt = System.currentTimeMillis()))
                        .copy(name = schedule.name, defaultClassDuration = schedule.defaultClassDuration)
                )
                timeTableDao.deleteSlotsByTimeTableId(targetId)
                timeTableDao.upsertSlots(
                    schedule.timeSlots.map { TimeSlot(targetId, it.number, it.startTime, it.endTime, it.alias) }
                )
                TimeTableComboRule(
                    id = UUID.randomUUID().toString(),
                    comboId = comboId,
                    targetTimeTableId = targetId,
                    startDate = schedule.startDate,
                    endDate = schedule.endDate,
                )
            }
        if (rules.isNotEmpty()) comboDao.insertRules(rules)
        timeTableDao.upsertBinding(CourseTimeBinding(tableId, CourseTimeBinding.TARGET_COMBO, comboId))
    }

    /** 导出用的整表快照：组合作息涉及的作息一次取齐，之后按日期在内存里切 */
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
            // 规则引用的作息表可能重复引用，按 id 去重；表本身取不到时退回基准节次
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

    private suspend fun ensureSideTables(tableId: String) {
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

    private fun defaultSlots(timeTableId: String): List<TimeSlot> =
        DEFAULT_SECTION_TIMES.mapIndexed { index, (start, end) ->
            TimeSlot(timeTableId = timeTableId, number = index + 1, startTime = start, endTime = end)
        }

    companion object {
        const val DEFAULT_TABLE_NAME = "我的课表"
    }
}

private fun CourseJson.toEntity(tableId: String, colorIndex: Int) =
    Course(
        id = UUID.randomUUID().toString(),
        courseTableId = tableId,
        name = name,
        teacher = teacher,
        position = position,
        day = day,
        startSection = startSection,
        endSection = endSection,
        isCustomTime = isCustomTime,
        customStartTime = customStartTime,
        customEndTime = customEndTime,
        // 文件里的色板索引超出本应用色板时退回按序分配，避免编辑页一个颜色都选不中
        colorInt = color?.takeIf { it in 0 until Course.COLOR_COUNT } ?: colorIndex,
        colorArgb = colorArgb,
        remark = remark?.take(300),
        credit = credit,
    )
