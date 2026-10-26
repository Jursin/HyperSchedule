package com.miuix.hyperschedule.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.MAX_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.currentWeekOf
import com.miuix.hyperschedule.schedule.formatDate
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.semesterStartMonday
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.DatePickerDialog
import com.miuix.hyperschedule.ui.component.NumberPickerDialog
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.ValueLabel
import com.miuix.hyperschedule.ui.rememberToday
import java.time.LocalDate
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsPage(
    store: SettingsStore,
    repository: ScheduleRepository,
    table: CourseTableData?,
    onAppearanceClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onAboutClick: () -> Unit,
    onManageCourses: () -> Unit = {},
    onManageTables: () -> Unit = {},
    onManageTimeTables: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenHoliday: () -> Unit = {},
    onOpenReminder: () -> Unit = {},
) {
    val state = store.state
    val scope = rememberCoroutineScope()

    var showStartDateDialog by remember { mutableStateOf(false) }
    var showCurrentWeekDialog by remember { mutableStateOf(false) }
    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var dateDraft by remember { mutableStateOf(LocalDate.now()) }
    var weekDraft by remember { mutableIntStateOf(1) }
    val config = table?.config
    val totalWeeks = (config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS).coerceAtLeast(1)
    var totalWeeksDraft by remember { mutableIntStateOf(totalWeeks) }

    // 学期信息只有一份：写课表配置，课表页与今日页都读它
    fun applySemester(startDate: String?, weeks: Int) {
        val tableId = table?.tableId ?: return
        scope.launch { repository.updateSemesterConfig(tableId, startDate, weeks) }
    }

    // 空串 = 未设置
    val startDate = remember(config?.semesterStartDate) { parseDateOrNull(config?.semesterStartDate.orEmpty()) }
    // 当前周数由开学日期推算，与开始上课日期互推：改一边会同步另一边
    val today = rememberToday()
    val currentWeek = remember(startDate, today) { startDate?.let { currentWeekOf(it, today) } }
    val currentWeekLabel =
        when {
            currentWeek == null -> "未设置"
            currentWeek < 1 -> "未开学"
            currentWeek > totalWeeks -> "学期已结束"
            else -> "第${currentWeek}周"
        }

    PreferenceScreen(
        title = "设置",
        appBlur = state.blur,
    ) {
        PreferenceSection("基本设置") {
            ArrowPreference(
                "开始上课日期",
                endActions = {
                    startDate?.let {
                        ValueLabel(formatDate(it))
                    } ?: ValueLabel("未设置")
                },
                onClick = {
                    dateDraft = startDate ?: LocalDate.now()
                    showStartDateDialog = true
                },
            )
            ArrowPreference(
                "当前周数",
                endActions = { ValueLabel(currentWeekLabel) },
                onClick = {
                    weekDraft = currentWeek?.coerceIn(1, totalWeeks) ?: 1
                    showCurrentWeekDialog = true
                },
            )
            ArrowPreference(
                "本学期总周数",
                endActions = { ValueLabel("共${totalWeeks}周") },
                onClick = {
                    totalWeeksDraft = totalWeeks
                    showTotalWeeksDialog = true
                },
            )
            SwitchPreference(
                checked = state.showNonCurrentWeek,
                onCheckedChange = { checked -> store.update { it.copy(showNonCurrentWeek = checked) } },
                title = "显示非本周课程",
            )
            OverlayDropdownPreference(
                title = "启动页面",
                items = listOf("今日课表", "课程表"),
                selectedIndex = state.launchPage,
                onSelectedIndexChange = { index ->
                    store.update { settings -> settings.copy(launchPage = index) }
                },
            )
        }

        PreferenceSection("数据管理") {
            ArrowPreference("作息管理", onClick = onManageTimeTables)
            ArrowPreference("课表管理", onClick = onManageTables)
            ArrowPreference("课程管理", onClick = onManageCourses)
            ArrowPreference("课表导入", onClick = onOpenImport)
            ArrowPreference("课表导出", onClick = onOpenExport)
        }

        PreferenceSection("高级功能") {
            ArrowPreference("课程提醒", onClick = onOpenReminder)
            ArrowPreference("节假日与调休", onClick = onOpenHoliday)
            ArrowPreference("备份与恢复", onClick = onOpenBackup)
        }

        PreferenceSection("更多") {
            ArrowPreference("外观与主题", onClick = onAppearanceClick)
            ArrowPreference("更新", onClick = onUpdateClick)
            ArrowPreference("关于", onClick = onAboutClick)
        }
    }

    if (showStartDateDialog) {
        DatePickerDialog(
            title = "开始上课日期",
            date = dateDraft,
            onDateChange = { dateDraft = it },
            appBlur = state.blur,
            onDismiss = { showStartDateDialog = false },
            onConfirm = {
                applySemester(dateDraft.toString(), totalWeeks)
                showStartDateDialog = false
            },
        )
    }
    if (showCurrentWeekDialog) {
        NumberPickerDialog(
            title = "当前周数",
            value = weekDraft,
            onValueChange = { weekDraft = it },
            range = 1..totalWeeks,
            label = { "第${it}周" },
            appBlur = state.blur,
            onDismiss = { showCurrentWeekDialog = false },
            onConfirm = {
                // 反推第一周的周一：让「今天」正好落在选定周，且开学日期本身就是周一
                val firstMonday = semesterStartMonday(LocalDate.now()).minusWeeks((weekDraft - 1).toLong())
                applySemester(firstMonday.toString(), totalWeeks)
                showCurrentWeekDialog = false
            },
        )
    }
    if (showTotalWeeksDialog) {
        NumberPickerDialog(
            title = "本学期总周数",
            value = totalWeeksDraft,
            onValueChange = { totalWeeksDraft = it },
            range = 1..MAX_TOTAL_WEEKS,
            label = { "共${it}周" },
            appBlur = state.blur,
            onDismiss = { showTotalWeeksDialog = false },
            onConfirm = {
                applySemester(config?.semesterStartDate, totalWeeksDraft)
                showTotalWeeksDialog = false
            },
        )
    }
}
