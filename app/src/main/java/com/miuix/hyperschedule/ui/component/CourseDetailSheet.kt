package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.schedule.cjkDigitGap
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.SheetBottomInset
import com.miuix.hyperschedule.theme.SheetMaxHeight
import com.miuix.hyperschedule.theme.sheetCardColor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 抽屉里的一门课：是否本周生效用于标题后缀 */
data class CourseDetailItem(
    val course: Course,
    val weeks: List<Int>,
    val activeInWeek: Boolean,
)

/**
 * 课程详情抽屉：列出被点格子里的所有课程，每张卡带一个「编辑」按钮。
 */
@Composable
fun CourseDetailSheet(
    items: List<CourseDetailItem>,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onEdit: (Course) -> Unit = {},
    onAdd: (day: Int, startSection: Int, endSection: Int) -> Unit = { _, _, _ -> },
) {
    SheetScaffold(
        title = "课程详情",
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        endAction = {
            IconButton(
                onClick = {
                    items.firstOrNull()?.let { item ->
                        val course = item.course
                        val start = course.startSection ?: 1
                        onDismiss()
                        onAdd(course.day, start, (course.endSection ?: start).coerceAtLeast(start))
                    }
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "添加课程",
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    ) {
        val listState = rememberLazyListState()
        val currentIndex = items.indexOfFirst { it.activeInWeek }
        // 打开时翻到本周那张卡；内容放得下时滚动会被钳住，等于不动
        LaunchedEffect(show) {
            if (show && currentIndex >= 0) listState.scrollToItem(currentIndex)
        }
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = SheetMaxHeight)
                    .overScrollVertical()
                    .scrollEndHaptic(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            items(items, key = { it.course.id }) { item ->
                CourseDetailCard(item, onEdit = { onEdit(item.course) })
            }
            // 卡片下方留白，最后一张也能完整滚出来
            item { Spacer(modifier = Modifier.height(SheetBottomInset)) }
        }
    }
}

@Composable
private fun CourseDetailCard(item: CourseDetailItem, onEdit: () -> Unit) {
    val course = item.course
    val cardColor = sheetCardColor()
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        insideMargin = PaddingValues(0.dp),
        pressFeedbackType = PressFeedbackType.None,
        colors =
            CardDefaults.defaultColors(
                color = cardColor,
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.activeInWeek) course.name else "${course.name}（非本周）",
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summaryText(course, item.weeks),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    inlineContent = courseInfoIcons(),
                )
                course.remark?.takeIf { it.isNotBlank() }?.let { remark ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text =
                            buildAnnotatedString {
                                infoIcon(InfoIconId.REMARK, "备注")
                                append(remark)
                            },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        inlineContent = courseInfoIcons(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            EditPill(onClick = onEdit)
        }
    }
}

/** 编辑入口 */
@Composable
private fun EditPill(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "编辑",
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

/** 第一行：周次 + 节次；第二行：地点 + 教师 + 学分（各自带图标，字段间用空格断行）；备注单独一行 */
private fun summaryText(course: Course, weeks: List<Int>): AnnotatedString =
    buildAnnotatedString {
        infoIcon(InfoIconId.WEEKS, "周次")
        fieldValue(formatWeeks(weeks))
        fieldGap()
        infoIcon(InfoIconId.SECTION, "节次")
        fieldValue(course.sectionLabel())
        if (course.position.isNotBlank() || course.teacher.isNotBlank()) {
            append('\n')
            if (course.position.isNotBlank()) {
                infoIcon(InfoIconId.POSITION, "地点")
                fieldValue(course.position)
            }
            if (course.teacher.isNotBlank()) {
                if (course.position.isNotBlank()) fieldGap()
                infoIcon(InfoIconId.TEACHER, "教师")
                fieldValue(course.teacher)
            }
        }
        course.credit?.let { credit ->
            if (course.position.isNotBlank() || course.teacher.isNotBlank()) {
                fieldGap()
            } else {
                append('\n')
            }
            infoIcon(InfoIconId.CREDIT, "学分")
            fieldValue("${formatCredit(credit)}学分".cjkDigitGap())
        }
    }
