package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.schedule.parseMinutes
import com.miuix.hyperschedule.schedule.weekDatesOf
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

// 网格几何
private val SectionHeight = 92.dp
private val SectionColumnWidth = 36.dp
private val WeekdayRowHeight = 40.dp

/** 星期行：年份 + 周一~周日 + 日期，今天蓝色加粗；未设置开学日期时只有星期名 */
@Composable
internal fun WeekdayRow(startDate: LocalDate?, week: Int, currentWeek: Int?, today: LocalDate) {
    val dates =
        remember(startDate, week) {
            if (startDate != null) weekDatesOf(startDate, week) else null
        }
    Row(modifier = Modifier.fillMaxWidth().height(WeekdayRowHeight).padding(end = 2.dp)) {
        Box(
            modifier = Modifier.width(SectionColumnWidth).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dates?.first()?.year?.toString().orEmpty(),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
        DAY_NAMES.forEachIndexed { index, dayName ->
            val date = dates?.getOrNull(index)
            val isToday = date != null && week == currentWeek && date == today
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = dayName,
                    style =
                        MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        ),
                    color = if (isToday) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
                if (date != null) {
                    Text(
                        text = "${date.monthValue}/${date.dayOfMonth}",
                        style = MiuixTheme.textStyles.footnote2,
                        color =
                            if (isToday) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantActions
                            },
                    )
                }
            }
        }
    }
}

/** 左侧节次列：序号 + 起止时间三行；当前节次整列跟随主题色 */
@Composable
internal fun SectionTimesColumn(currentSection: Int, sectionTimes: List<Pair<String, String>>) {
    Column(modifier = Modifier.width(SectionColumnWidth)) {
        sectionTimes.forEachIndexed { index, (startTime, endTime) ->
            val isCurrent = index + 1 == currentSection
            val accent = MiuixTheme.colorScheme.primary
            Box(
                modifier = Modifier.fillMaxWidth().height(SectionHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${index + 1}",
                        style =
                            MiuixTheme.textStyles.body2.copy(
                                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                            ),
                        color = if (isCurrent) accent else MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = startTime,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                        color =
                            if (isCurrent) accent else MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                    Text(
                        text = endTime,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                        color =
                            if (isCurrent) accent else MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
        }
    }
}

/** 单日列：标准课按节次并排，自定义时间课按时间轴插值定位；「+」占位仍保留 */
@Composable
internal fun DayGridColumn(
    courses: List<WeekCourse>,
    sectionCount: Int,
    sectionTimes: List<Pair<String, String>>,
    pendingSection: Int,
    onCellTap: ((Int) -> Unit)?,
    onCourseClick: (Course) -> Unit,
    onPendingTap: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val sectionPx = with(density) { SectionHeight.toPx() }
    val currentOnCellTap by rememberUpdatedState(onCellTap)
    val currentOnCourseClick by rememberUpdatedState(onCourseClick)
    val currentOnPendingTap by rememberUpdatedState(onPendingTap)
    val currentPendingSection by rememberUpdatedState(pendingSection)
    val currentCourses by rememberUpdatedState(courses)
    // 已被课程占用的节次不再响应「+」：标准课按节次范围，自定义时间课按与节次的重叠区间
    val occupiedSections =
        remember(courses, sectionTimes) {
            buildSet {
                courses.forEach { item ->
                    val course = item.course
                    if (course.isCustomTime) {
                        addAll(overlappedSections(course, sectionTimes))
                    } else {
                        val start = course.startSection ?: return@forEach
                        addAll(start..(course.endSection ?: start).coerceAtLeast(start))
                    }
                }
            }
        }
    // 「+」格配色：有动态取色时用主题色，否则中性灰
    val dynamicColor = MiuixTheme.isDynamicColor
    val pendingColor =
        (if (dynamicColor) MiuixTheme.colorScheme.primary else Color(0xFF9E9E9E))
            .copy(alpha = if (isDark) 0.13f else 0.15f)
    val pendingContent =
        if (dynamicColor) {
            MiuixTheme.colorScheme.onSurfaceVariantActions
        } else {
            Color(0xFF9E9E9E).copy(alpha = 0.5f)
        }
    Box(
        modifier =
            modifier
                .height(SectionHeight * sectionCount)
                // 整列一个点击层：落在课程上就开详情抽屉，落在空格上就给「+」。
                // 卡片自身的 clickable 会先消费掉事件，这里只兜底，两条路径只会走一条。
                .pointerInput(sectionTimes, occupiedSections) {
                    detectTapGestures { offset ->
                        val section = (offset.y / sectionPx).toInt() + 1
                        if (section !in 1..sectionCount) return@detectTapGestures
                        val hit =
                            currentCourses
                                .firstOrNull { it.active && it.course.covers(section, sectionTimes) }
                                ?: currentCourses.firstOrNull { it.course.covers(section, sectionTimes) }
                        if (hit != null) {
                            currentOnCourseClick(hit.course)
                        } else if (section !in occupiedSections) {
                            // 已经是「+」的格子再点一次就进新建课程页
                            if (section == currentPendingSection) {
                                currentOnPendingTap?.invoke(section)
                            } else {
                                currentOnCellTap?.invoke(section)
                            }
                        }
                    }
                },
    ) {
        courses
            .filter { !it.course.isCustomTime && it.course.startSection != null }
            .groupBy { it.course.startSection!! }
            .forEach { (start, items) ->
                // 一格里的条目超过一条就打点：包括同一门课的不同地点，也包括被折叠的非本周课
                val stacked = items.sumOf { 1 + it.hiddenCount } > 1
                Box(modifier = Modifier.offset(y = SectionHeight * (start - 1))) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        items.forEach { item ->
                            ScheduleCourseCard(
                                course = item.course,
                                sectionHeight = SectionHeight,
                                isCurrentWeek = item.active,
                                showBadge = stacked,
                                onClick = { onCourseClick(item.course) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

        // 自定义时间课：按课表作息把起止时间插值成纵向位置与高度
        courses.filter { it.course.isCustomTime }.forEach { item ->
            val layout = customTimeLayout(item.course, sectionTimes) ?: return@forEach
            ScheduleCourseCard(
                course = item.course,
                sectionHeight = SectionHeight,
                isCurrentWeek = item.active,
                showBadge = item.hiddenCount > 0,
                onClick = { onCourseClick(item.course) },
                heightOverride = layout.height,
                modifier = Modifier.offset(y = layout.top),
            )
        }

        if (pendingSection in 1..sectionCount && pendingSection !in occupiedSections) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(SectionHeight)
                        .offset(y = SectionHeight * (pendingSection - 1))
                        // 再点一次「+」直接进新建课程页；点击层放在卡片外层，避免和列手势抢事件
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onPendingTap?.invoke(pendingSection) },
                        ),
            ) {
                Card(
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    cornerRadius = 10.dp,
                    insideMargin = PaddingValues(0.dp),
                    colors =
                        CardDefaults.defaultColors(
                            color = pendingColor,
                            contentColor = pendingContent,
                        ),
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = "添加",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 同一格：同一天且起始节次相同（与网格分格方式一致）；自定义时间课比对起止时间 */
internal fun Course.isSameSlotAs(other: Course): Boolean {
    if (day != other.day) return false
    if (isCustomTime || other.isCustomTime) {
        return isCustomTime && other.isCustomTime &&
            customStartTime == other.customStartTime &&
            customEndTime == other.customEndTime
    }
    return startSection != null && startSection == other.startSection
}

/** 该课程是否落在第 [section] 节：标准课看节次范围，自定义时间课看时间重叠 */
private fun Course.covers(section: Int, sectionTimes: List<Pair<String, String>>): Boolean =
    if (isCustomTime) section in overlappedSections(this, sectionTimes) else {
        val start = startSection ?: return false
        val end = (endSection ?: start).coerceAtLeast(start)
        section in start..end
    }

/** 某页周次下的一门课：active = 本页周次生效；hiddenCount = 同格还有几门没画出来 */
internal data class WeekCourse(
    val course: Course,
    val active: Boolean,
    val hiddenCount: Int = 0,
)

/** 槽位必须带星期，否则不同天的同一节会被并成一组、互相吞掉 */
private fun courseSlotKey(course: Course): String =
    if (course.isCustomTime) {
        "custom|${course.day}|${course.customStartTime}|${course.customEndTime}"
    } else {
        "section|${course.day}|${course.startSection}"
    }

/** 本页周次生效的照常显示；某格全都不上时取最近的一门灰显（可在设置里关闭） */
internal fun resolveWeekCourses(
    all: List<CourseWithWeeks>,
    week: Int,
    showNonCurrent: Boolean,
): List<WeekCourse> =
    all.groupBy { courseSlotKey(it.course) }.flatMap { (_, items) ->
        val active = items.filter { item -> item.weeks.any { it.weekNumber == week } }
        when {
            active.isNotEmpty() ->
                active.map { WeekCourse(it.course, true, hiddenCount = items.size - active.size) }
            !showNonCurrent -> emptyList()
            else -> {
                val ended = items.filter { item -> (item.weeks.maxOfOrNull { it.weekNumber } ?: 0) < week }
                val upcoming =
                    items.filter { item ->
                        (item.weeks.minOfOrNull { it.weekNumber } ?: Int.MAX_VALUE) > week
                    }
                val picked =
                    ended.maxByOrNull { item -> item.weeks.maxOf { it.weekNumber } }
                        ?: upcoming.minByOrNull { item -> item.weeks.minOf { it.weekNumber } }
                        ?: items.first()
                listOf(WeekCourse(picked.course, false, hiddenCount = items.size - 1))
            }
        }
    }

/** 自定义时间课占用的节次：与节次时间段有交集即算占用 */
private fun overlappedSections(course: Course, sectionTimes: List<Pair<String, String>>): List<Int> {
    val startAt = parseMinutes(course.customStartTime) ?: return emptyList()
    val endAt = parseMinutes(course.customEndTime) ?: return emptyList()
    if (startAt == endAt) return emptyList()
    return sectionTimes.mapIndexedNotNull { index, (start, end) ->
        val sectionStart = parseMinutes(start) ?: return@mapIndexedNotNull null
        val sectionEnd = parseMinutes(end) ?: return@mapIndexedNotNull null
        if (startAt < sectionEnd && endAt > sectionStart) index + 1 else null
    }
}

/** 自定义时间课在列内的纵向位置与高度 */
private data class CustomTimeLayout(val top: Dp, val height: Dp)

/** 把起止时间按作息插值成纵向位置与高度；落在作息之外或算不出高度时返回 null */
private fun customTimeLayout(
    course: Course,
    sectionTimes: List<Pair<String, String>>,
): CustomTimeLayout? {
    val startMinute = parseMinutes(course.customStartTime) ?: return null
    val endMinute = parseMinutes(course.customEndTime) ?: return null
    if (startMinute == endMinute) return null

    val sections =
        sectionTimes.mapIndexedNotNull { index, (start, end) ->
            val startAt = parseMinutes(start) ?: return@mapIndexedNotNull null
            val endAt = parseMinutes(end) ?: return@mapIndexedNotNull null
            Triple(startAt, endAt, index)
        }
    if (sections.isEmpty()) return null

    val columnBottom = SectionHeight * sectionTimes.size
    fun timeToY(minute: Int): Float {
        for ((startAt, endAt, index) in sections) {
            if (minute <= endAt) {
                val top = SectionHeight.value * index
                val fraction =
                    if (endAt > startAt) {
                        ((minute - startAt).toFloat() / (endAt - startAt)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                return top + SectionHeight.value * fraction
            }
        }
        return columnBottom.value
    }

    val top =
        if (startMinute <= sections.first().first) {
            0f
        } else {
            timeToY(startMinute).coerceIn(0f, columnBottom.value)
        }
    val bottom = timeToY(endMinute).coerceIn(0f, columnBottom.value)
    // 作息之外或跨午夜算不出有效区间就不画
    val height = (bottom - top).coerceAtLeast(0f)
    if (height <= 0f) return null
    return CustomTimeLayout(top.dp, height.dp)
}
