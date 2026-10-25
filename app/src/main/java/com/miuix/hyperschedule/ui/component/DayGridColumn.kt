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

/** 单日列：标准课按节次并排，自定义时间课按时间轴定位；「+」占位仍保留 */
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
    // 放假当天：卡片一律灰显并标「假」
    isHoliday: Boolean = false,
    // 补课当天：这一列画的是被调那天的课，标「调」
    isWorkSwap: Boolean = false,
    // 补课列显示的课属于别的星期，拖动会算错列，直接禁掉
    dragEnabled: Boolean = true,
    // 正在拖的原卡片（按原位置比对），它按「本周不上」灰显
    draggingCourse: Course? = null,
    onDragStart: (Course, Offset) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val density = LocalDensity.current
    val currentOnCellTap by rememberUpdatedState(onCellTap)
    val currentOnCourseClick by rememberUpdatedState(onCourseClick)
    val currentOnPendingTap by rememberUpdatedState(onPendingTap)
    val currentPendingSection by rememberUpdatedState(pendingSection)
    val currentCourses by rememberUpdatedState(courses)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentDragEnabled by rememberUpdatedState(dragEnabled)
    // 拖动回调要把手指坐标换算成 root 坐标
    var columnTopLeft by remember { mutableStateOf(Offset.Zero) }
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
    // 落在课程上就开详情抽屉，落在空格上就给「+」
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
            // 已经是「+」的格子再点一次就进新建课程页
            if (section == currentPendingSection) {
                currentOnPendingTap?.invoke(section)
            } else {
                currentOnCellTap?.invoke(section)
            }
        }
    }
    // 「+」格配色：有动态取色时用主题色，否则跟随 surface 层级
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
                // 一层手势处理完：抬手是点击，长按后跟手拖动课程
                .pointerInput(sectionTimes, occupiedSections, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPosition = down.position
                        var upPosition: Offset? = null
                        var upConsumed = false
                        var movedAway = false
                        // 长按等待期：抬手走点击，移动超出阈值就交还给页面滚动
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                                if (!change.pressed) {
                                    upPosition = change.position
                                    // 子节点（如「+」格）已经处理过的点击不再重复
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
                        // 长按成立：只有本周生效的标准节次课可以拖（只挪这一周）
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
                        currentOnDragStart(hit.course, columnTopLeft + downPosition)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                currentOnDrag(columnTopLeft + change.position)
                            }
                        } finally {
                            // 手势被系统打断时也要收尾，不能让浮层卡住
                            currentOnDragEnd()
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
                Box(modifier = Modifier.offset(y = layout.topOf(start))) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        items.forEach { item ->
                            // 只灰显原来那一张：整行搬走后它已经换了位置，就别再灰了
                            val original = draggingCourse
                            val dragged =
                                original != null &&
                                    item.course.id == original.id &&
                                    item.course.day == original.day &&
                                    item.course.startSection == original.startSection
                            val span = (item.course.endSection ?: start).coerceAtLeast(start)
                            ScheduleCourseCard(
                                course = item.course,
                                style = style,
                                heightOverride = layout.heightOf(start, span),
                                // 松手落库后本周这张本来就不生效，先按灰卡显示，颜色不会跳
                                isCurrentWeek = item.active && !dragged,
                                isHoliday = isHoliday,
                                isWorkSwap = isWorkSwap,
                                showBadge = stacked,
                                // 点击统一由整列的手势处理，卡片自己不再抢手势，长按才能顺利进入拖动
                                onClick = null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

        // 自定义时间课：按同一套时间锚点插值定位，与节次一起被横带推开
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
                        // 再点一次「+」直接进新建课程页；点击层放在卡片外层，避免和列手势抢事件
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
