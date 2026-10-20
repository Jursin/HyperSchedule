package com.miuix.hyperschedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.miuix.hyperschedule.data.model.CourseTableConfig
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.schedule.DEFAULT_SECTION_TIMES
import kotlinx.coroutines.flow.flowOf

/** 课表级数据：当前课表、配置与生效作息；今日页与课表页共用 */
@Immutable
data class CourseTableData(
    val tableId: String,
    val config: CourseTableConfig?,
    val slots: List<TimeSlot>,
) {
    /** 节次时间段，下标即节次序号；导入的作息可能不是 8 节 */
    val sectionTimes: List<Pair<String, String>> =
        if (slots.isEmpty()) DEFAULT_SECTION_TIMES else slots.map { it.startTime to it.endTime }
}

/** 首启初始化并订阅课表配置与作息；初始化完成前返回 null */
@Composable
fun rememberCourseTableData(repository: ScheduleRepository): CourseTableData? {
    var tableId by remember(repository) { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) { tableId = repository.ensureInitialized() }
    val id = tableId ?: return null

    val configFlow = remember(id) { repository.configOf(id) }
    val config by configFlow.collectAsState(null)
    val slotsFlow = remember(id) { repository.timeSlotsOf(id) }
    val slots by slotsFlow.collectAsState(emptyList())

    return remember(id, config, slots) { CourseTableData(id, config, slots) }
}

/** 全部课程及其周次；在常驻作用域里订阅，进入详情页时数据已就绪 */
@Composable
fun rememberCoursesWithWeeks(repository: ScheduleRepository, tableId: String?): List<CourseWithWeeks> {
    val flow = remember(tableId) { tableId?.let { repository.coursesWithWeeksOfTable(it) } ?: flowOf(emptyList()) }
    return flow.collectAsState(emptyList()).value
}
