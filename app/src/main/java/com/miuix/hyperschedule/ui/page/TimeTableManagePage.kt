package com.miuix.hyperschedule.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import com.miuix.hyperschedule.data.repository.ScheduleOverview
import com.miuix.hyperschedule.ui.component.ChoiceDialog
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.DialogChoice
import com.miuix.hyperschedule.ui.component.PreferenceItemSection
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.SelectableEditRow
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 待删除的作息：公共作息或组合作息 */
private data class DeleteTarget(
    val id: String,
    val name: String,
    val isCombo: Boolean,
)

/** 作息管理：专属、公共、组合作息平铺，单选切换使用，右侧编辑/删除 */
@Composable
fun TimeTableManagePage(
    overview: ScheduleOverview?,
    appBlur: Boolean = false,
    onBind: (targetType: String, targetId: String) -> Unit = { _, _ -> },
    onOpenPlan: (String) -> Unit = {},
    onOpenCombo: (String) -> Unit = {},
    onCreatePublic: () -> Unit = {},
    onCreateCombo: () -> Unit = {},
    onDeletePlan: (String) -> Unit = {},
    onDeleteCombo: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    PreferenceScreen(
        title = "作息管理",
        appBlur = appBlur,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "添加作息",
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        if (overview == null) return@PreferenceScreen

        val courseTableId = overview.exclusive?.id.orEmpty()

        overview.exclusive?.let { exclusive ->
            PreferenceItemSection("专属作息") {
                SelectableEditRow(
                    title = "专属作息",
                    summary = "共 ${overview.slotCounts[exclusive.id] ?: 0} 节",
                    selected = overview.isActive(CourseTimeBinding.TARGET_SINGLE, exclusive.id, courseTableId),
                    onSelect = { onBind(CourseTimeBinding.TARGET_SINGLE, exclusive.id) },
                    onEdit = { onOpenPlan(exclusive.id) },
                    onDelete = null,
                )
            }
        }

        if (overview.publics.isNotEmpty()) {
            PreferenceItemSection("公共作息") {
                overview.publics.forEach { plan ->
                    val title = plan.name.orEmpty().ifBlank { "未命名" }
                    SelectableEditRow(
                        title = title,
                        summary = "共 ${overview.slotCounts[plan.id] ?: 0} 节",
                        selected = overview.isActive(CourseTimeBinding.TARGET_SINGLE, plan.id, courseTableId),
                        onSelect = { onBind(CourseTimeBinding.TARGET_SINGLE, plan.id) },
                        onEdit = { onOpenPlan(plan.id) },
                        onDelete = { pendingDelete = DeleteTarget(plan.id, title, isCombo = false) },
                    )
                }
            }
        }

        if (overview.combos.isNotEmpty()) {
            PreferenceItemSection("组合作息") {
                overview.combos.forEach { combo ->
                    val title = combo.name.ifBlank { "未命名" }
                    SelectableEditRow(
                        title = title,
                        summary = "${overview.ruleCounts[combo.id] ?: 0} 条切换规则",
                        selected = overview.isActive(CourseTimeBinding.TARGET_COMBO, combo.id, courseTableId),
                        onSelect = { onBind(CourseTimeBinding.TARGET_COMBO, combo.id) },
                        onEdit = { onOpenCombo(combo.id) },
                        onDelete = { pendingDelete = DeleteTarget(combo.id, title, isCombo = true) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ChoiceDialog(
            title = "新增作息",
            appBlur = appBlur,
            onDismiss = { showAddDialog = false },
            choices =
                listOf(
                    DialogChoice(text = "公共作息", onClick = onCreatePublic),
                    DialogChoice(text = "组合作息", onClick = onCreateCombo),
                ),
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = if (target.isCombo) "删除组合作息" else "删除作息",
            message = "「${target.name}」删除后无法恢复，确定删除？",
            appBlur = appBlur,
            destructive = true,
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                if (target.isCombo) onDeleteCombo(target.id) else onDeletePlan(target.id)
            },
        )
    }
}
