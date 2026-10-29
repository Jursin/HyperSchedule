package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.repository.TableSummary
import com.miuix.hyperschedule.schedule.cjkDigitGap
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

/** 导入到课表，文件 / 分享码 / 教务脚本共用，[onAddTable] 为空时不显示新建入口 */
@Composable
fun ImportTablePickerDialog(
    tables: List<TableSummary>,
    selectedTableId: String?,
    appBlur: Boolean,
    onSelect: (tableId: String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    busy: Boolean = false,
    onAddTable: (() -> Unit)? = null,
) {
    ConfirmDialog(
        title = "导入到课表",
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmEnabled = selectedTableId != null && !busy,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tables.forEach { summary ->
                SelectableEditRow(
                    title = summary.table.name.ifBlank { "未命名" },
                    summary =
                        if (summary.courseCount > 0) {
                            "共${summary.courseCount}门课程，导入后将被覆盖".cjkDigitGap()
                        } else {
                            "暂无课程"
                        },
                    selected = summary.table.id == selectedTableId,
                    onSelect = { onSelect(summary.table.id) },
                )
            }
        }
        if (onAddTable != null) {
            WideActionButton(
                text = "添加课表",
                icon = MiuixIcons.Add,
                onClick = onAddTable,
            )
        }
    }
}
