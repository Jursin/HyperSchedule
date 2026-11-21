package top.jursin.hyperschedule.ui.page.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.jursin.hyperschedule.data.local.ReminderSettings
import top.jursin.hyperschedule.data.repository.HolidayRepository
import top.jursin.hyperschedule.data.repository.ScheduleRepository
import top.jursin.hyperschedule.data.repository.TableSummary
import top.jursin.hyperschedule.ui.component.JoinedGlassTabs
import top.jursin.hyperschedule.ui.component.PreferenceScreen
import top.jursin.hyperschedule.ui.state.CourseTableData

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
        JoinedGlassTabs(
            tabs = listOf("导入", "导出"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        if (selectedTab == TAB_IMPORT) {
            ImportTab(
                tables = tables,
                table = table,
                repository = repository,
                appBlur = appBlur,
                onSwitchTable = onSwitchTable,
                onOpenEduImport = onOpenEduImport,
            )
        } else {
            ExportTab(
                tables = tables,
                repository = repository,
                holidayRepository = holidayRepository,
                currentTableId = currentTableId,
                reminder = reminder,
                appBlur = appBlur,
            )
        }
    }
}
