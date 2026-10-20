package com.miuix.hyperschedule.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.currentWeekOf
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.semesterStartMonday
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.DatePickerDialog
import com.miuix.hyperschedule.ui.component.NumberPickerDialog
import com.miuix.hyperschedule.ui.component.PageScrollColumn
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsPage(
    store: SettingsStore,
    repository: ScheduleRepository,
    table: CourseTableData?,
    onAppearanceClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onAboutClick: () -> Unit,
    bottomPadding: Dp = 80.dp,
) {
    val state = store.state
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(state.blur)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 课表导入：选 JSON 文件后整表替换
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val result =
                    runCatching {
                        val text =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                    ?: error("无法读取文件")
                            }
                        repository.importCourseTableJson(text)
                    }
                Toast.makeText(
                    context,
                    result.fold({ "已导入 $it 门课程" }, { "导入失败：${it.message ?: "文件格式不正确"}" }),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

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
    val currentWeek = remember(startDate) { startDate?.let { currentWeekOf(it, LocalDate.now()) } }
    val currentWeekLabel =
        when {
            currentWeek == null -> "未设置"
            currentWeek < 1 -> "未开学"
            currentWeek > totalWeeks -> "学期已结束"
            else -> "第${currentWeek}周"
        }

    Scaffold(topBar = { BlurredTopAppBar("设置", blurBackdrop, scrollBehavior) }) { paddingValues ->
        PageScrollColumn(blurBackdrop, scrollBehavior, paddingValues, bottomPadding = bottomPadding, topPadding = 4.dp) {
            SettingsSection("基本设置") {
                ArrowPreference(
                    "开始上课日期",
                    endActions = {
                        startDate?.let {
                            SettingValueText("%04d/%02d/%02d".format(it.year, it.monthValue, it.dayOfMonth))
                        } ?: SettingValueText("未设置")
                    },
                    onClick = {
                        dateDraft = startDate ?: LocalDate.now()
                        showStartDateDialog = true
                    },
                )
                ArrowPreference(
                    "当前周数",
                    endActions = { SettingValueText(currentWeekLabel) },
                    onClick = {
                        weekDraft = currentWeek?.coerceIn(1, totalWeeks) ?: 1
                        showCurrentWeekDialog = true
                    },
                )
                ArrowPreference(
                    "本学期总周数",
                    endActions = { SettingValueText("共${totalWeeks}周") },
                    onClick = {
                        totalWeeksDraft = totalWeeks
                        showTotalWeeksDialog = true
                    },
                )
                SwitchPreference("显示非本周课程", checked = state.showNonCurrentWeek) { checked ->
                    store.update { it.copy(showNonCurrentWeek = checked) }
                }
                OverlayDropdownPreference(
                    title = "启动页面",
                    items = listOf("今日课表", "课程表"),
                    selectedIndex = state.launchPage,
                    onSelectedIndexChange = { index ->
                        store.update { settings -> settings.copy(launchPage = index) }
                    },
                )
            }

            SettingsSection("数据管理") {
                ArrowPreference("作息管理", onClick = {})
                ArrowPreference("课表管理", onClick = {})
                ArrowPreference("课程管理", onClick = {})
                ArrowPreference("课表导入", onClick = { importLauncher.launch(arrayOf("*/*")) })
                ArrowPreference("课表导出", onClick = {})
            }

            SettingsSection("高级功能") {
                ArrowPreference("课程提醒", onClick = {})
                ArrowPreference("节假日与调休", onClick = {})
                ArrowPreference("开启新学期", onClick = {})
                ArrowPreference("备份与恢复", onClick = {})
            }

            SettingsSection("更多") {
                ArrowPreference("外观与主题", onClick = onAppearanceClick)
                ArrowPreference("更新", onClick = onUpdateClick)
                ArrowPreference("关于", onClick = onAboutClick)
            }
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
            range = 1..60,
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

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    SmallTitle(text = title, insideMargin = PaddingValues(16.dp, 8.dp, 16.dp, 0.dp))
    Card(modifier = Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun SettingValueText(value: String) {
    Text(
        text = value,
        fontSize = 14.5.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}
