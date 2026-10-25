package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.repository.TableSummary
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课表导出：选择课表 + 导出格式；导出功能待实现 */
@Composable
fun ScheduleExportPage(
    tables: List<TableSummary>,
    currentTableId: String?,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val ids = tables.map { it.table.id }
    var selectedId by remember { mutableStateOf(currentTableId) }
    val selectedIndex = ids.indexOf(selectedId).coerceAtLeast(0)

    PreferenceScreen(title = "课表导出", appBlur = appBlur, onBack = onBack) {
        PreferenceSection("导出课表") {
            if (tables.isEmpty()) {
                Text(
                    text = "暂无课表，请先创建或导入后再导出",
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                OverlayDropdownPreference(
                    title = "选择课表",
                    items = tables.map { it.table.name.ifBlank { "未命名" } },
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedId = ids[it] },
                )
            }
        }

        PreferenceSection("导出格式") {
            ArrowPreference(
                title = "JSON 文件导出",
                summary = "导出课表为 JSON 文件",
                onClick = {},
            )
            ArrowPreference(
                title = "ICS 文件导出",
                summary = "导出课表为 ICS 日历文件",
                onClick = {},
            )
        }
    }
}
