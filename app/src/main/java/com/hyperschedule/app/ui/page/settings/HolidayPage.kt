package com.hyperschedule.app.ui.page.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.model.HolidayEntry
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.schedule.formatDate
import com.hyperschedule.app.schedule.parseDateOrNull
import com.hyperschedule.app.theme.CardCornerRadius
import com.hyperschedule.app.ui.component.ConfirmDialog
import com.hyperschedule.app.ui.component.DateRangeCalendar
import com.hyperschedule.app.ui.component.DialogDeleteAction
import com.hyperschedule.app.ui.component.DualDateCalendar
import com.hyperschedule.app.ui.component.PreferenceScreen
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.SectionTitle
import com.hyperschedule.app.ui.component.WideActionButton
import com.hyperschedule.app.ui.component.pref.ArrowPreference
import com.hyperschedule.app.ui.component.pref.OverlayDropdownPreference
import java.time.LocalDate
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val HolidayYears = 2024..2035

/** 节假日与调休，按年维护假期区间与补班日，右下角可拉取远端数据 */
@Composable
fun HolidayPage(
    repository: HolidayRepository,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var year by remember { mutableIntStateOf(LocalDate.now().year.coerceIn(HolidayYears)) }
    val entries by remember(year) { repository.observe(year) }.collectAsState(emptyList())
    val years = HolidayYears.toList()

    var loading by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogType by remember { mutableIntStateOf(HolidayEntry.TYPE_HOLIDAY) }
    var editing by remember { mutableStateOf<HolidayEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<HolidayEntry?>(null) }

    PreferenceScreen(
        title = "节假日与调休",
        appBlur = appBlur,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!loading) {
                        loading = true
                        scope.launch {
                            val result = runCatching { repository.fetchRemote(year) }
                            loading = false
                            Toast.makeText(
                                context,
                                result.fold({ "已更新${it}条${year}年数据".cjkDigitGap() }, { "更新失败：${it.message ?: "网络错误"}" }),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        size = 24.dp,
                        colors =
                            ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.onPrimary,
                                backgroundColor = Color.Transparent,
                            ),
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = "更新",
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) {
        PreferenceSection {
            OverlayDropdownPreference(
                title = "年份",
                items = years.map { it.toString() },
                selectedIndex = years.indexOf(year),
                onSelectedIndexChange = { year = years[it] },
            )
        }

        HolidayGroup(
            title = "节假日",
            entries = entries.filter { it.type == HolidayEntry.TYPE_HOLIDAY },
            addText = "添加节假日",
            onEdit = {
                editing = it
                dialogType = HolidayEntry.TYPE_HOLIDAY
                showDialog = true
            },
            onAdd = {
                editing = null
                dialogType = HolidayEntry.TYPE_HOLIDAY
                showDialog = true
            },
        )

        HolidayGroup(
            title = "调休工作日",
            entries = entries.filter { it.type == HolidayEntry.TYPE_WORKSWAP },
            addText = "添加调休工作日",
            onEdit = {
                editing = it
                dialogType = HolidayEntry.TYPE_WORKSWAP
                showDialog = true
            },
            onAdd = {
                editing = null
                dialogType = HolidayEntry.TYPE_WORKSWAP
                showDialog = true
            },
        )
    }

    if (showDialog) {
        HolidayEditDialog(
            entry = editing,
            type = dialogType,
            year = year,
            appBlur = appBlur,
            onDismiss = { showDialog = false },
            onSave = { updated ->
                showDialog = false
                scope.launch { repository.upsert(updated, editing) }
            },
            onDelete = {
                pendingDelete = editing
                showDialog = false
            },
        )
    }

    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        ConfirmDialog(
            title = "删除记录",
            message = "确定要删除这条${if (deleteTarget.type == HolidayEntry.TYPE_HOLIDAY) "节假日" else "调休工作日"}记录吗？\n此操作不可撤销。",
            appBlur = appBlur,
            destructive = true,
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch { repository.delete(deleteTarget) }
            },
        )
    }
}

/** 一组条目，有内容时一张卡片，末尾跟一个整宽添加入口 */
@Composable
private fun HolidayGroup(
    title: String,
    entries: List<HolidayEntry>,
    addText: String,
    onEdit: (HolidayEntry) -> Unit,
    onAdd: () -> Unit,
) {
    if (entries.isEmpty()) {
        // 没有条目时只留分组标题，避免出现空卡片
        SectionTitle(title)
    } else {
        PreferenceSection(title = title) {
            entries.forEach { entry ->
                ArrowPreference(
                    title = entry.name,
                    summary = entrySummary(entry),
                    onClick = { onEdit(entry) },
                )
            }
        }
    }
    WideActionButton(text = addText, icon = MiuixIcons.Add, onClick = onAdd)
}

@Composable
private fun HolidayEditDialog(
    entry: HolidayEntry?,
    type: Int,
    year: Int,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onSave: (HolidayEntry) -> Unit,
    onDelete: () -> Unit,
) {
    val isHoliday = type == HolidayEntry.TYPE_HOLIDAY
    // 打开时是「添加」还是「编辑」固定下来，避免外部 entry 变化导致标题跳动
    val isNewEntry = remember { entry == null }
    val fallbackDate =
        remember(year) {
            if (year == LocalDate.now().year) LocalDate.now() else LocalDate.of(year, 1, 1)
        }
    var name by remember { mutableStateOf(entry?.name.orEmpty()) }
    var startDate by remember { mutableStateOf(parseDateOrNull(entry?.date.orEmpty()) ?: fallbackDate) }
    var endDate by remember { mutableStateOf(parseDateOrNull(entry?.endDate.orEmpty()) ?: startDate) }
    // 调休补哪一天的课，按绝对日期选，未设置时为空
    var followDate by remember { mutableStateOf(parseDateOrNull(entry?.followDate.orEmpty())) }

    val canSave = name.isNotBlank() && (isHoliday.not() || endDate >= startDate)
    val title =
        when {
            isNewEntry && isHoliday -> "添加节假日"
            isNewEntry -> "添加调休工作日"
            isHoliday -> "编辑节假日"
            else -> "编辑调休工作日"
        }

    // 只有编辑已有条目才给删除入口，放在标题右侧
    val deleteAction: (@Composable () -> Unit)? =
        if (entry == null) {
            null
        } else {
            { DialogDeleteAction(onClick = onDelete) }
        }

    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        confirmEnabled = canSave,
        endAction = deleteAction,
        onDismiss = onDismiss,
        onConfirm = {
            onSave(
                HolidayEntry(
                    date = startDate.toString(),
                    name = name.trim().ifBlank { if (isHoliday) "节假日" else "调休工作日" },
                    type = type,
                    endDate = if (isHoliday) endDate.toString() else "",
                    followDate = if (isHoliday) "" else followDate?.toString().orEmpty(),
                    custom = true,
                ),
            )
        },
    ) {
        TextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = "名称",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isHoliday) {
            CalendarField(title = "假期日期") {
                DateRangeCalendar(
                    startDate = startDate,
                    endDate = endDate,
                    onRangeChange = { start, end ->
                        startDate = start
                        endDate = end
                    },
                )
            }
        } else {
            CalendarField(title = "调休日与补课日") {
                DualDateCalendar(
                    primaryDate = startDate,
                    secondaryDate = followDate,
                    onPrimaryChange = { startDate = it },
                    onSecondaryChange = { followDate = it },
                    startWithSecondary = !isNewEntry,
                )
            }
        }
    }
}

@Composable
private fun CalendarField(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardCornerRadius,
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        ) {
            content()
        }
    }
}

/** yyyy-MM-dd → yyyy/MM/dd，无法解析时原样返回 */
private fun displayDate(value: String): String = parseDateOrNull(value)?.let(::formatDate) ?: value

private fun entrySummary(entry: HolidayEntry): String {
    val start = displayDate(entry.date)
    if (entry.type == HolidayEntry.TYPE_HOLIDAY) {
        return if (entry.endDate.isBlank()) start else "${start}至${displayDate(entry.endDate)}".cjkDigitGap()
    }
    val follow = parseDateOrNull(entry.followDate)
    val tail = if (follow == null) "未设置跟随课程" else "上${follow.monthValue}月${follow.dayOfMonth}日的课".cjkDigitGap()
    return "$start · $tail"
}
