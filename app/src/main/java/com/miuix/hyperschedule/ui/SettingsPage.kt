package com.miuix.hyperschedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onAppearanceClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onAboutClick: () -> Unit,
    bottomPadding: Dp = 80.dp,
) {
    val state = store.state
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(state.blur)
    var showNonCurrentWeek by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var showCurrentWeekDialog by remember { mutableStateOf(false) }
    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var dateDraft by remember { mutableStateOf(LocalDate.now()) }
    var weekDraft by remember { mutableIntStateOf(1) }
    var totalWeeksDraft by remember { mutableIntStateOf(state.totalWeeks) }

    // 空串 = 未设置
    val startDate = remember(state.startDate) { parseDateOrNull(state.startDate) }
    // 当前周数由开学日期推算，与开始上课日期互推：改一边会同步另一边
    val currentWeek = remember(startDate) { startDate?.let { currentWeekOf(it, LocalDate.now()) } }
    val currentWeekLabel =
        when {
            currentWeek == null -> "未设置"
            currentWeek < 1 -> "未开学"
            currentWeek > state.totalWeeks -> "学期已结束"
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
                        weekDraft = currentWeek?.coerceIn(1, state.totalWeeks) ?: 1
                        showCurrentWeekDialog = true
                    },
                )
                ArrowPreference(
                    "本学期总周数",
                    endActions = { SettingValueText("共${state.totalWeeks}周") },
                    onClick = {
                        totalWeeksDraft = state.totalWeeks
                        showTotalWeeksDialog = true
                    },
                )
                SwitchPreference("显示非本周课程", checked = showNonCurrentWeek) { showNonCurrentWeek = it }
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
                ArrowPreference("课表导入", onClick = {})
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
                store.update { it.copy(startDate = dateDraft.toString()) }
                showStartDateDialog = false
            },
        )
    }
    if (showCurrentWeekDialog) {
        NumberPickerDialog(
            title = "当前周数",
            value = weekDraft,
            onValueChange = { weekDraft = it },
            range = 1..state.totalWeeks,
            label = { "第${it}周" },
            appBlur = state.blur,
            onDismiss = { showCurrentWeekDialog = false },
            onConfirm = {
                // 反推第一周的周一：让「今天」正好落在选定周，且开学日期本身就是周一
                val firstMonday = semesterStartMonday(LocalDate.now()).minusWeeks((weekDraft - 1).toLong())
                store.update { it.copy(startDate = firstMonday.toString()) }
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
                store.update { it.copy(totalWeeks = totalWeeksDraft) }
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
