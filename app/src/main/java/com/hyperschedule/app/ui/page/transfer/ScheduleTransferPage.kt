package com.hyperschedule.app.ui.page.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hyperschedule.app.data.local.ReminderSettings
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.data.repository.ScheduleRepository
import com.hyperschedule.app.data.repository.TableSummary
import com.hyperschedule.app.ui.component.PreferenceScreen
import com.hyperschedule.app.ui.state.CourseTableData
import top.yukonga.miuix.kmp.basic.TabRowWithContour

private const val TAB_IMPORT = 0

/** 导入与导出页 */
@Composable
fun ScheduleTransferPage(
    tables: List<TableSummary>,
    table: CourseTableData?,
    repository: ScheduleRepository,
    holidayRepository: HolidayRepository,
    reminder: ReminderSettings = ReminderSettings.Default,
    currentTableId: String? = null,
    appBlur: Boolean = false,
    onSwitchTable: (String) -> Unit = {},
    onOpenEduImport: () -> Unit = {},
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(TAB_IMPORT) }

    PreferenceScreen(title = "导入与导出", appBlur = appBlur, onBack = onBack) {
        TabRowWithContour(
            tabs = listOf("导入", "导出"),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        when (selectedTab) {
            TAB_IMPORT ->
                ImportTab(
                    tables = tables,
                    table = table,
                    repository = repository,
                    appBlur = appBlur,
                    onSwitchTable = onSwitchTable,
                    onOpenEduImport = onOpenEduImport,
                )
            else ->
                ExportTab(
                    tables = tables,
                    repository = repository,
                    holidayRepository = holidayRepository,
                    currentTableId = currentTableId,
                    reminder = reminder,
                )
        }
    }
}
