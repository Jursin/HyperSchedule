package com.miuix.hyperschedule.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.schedule.cjkDigitGap
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.CourseTintAlpha
import com.miuix.hyperschedule.theme.PageBottomInset
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.ui.component.AwardStar
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.Person
import com.miuix.hyperschedule.ui.component.formatCredit
import com.miuix.hyperschedule.ui.component.pageContentPadding
import com.miuix.hyperschedule.ui.component.pageScroll
import com.miuix.hyperschedule.ui.component.rememberCourseColor
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import java.text.Collator
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课程管理：同名课程聚合成一张卡，点击进入编辑；长按进入多选，可批量删除 */
@Composable
fun CourseManagePage(
    courses: List<CourseWithWeeks>,
    appBlur: Boolean = false,
    onEditCourse: (String) -> Unit = {},
    onAddCourse: () -> Unit = {},
    onDeleteCourses: (List<String>) -> Unit = {},
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val haptic = LocalHapticFeedback.current

    // 同名课程的所有时段归一组，组间按中文拼音排
    val groups =
        remember(courses) {
            val collator = Collator.getInstance(Locale.CHINA)
            courses
                .groupBy { it.course.name }
                .toList()
                .sortedWith { a, b -> collator.compare(a.first, b.first) }
        }

    // 选中的课程名；非空即处于多选状态
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    fun toggle(name: String) {
        selected = if (name in selected) selected - name else selected + name
    }

    // 多选时返回键先退出多选
    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(
        topBar = { BlurredTopAppBar("课程管理", blurBackdrop, scrollBehavior, onBack = onBack) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
                if (selecting) {
                    FloatingActionButton(
                        onClick = { deleteConfirm = true },
                        containerColor = MiuixTheme.colorScheme.error,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "删除",
                            tint = MiuixTheme.colorScheme.onError,
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { if (selecting) selected = emptySet() else onAddCourse() },
                    containerColor =
                        if (selecting) {
                            MiuixTheme.colorScheme.secondaryVariant
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                ) {
                    Icon(
                        imageVector = if (selecting) MiuixIcons.Basic.Close else MiuixIcons.Add,
                        contentDescription = if (selecting) "取消" else "添加课程",
                        tint =
                            if (selecting) {
                                MiuixTheme.colorScheme.onSecondaryVariant
                            } else {
                                MiuixTheme.colorScheme.onPrimary
                            },
                    )
                }
            }
        },
    ) { paddingValues ->
        if (groups.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "暂无课程",
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点右下角添加课程",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().pageScroll(blurBackdrop, scrollBehavior),
                contentPadding = pageContentPadding(paddingValues, bottom = PageBottomInset),
                horizontalArrangement = Arrangement.spacedBy(SectionSpacing),
                verticalItemSpacing = SectionSpacing,
                overscrollEffect = null,
            ) {
                items(groups, key = { it.first }) { (name, list) ->
                    val representative = list.first().course
                    CourseManageCard(
                        name = name,
                        color = rememberCourseColor(representative),
                        teacher = list.map { it.course.teacher }.filter { it.isNotBlank() }.distinct().joinToString("/"),
                        credit = list.firstNotNullOfOrNull { it.course.credit },
                        slotCount = list.size,
                        selected = name in selected,
                        onClick = { if (selecting) toggle(name) else onEditCourse(name) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggle(name)
                        },
                    )
                }
            }
        }
    }

    if (deleteConfirm) {
        ConfirmDialog(
            title = "删除课程",
            appBlur = appBlur,
            message = "确定要删除选中的${selected.size}门课程吗？".cjkDigitGap(),
            confirmText = "删除",
            destructive = true,
            onDismiss = { deleteConfirm = false },
            onConfirm = {
                deleteConfirm = false
                onDeleteCourses(courses.filter { it.course.name in selected }.map { it.course.id })
                selected = emptySet()
            },
        )
    }
}

@Composable
private fun CourseManageCard(
    name: String,
    color: Color,
    teacher: String,
    credit: Float?,
    slotCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardCornerRadius,
            insideMargin = PaddingValues(14.dp),
            colors = CardDefaults.defaultColors(color = color.copy(alpha = CourseTintAlpha)),
            onClick = onClick,
            onLongPress = onLongClick,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 3.dp, height = 16.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(color),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (slotCount > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(color.copy(alpha = CourseTintAlpha))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${slotCount}个时段".cjkDigitGap(),
                                style = MiuixTheme.textStyles.footnote2,
                                maxLines = 1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                if (teacher.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    MetaRow(icon = Person, text = teacher)
                }
                if (credit != null) {
                    Spacer(modifier = Modifier.height(if (teacher.isNotBlank()) 4.dp else 10.dp))
                    MetaRow(icon = AwardStar, text = "${formatCredit(credit)}学分".cjkDigitGap())
                }
            }
        }
        // 选中描边用覆盖层，不参与测量，卡片尺寸不跟着变
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .squircleBorder(width = 2.dp, color = color, cornerRadius = CardDefaults.CornerRadius),
            )
        }
    }
}

/** 教师 / 学分这类次要信息：小图标给出语义锚点 */
@Composable
private fun MetaRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
