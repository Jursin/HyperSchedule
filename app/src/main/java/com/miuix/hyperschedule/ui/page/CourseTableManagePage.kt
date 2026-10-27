package com.miuix.hyperschedule.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.miuix.hyperschedule.data.repository.TableSummary
import com.miuix.hyperschedule.schedule.cjkDigitGap
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.PreferenceItemSection
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.SelectableEditRow
import com.miuix.hyperschedule.ui.component.TableNameDialog
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课表管理：课表平铺，单选切换使用，右侧编辑/删除；最后一张不可删 */
@Composable
fun CourseTableManagePage(
    tables: List<TableSummary>,
    currentTableId: String?,
    appBlur: Boolean = false,
    onSwitch: (String) -> Unit = {},
    onCreate: (String) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TableSummary?>(null) }
    var pendingDelete by remember { mutableStateOf<TableSummary?>(null) }

    PreferenceScreen(
        title = "课表管理",
        appBlur = appBlur,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "添加课表",
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        if (tables.isEmpty()) return@PreferenceScreen

        PreferenceItemSection {
            tables.forEach { summary ->
                val table = summary.table
                SelectableEditRow(
                    title = table.name.ifBlank { "未命名" },
                    summary = "共${summary.courseCount}门课程".cjkDigitGap(),
                    selected = table.id == currentTableId,
                    onSelect = { onSwitch(table.id) },
                    onEdit = { editing = summary },
                    onDelete = if (tables.size > 1) ({ pendingDelete = summary }) else null,
                )
            }
        }
    }

    if (showAddDialog) {
        TableNameDialog(
            title = "添加课表",
            appBlur = appBlur,
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                showAddDialog = false
                onCreate(name)
            },
        )
    }

    editing?.let { summary ->
        TableNameDialog(
            title = "编辑课表",
            initialName = summary.table.name,
            appBlur = appBlur,
            onDismiss = { editing = null },
            onConfirm = { name ->
                editing = null
                onRename(summary.table.id, name)
            },
        )
    }

    pendingDelete?.let { summary ->
        ConfirmDialog(
            title = "删除课表",
            message = "「${summary.table.name}」删除后无法恢复，确定删除？",
            appBlur = appBlur,
            destructive = true,
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                onDelete(summary.table.id)
            },
        )
    }
}
