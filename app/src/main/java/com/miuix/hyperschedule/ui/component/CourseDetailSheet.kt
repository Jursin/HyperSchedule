package com.miuix.hyperschedule.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
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
 *
 * 不用系统 Dialog，直接叠在宿主页面上做位移 + 弹簧动画，
 * 避免每次打开都要创建窗口导致首帧掉帧。
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
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val progress = remember { Animatable(0f) }
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    LaunchedEffect(show) {
        if (show) {
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 520f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 620f))
            currentOnDismissFinished()
        }
    }
    // 完全收起且不再展示时整棵子树退出，避免无谓的合成层
    if (progress.value <= 0f && !show) return

    BackHandler(enabled = show) { onDismiss() }

    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    // 抽屉本体用实色而不是玻璃层：深色 #1E1E1E 比页面底色亮一档，浅色 #F2F2F2 略深于页面，两种主题都能和背景分开
    val panelColor =
        when {
            MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.surfaceContainer
            isDark -> Color(0xFF1E1E1E)
            else -> Color(0xFFF2F2F2)
        }
    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { translationY = (1f - progress.value) * size.height }
                    .clip(shape)
                    .background(panelColor)
                    // 顶边一道极淡的高光，避免深色下和背景黏在一起
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = shape,
                    ),
        ) {
            // 拖动条
            Box(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.3f))
                        .align(Alignment.CenterHorizontally),
            )
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "课程详情",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = {
                        items.firstOrNull()?.let { item ->
                            val course = item.course
                            val start = course.startSection ?: 1
                            onDismiss()
                            onAdd(course.day, start, (course.endSection ?: start).coerceAtLeast(start))
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    minWidth = 40.dp,
                    minHeight = 40.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = "添加课程",
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
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
                        .heightIn(max = 560.dp)
                        .overScrollVertical()
                        .scrollEndHaptic(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.course.id }) { item ->
                    CourseDetailCard(item, onEdit = { onEdit(item.course) })
                }
                // 卡片下方留白
                item { Spacer(modifier = Modifier.height(70.dp)) }
            }
        }
    }
}

@Composable
private fun CourseDetailCard(item: CourseDetailItem, onEdit: () -> Unit) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val course = item.course
    // 动态取色用 surfaceContainerHigh，否则深色 #303030、浅色纯白
    val cardColor =
        when {
            MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.surfaceContainerHigh
            isDark -> Color(0xFF303030)
            else -> Color.White
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summaryText(course, item.weeks),
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
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
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
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
            style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
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
            fieldValue("${formatCredit(credit)}学分")
        }
    }
