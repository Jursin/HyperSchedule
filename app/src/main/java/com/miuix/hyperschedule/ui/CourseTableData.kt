package com.miuix.hyperschedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.miuix.hyperschedule.data.model.CourseTableConfig
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.SpecialTimeBlock
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.model.TimeTable
import com.miuix.hyperschedule.data.repository.ScheduleOverview
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.data.repository.TableSummary
import com.miuix.hyperschedule.schedule.DEFAULT_SECTION_TIMES
import com.miuix.hyperschedule.theme.SettingsStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** 课表级数据：当前课表、配置与生效作息；今日页与课表页共用 */
@Immutable
data class CourseTableData(
    val tableId: String,
    val config: CourseTableConfig?,
    val slots: List<TimeSlot>,
    val specialBlocks: List<SpecialTimeBlock> = emptyList(),
    val morningAfternoonSplit: String = TimeTable.DEFAULT_MORNING_SPLIT,
    val afternoonEveningSplit: String = TimeTable.DEFAULT_EVENING_SPLIT,
) {
    /** 节次时间段，下标即节次序号；导入的作息可能不是 8 节 */
    val sectionTimes: List<Pair<String, String>> =
        if (slots.isEmpty()) DEFAULT_SECTION_TIMES else slots.map { it.startTime to it.endTime }
}

/** 首启初始化并订阅课表配置、作息与特殊课程；初始化完成前返回 null */
@Composable
fun rememberCourseTableData(
    repository: ScheduleRepository,
    store: SettingsStore,
): CourseTableData? {
    val storedId = store.state.currentTableId
    var resolvedId by remember(repository) { mutableStateOf<String?>(null) }
    LaunchedEffect(repository, storedId) {
        val id = repository.ensureInitialized(storedId)
        resolvedId = id
        if (id != storedId) store.update { it.copy(currentTableId = id) }
    }
    val id = storedId ?: resolvedId ?: return null

    // 切换课表时先清空，避免短暂沿用上一张课表的配置与节次
    return produceState<CourseTableData?>(null, repository, id) {
        value = null
        combine(
            repository.configOf(id),
            repository.timeSlotsOf(id),
            repository.specialBlocksOf(id),
            repository.effectiveTimeTableOf(id),
        ) { config, slots, specials, plan ->
            CourseTableData(
                tableId = id,
                config = config,
                slots = slots,
                specialBlocks = specials,
                morningAfternoonSplit = plan?.morningAfternoonSplit.orEmpty(),
                afternoonEveningSplit = plan?.afternoonEveningSplit.orEmpty(),
            )
        }.collect { value = it }
    }.value
}

/** 全部课程及其周次；在常驻作用域里订阅，进入详情页时数据已就绪 */
@Composable
fun rememberCoursesWithWeeks(repository: ScheduleRepository, tableId: String?): List<CourseWithWeeks> {
    val flow = remember(tableId) { tableId?.let { repository.coursesWithWeeksOfTable(it) } ?: flowOf(emptyList()) }
    return flow.collectAsState(emptyList()).value
}

/** 作息管理页的数据：专属、公共、组合、当前绑定 */
@Composable
fun rememberScheduleOverview(repository: ScheduleRepository, tableId: String?): ScheduleOverview? {
    if (tableId == null) return null
    val flow = remember(tableId) { repository.overviewOf(tableId) }
    return flow.collectAsState(null).value
}

/** 课表列表与各自的课程门数 */
@Composable
fun rememberTableSummaries(repository: ScheduleRepository): List<TableSummary> {
    val flow = remember(repository) { repository.observeTableSummaries() }
    return flow.collectAsState(emptyList()).value
}
