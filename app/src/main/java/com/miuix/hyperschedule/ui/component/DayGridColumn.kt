package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.schedule.parseMinutes
import com.miuix.hyperschedule.theme.CourseTintAlpha
import com.miuix.hyperschedule.theme.ScheduleStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 单日列 */
@Composable
internal fun DayGridColumn(
    layout: ScheduleLayout,
    courses: List<WeekCourse>,
    sectionTimes: List<Pair<String, String>>,
    style: ScheduleStyle,
    pendingSection: Int,
    onCellTap: ((Int) -> Unit)?,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
    onPendingTap: ((Int) -> Unit)? = null,
    // 放假当天
    isHoliday: Boolean = false,
    // 补课当天
    isWorkSwap: Boolean = false,
    // 补课列显示的课属于别的星期，禁用拖动
    dragEnabled: Boolean = true,
    // 被浮层顶替的原卡 id
    draggingId: String? = null,
    // 长按拿起只上报，跟手交给页面级 tracker
    onDragStart: (Course, Offset, PointerId) -> Unit = { _, _, _ -> },
) {
    val density = LocalDensity.current
    val currentOnCellTap by rememberUpdatedState(onCellTap)
    val currentOnCourseClick by rememberUpdatedState(onCourseClick)
    val currentOnPendingTap by rememberUpdatedState(onPendingTap)
    val currentPendingSection by rememberUpdatedState(pendingSection)
    val currentCourses by rememberUpdatedState(courses)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentDragEnabled by rememberUpdatedState(dragEnabled)
    // 拖动回调要把手指坐标换算成 root 坐标
    var columnTopLeft by remember { mutableStateOf(Offset.Zero) }
    // 已被课程占用的节次不再响应「+」
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
    // 落在课上开抽屉，空格给「+」
    fun tapAt(position: Offset) {
        val section = with(density) { layout.sectionAt(position.y.toDp()) }
        if (section !in 1..layout.sectionCount) return
        val hit =
            currentCourses
                .firstOrNull { it.active && it.course.covers(section, sectionTimes) }
                ?: currentCourses.firstOrNull { it.course.covers(section, sectionTimes) }
        if (hit != null) {
            currentOnCourseClick(hit.course)
        } else if (section !in occupiedSections) {
            // 再点一次进新建课程页
            if (section == currentPendingSection) {
                currentOnPendingTap?.invoke(section)
            } else {
                currentOnCellTap?.invoke(section)
            }
        }
    }
    // 「+」格配色
    val dynamicColor = MiuixTheme.isDynamicColor
    val pendingColor =
        (if (dynamicColor) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
            .copy(alpha = CourseTintAlpha)
    val pendingContent =
        if (dynamicColor) {
            MiuixTheme.colorScheme.onSurfaceVariantActions
        } else {
            MiuixTheme.colorScheme.onSurfaceContainerVariant
        }
    Box(
        modifier =
            modifier
                .height(layout.height)
                .onGloballyPositioned { columnTopLeft = it.boundsInRoot().topLeft }
                // 整列一个手势
                .pointerInput(sectionTimes, occupiedSections, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPosition = down.position
                        var upPosition: Offset? = null
                        var upConsumed = false
                        var movedAway = false
                        // 长按等待期
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                                if (!change.pressed) {
                                    upPosition = change.position
                                    // 子节点处理过的点击不重复
                                    upConsumed = change.isConsumed
                                    return@withTimeoutOrNull
                                }
                                if ((change.position - downPosition).getDistance() > viewConfiguration.touchSlop) {
                                    movedAway = true
                                    return@withTimeoutOrNull
                                }
                            }
                        }
                        if (movedAway) return@awaitEachGesture
                        upPosition?.let { position ->
                            if (!upConsumed) tapAt(position)
                            return@awaitEachGesture
                        }
                        // 长按成立
                        val section = with(density) { layout.sectionAt(downPosition.y.toDp()) }
                        val hit =
                            if (section in 1..layout.sectionCount) {
                                currentCourses.firstOrNull {
                                    it.active && !it.course.isCustomTime && it.course.covers(section, sectionTimes)
                                }
                            } else {
                                null
                            }
                        if (hit == null || !currentDragEnabled) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        // 拿起后交棒
                        currentOnDragStart(hit.course, columnTopLeft + downPosition, down.id)
                        return@awaitEachGesture
                    }
                },
    ) {
        courses
            .filter { !it.course.isCustomTime && it.course.startSection != null }
            .groupBy { it.course.startSection!! }
            .forEach { (start, items) ->
                // 一格里的条目超过一条就打点
                val stacked = items.sumOf { 1 + it.hiddenCount } > 1
                Box(modifier = Modifier.offset(y = layout.topOf(start))) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        items.forEach { item ->
                            // 只灰显源周那一张
                            val dragged = item.course.id == draggingId
                            val span = (item.course.endSection ?: start).coerceAtLeast(start)
                            ScheduleCourseCard(
                                course = item.course,
                                style = style,
                                heightOverride = layout.heightOf(start, span),
                                // 落库后先按灰卡显示，颜色不跳
                                isCurrentWeek = item.active && !dragged,
                                isHoliday = isHoliday,
                                isWorkSwap = isWorkSwap,
                                showBadge = stacked,
                                // 点击交给整列处理，卡片不抢手势
                                onClick = null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

        // 自定义时间课
        courses.filter { it.course.isCustomTime }.forEach { item ->
            val top = parseMinutes(item.course.customStartTime)?.let(layout::minuteToY) ?: return@forEach
            val bottom = parseMinutes(item.course.customEndTime)?.let(layout::minuteToY) ?: return@forEach
            val courseHeight = (bottom - top).coerceAtLeast(20.dp)
            ScheduleCourseCard(
                course = item.course,
                style = style,
                heightOverride = courseHeight,
                isCurrentWeek = item.active,
                isHoliday = isHoliday,
                isWorkSwap = isWorkSwap,
                showBadge = item.hiddenCount > 0,
                onClick = null,
                modifier = Modifier.offset(y = top),
            )
        }

        if (pendingSection in 1..layout.sectionCount && pendingSection !in occupiedSections) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(layout.sectionHeight)
                        .offset(y = layout.topOf(pendingSection))
                        // 点击层放卡片外层，避免和列手势抢事件
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onPendingTap?.invoke(pendingSection) },
                        ),
            ) {
                Card(
                    modifier = Modifier.fillMaxSize().padding(CardInset),
                    cornerRadius = style.cardCorner.dp,
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
