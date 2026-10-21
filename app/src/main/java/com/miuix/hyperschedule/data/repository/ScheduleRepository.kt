package com.miuix.hyperschedule.data.repository

import androidx.room.withTransaction
import com.miuix.hyperschedule.data.db.AppDatabase
import com.miuix.hyperschedule.data.importer.CourseJson
import com.miuix.hyperschedule.data.importer.CourseTableJsonParser
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseTable
import com.miuix.hyperschedule.data.model.CourseTableConfig
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import com.miuix.hyperschedule.data.model.CourseWeek
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.model.TimeTable
import com.miuix.hyperschedule.schedule.DEFAULT_SECTION_TIMES
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.semesterStartMonday
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** 课表/课程/作息的数据入口；首启自动建默认课表与专属作息 */
class ScheduleRepository(
    private val db: AppDatabase,
) {
    private val tableDao = db.courseTableDao()
    private val configDao = db.courseTableConfigDao()
    private val bindingDao = db.courseTimeBindingDao()
    private val courseDao = db.courseDao()
    private val weekDao = db.courseWeekDao()
    private val timeTableDao = db.timeTableDao()
    private val slotDao = db.timeSlotDao()
    private val comboDao = db.timeTableComboDao()

    /** 首启初始化：没有课表就建一张，缺配置/绑定/作息就补齐；返回当前课表 ID */
    suspend fun ensureInitialized(): String =
        withContext(Dispatchers.IO) {
            val existing = tableDao.getFirstTableOnce()
            if (existing != null) {
                ensureSideTables(existing.id)
                return@withContext existing.id
            }
            val id = UUID.randomUUID().toString()
            db.withTransaction {
                tableDao.insert(CourseTable(id = id, name = DEFAULT_TABLE_NAME, createdAt = System.currentTimeMillis()))
                ensureSideTables(id)
            }
            id
        }

    fun configOf(courseTableId: String): Flow<CourseTableConfig?> = configDao.getConfigById(courseTableId)

    /** 设置页改学期信息：写入课表配置，课表页与今日页都以它为准 */
    suspend fun updateSemesterConfig(
        courseTableId: String,
        startDate: String?,
        totalWeeks: Int,
    ) = withContext(Dispatchers.IO) {
        val current = configDao.getConfigOnce(courseTableId) ?: CourseTableConfig(courseTableId)
        configDao.insertOrUpdate(
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
                weekDao.updateCourseWeeks(
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun timeSlotsOf(courseTableId: String): Flow<List<TimeSlot>> =
        bindingDao.observeBindingByCourseTableId(courseTableId).flatMapLatest { binding ->
            flow {
                val targetId = resolveTarget(binding, courseTableId)
                emitAll(slotDao.getTimeSlotsByTimeTableId(targetId))
            }
        }

    /** 导入课程表导出的单表 JSON（覆盖当前课表的课程与作息），返回导入的课程数 */
    suspend fun importCourseTableJson(text: String): Int =
        withContext(Dispatchers.IO) {
            val tableId = ensureInitialized()
            val data = CourseTableJsonParser.parse(text)
            require(data.courses.isNotEmpty()) { "文件里没有课程" }
            db.withTransaction {
                courseDao.deleteCoursesByTableId(tableId)
                // 颜色按「课程名首次出现顺序」分配，同名同色
                val colorIndexByName = mutableMapOf<String, Int>()
                val entities =
                    data.courses.map { json ->
                        val index = colorIndexByName.getOrPut(json.name) { colorIndexByName.size }
                        json.toEntity(tableId, index)
                    }
                courseDao.insertAll(entities)
                val weeks =
                    data.courses.flatMapIndexed { index, course ->
                        course.weeks.map { CourseWeek(entities[index].id, it) }
                    }
                if (weeks.isNotEmpty()) weekDao.insertAll(weeks)

                data.timeSlots?.takeIf { it.isNotEmpty() }?.let { slots ->
                    val targetId = resolveTarget(bindingDao.getBindingByCourseTableId(tableId), tableId)
                    slotDao.deleteAllTimeSlotsByTimeTableId(targetId)
                    slotDao.insertAll(
                        slots.map { TimeSlot(targetId, it.number, it.startTime, it.endTime, it.alias) }
                    )
                }

                data.config?.let { config ->
                    val current = configDao.getConfigOnce(tableId)
                    // 没带开学日期就落到本周一，否则今日页算不出周次、导入后看不到课
                    val startDate =
                        config.semesterStartDate?.takeIf { parseDateOrNull(it) != null }
                            ?: current?.semesterStartDate
                            ?: semesterStartMonday(LocalDate.now()).toString()
                    configDao.insertOrUpdate(
                        (current ?: CourseTableConfig(courseTableId = tableId)).copy(
                            semesterStartDate = startDate,
                            semesterTotalWeeks = config.semesterTotalWeeks,
                            firstDayOfWeek = config.firstDayOfWeek,
                        )
                    )
                }
            }
            data.courses.size
        }

    /** 绑定的作息目标：单一作息直接用 targetId；组合方案按今天命中的日期规则取，未命中回落基准作息 */
    private suspend fun resolveTarget(binding: CourseTimeBinding?, courseTableId: String): String {
        if (binding == null) return courseTableId
        if (binding.targetType == CourseTimeBinding.TargetType.SINGLE) return binding.targetId
        val combo = comboDao.getComboById(binding.targetId) ?: return courseTableId
        val today = LocalDate.now().toString()
        val rule = comboDao.getRuleListByComboId(combo.id).firstOrNull { today in it.startDate..it.endDate }
        return rule?.targetTimeTableId ?: combo.baseTimeTableId ?: courseTableId
    }

    private suspend fun ensureSideTables(tableId: String) {
        if (configDao.getConfigOnce(tableId) == null) {
            configDao.insertOrUpdate(CourseTableConfig(courseTableId = tableId))
        }
        if (bindingDao.getBindingByCourseTableId(tableId) == null) {
            bindingDao.insertOrUpdate(CourseTimeBinding(courseTableId = tableId, targetId = tableId))
        }
        if (timeTableDao.getTimeTableById(tableId) == null) {
            timeTableDao.insert(TimeTable(id = tableId, createdAt = System.currentTimeMillis()))
        }
        if (slotDao.getTimeSlotsOnceByTimeTableId(tableId).isEmpty()) {
            slotDao.insertAll(defaultSlots(tableId))
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
        colorInt = colorIndex,
        remark = remark?.take(300),
        credit = credit,
    )
