package com.hyperschedule.app.data.repository

import androidx.room.withTransaction
import com.hyperschedule.app.data.db.entity.Course
import com.hyperschedule.app.data.db.entity.CourseTableConfig
import com.hyperschedule.app.data.db.entity.CourseTimeBinding
import com.hyperschedule.app.data.db.entity.CourseWeek
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.db.entity.TimeTable
import com.hyperschedule.app.data.db.entity.TimeTableCombo
import com.hyperschedule.app.data.db.entity.TimeTableComboRule
import com.hyperschedule.app.data.importer.ComboScheduleJson
import com.hyperschedule.app.data.importer.CourseJson
import com.hyperschedule.app.data.importer.CourseTableConfigJson
import com.hyperschedule.app.data.importer.CourseTableJson
import com.hyperschedule.app.data.importer.CourseTableJsonParser
import com.hyperschedule.app.data.importer.IcsImport
import com.hyperschedule.app.data.importer.TimeSlotJson
import com.hyperschedule.app.data.importer.normalizePosition
import com.hyperschedule.app.data.importer.requireValidForImport
import com.hyperschedule.app.schedule.parseDateOrNull
import com.hyperschedule.app.schedule.semesterStartMonday
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导入单表 JSON 并覆盖指定课表，返回课程数，结构不合法则抛异常 */
suspend fun ScheduleRepository.importCourseTableJson(
    text: String,
    tableId: String,
): Int =
    withContext(Dispatchers.IO) {
        val data = CourseTableJsonParser.parse(text).also { it.requireValidForImport() }
        importParsed(data, tableId)
    }

/** 导入 ICS，能对上节次的还原成节次课程，否则留自定义时间 */
suspend fun ScheduleRepository.importIcs(
    text: String,
    tableId: String,
): Int =
    withContext(Dispatchers.IO) {
        val data = IcsImport.parse(text, timeTableDao.getSlotsOnce(tableId)).also { it.requireValidForImport() }
        importParsed(data, tableId)
    }

/** 分享码/口令导入，解析结果按 JSON 文件那套流程落库 */
suspend fun ScheduleRepository.importShareCode(
    payload: CourseTableJson,
    tableId: String,
): Int =
    withContext(Dispatchers.IO) {
        payload.requireValidForImport()
        importParsed(payload, tableId)
    }

/** 教务脚本导入，颜色按课程名重分配，备注清空，覆盖目标课表 */
suspend fun ScheduleRepository.importEduCourses(
    tableId: String,
    courses: List<CourseJson>,
): Int =
    withContext(Dispatchers.IO) {
        require(courses.isNotEmpty()) { "脚本没有返回课程" }
        // 脚本带的自定义色和备注不采用，颜色统一按课程名分配
        val sanitized = courses.map { it.copy(color = null, colorArgb = null, remark = null) }
        CourseTableJson(courses = sanitized).requireValidForImport()
        db.withTransaction {
            ensureSideTables(tableId)
            importCourses(tableId, sanitized)
        }
        sanitized.size
    }

/** 教务脚本导入的节次时间，覆盖课表专属作息 */
suspend fun ScheduleRepository.importEduTimeSlots(
    tableId: String,
    slots: List<TimeSlotJson>,
) = withContext(Dispatchers.IO) {
    val payload = CourseTableJson(timeSlots = slots)
    payload.requireValidForImport()
    db.withTransaction {
        ensureSideTables(tableId)
        importSlots(tableId, payload)
    }
}

suspend fun ScheduleRepository.importEduConfig(
    tableId: String,
    config: CourseTableConfigJson,
) = withContext(Dispatchers.IO) {
    db.withTransaction {
        ensureSideTables(tableId)
        importConfig(tableId, config)
    }
}

suspend fun ScheduleRepository.importEduCombo(
    tableId: String,
    combo: ComboScheduleJson,
) = withContext(Dispatchers.IO) {
    CourseTableJson(comboSchedule = combo).requireValidForImport()
    db.withTransaction {
        ensureSideTables(tableId)
        importCombo(tableId, combo)
    }
}

/** 两种导入来源共用的落库流程，空文件不导入 */
private suspend fun ScheduleRepository.importParsed(
    data: CourseTableJson,
    tableId: String,
): Int {
    require(data.courses.isNotEmpty()) { "文件里没有课程，空课表不导入" }
    db.withTransaction {
        ensureSideTables(tableId)
        importSlots(tableId, data)
        // 文件里没有组合作息（或公共作息为空）就不动现有组合
        data.comboSchedule?.takeIf { it.publicSchedules.isNotEmpty() }?.let { importCombo(tableId, it) }
        importCourses(tableId, data.courses)
        data.config?.let { importConfig(tableId, it) }
    }
    return data.courses.size
}

/** 文件里的作息覆盖课表专属作息 */
private suspend fun ScheduleRepository.importSlots(tableId: String, data: CourseTableJson) {
    val slots = data.timeSlots?.takeIf { it.isNotEmpty() } ?: return
    // 导入组合时绑定由组合导入重设，这里只在没带组合时收回本表
    if (data.comboSchedule == null) {
        val binding = timeTableDao.getBindingOnce(tableId)
        if (binding?.targetType == CourseTimeBinding.TARGET_SINGLE && binding.targetId != tableId) {
            timeTableDao.upsertBinding(CourseTimeBinding(tableId, CourseTimeBinding.TARGET_SINGLE, tableId))
        }
    }
    timeTableDao.deleteSlotsByTimeTableId(tableId)
    timeTableDao.upsertSlots(slots.map { TimeSlot(tableId, it.number, it.startTime, it.endTime, it.alias) })
}

/** 覆盖课程与周次，颜色按课程名首次出现顺序分配，超出色板循环取色 */
private suspend fun ScheduleRepository.importCourses(tableId: String, courses: List<CourseJson>) {
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

/** 学期设置，文件里带单节时长时落到课表专属作息上，导出时会一起写回 config */
private suspend fun ScheduleRepository.importConfig(tableId: String, config: CourseTableConfigJson) {
    val current = tableDao.getConfigOnce(tableId)
        // 没带开学日期就落到本周一
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
private suspend fun ScheduleRepository.importCombo(tableId: String, json: ComboScheduleJson) {
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
            // 同序号的旧公共作息直接复用
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

private fun CourseJson.toEntity(tableId: String, colorIndex: Int) =
    Course(
        id = UUID.randomUUID().toString(),
        courseTableId = tableId,
        name = name,
        teacher = teacher,
        position = position.normalizePosition(),
        day = day,
        startSection = startSection,
        endSection = endSection,
        isCustomTime = isCustomTime,
        customStartTime = customStartTime,
        customEndTime = customEndTime,
        // 色板索引越界时退回按序分配
        colorInt = color?.takeIf { it in 0 until Course.COLOR_COUNT } ?: colorIndex,
        colorArgb = colorArgb,
        remark = remark?.take(300),
        credit = credit,
    )
