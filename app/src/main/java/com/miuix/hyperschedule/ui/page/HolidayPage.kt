package com.miuix.hyperschedule.ui.page

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.HolidayEntry
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.adjustDate
import com.miuix.hyperschedule.schedule.cjkDigitGap
import com.miuix.hyperschedule.schedule.currentWeekOf
import com.miuix.hyperschedule.schedule.formatDate
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.schedule.weekDatesOf
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.CardInsidePadding
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.DAY_NAMES
import com.miuix.hyperschedule.ui.component.DatePickerRow
import com.miuix.hyperschedule.ui.component.DialogDeleteAction
import com.miuix.hyperschedule.ui.component.NumberWheelRow
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.SectionTitle
import com.miuix.hyperschedule.ui.component.WideActionButton
import com.miuix.hyperschedule.ui.component.pref.ArrowPreference
import com.miuix.hyperschedule.ui.component.pref.OverlayDropdownPreference
import java.time.LocalDate
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val HolidayYears = 2024..2035

/** 节假日与调休：按年维护假期区间与补班日，右下角可拉取远端数据 */
@Composable
fun HolidayPage(
    repository: HolidayRepository,
    table: CourseTableData?,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = table?.config
    val startDateText = config?.semesterStartDate.orEmpty()
    val semesterStart = remember(startDateText) { parseDateOrNull(startDateText) }
    val totalWeeks = (config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS).coerceAtLeast(1)

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
            semesterStart = semesterStart,
            totalWeeks = totalWeeks,
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

/** 一组条目：有内容时一张卡片，末尾跟一个整宽添加入口 */
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
    semesterStart: LocalDate?,
    totalWeeks: Int,
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
    val savedFollowDate = parseDateOrNull(entry?.followDate.orEmpty())
    // 跟随课程按「第 X 周 星期 Y」编辑，落库存绝对日期
    var followWeek by remember {
        mutableIntStateOf(
            (semesterStart?.let { currentWeekOf(it, savedFollowDate ?: startDate) } ?: 1).coerceIn(1, totalWeeks),
        )
    }
    var followWeekday by remember { mutableIntStateOf(savedFollowDate?.dayOfWeek?.value ?: 1) }
    val followTarget =
        remember(semesterStart, followWeek, followWeekday) {
            semesterStart?.let { weekDatesOf(it, followWeek).getOrNull(followWeekday - 1) }
        }

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
                    followDate = if (isHoliday) "" else followTarget?.toString().orEmpty(),
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

        DateField(
            label = if (isHoliday) "开始日期" else "调休日期",
            date = startDate,
            onDateChange = { startDate = it },
        )

        if (isHoliday) {
            DateField(label = "结束日期", date = endDate, onDateChange = { endDate = it })
        } else {
            FollowCourseField(
                semesterStart = semesterStart,
                followWeek = followWeek,
                followWeekday = followWeekday,
                followTarget = followTarget,
                totalWeeks = totalWeeks,
                onWeekChange = { followWeek = it },
                onWeekdayChange = { followWeekday = it },
            )
        }
    }
}

/** 调休「跟随课程」：小标题右侧显示推算出的日期，下面是周次与星期两个滚轮 */
@Composable
private fun FollowCourseField(
    semesterStart: LocalDate?,
    followWeek: Int,
    followWeekday: Int,
    followTarget: LocalDate?,
    totalWeeks: Int,
    onWeekChange: (Int) -> Unit,
    onWeekdayChange: (Int) -> Unit,
) {
    Column {
        if (semesterStart == null) {
            SectionTitle("跟随课程")
            Text(
                text = "请先在设置中填写「开始上课日期」",
                modifier = Modifier.fillMaxWidth().padding(horizontal = CardInsidePadding),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        } else {
            // 推算出的日期右对齐在小标题旁，方便核对「补的是哪一天」
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("跟随课程", modifier = Modifier.weight(1f))
                if (followTarget != null) {
                    Text(
                        text = formatDate(followTarget),
                        modifier = Modifier.padding(end = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardCornerRadius,
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    NumberWheelRow(
                        value = followWeek,
                        range = 1..totalWeeks,
                        label = { "第${it}周".cjkDigitGap() },
                        onValueChange = onWeekChange,
                        modifier = Modifier.weight(1f),
                    )
                    NumberWheelRow(
                        value = followWeekday,
                        range = 1..7,
                        label = { DAY_NAMES[it - 1] },
                        onValueChange = onWeekdayChange,
                        modifier = Modifier.weight(1f),
                        wrapAround = true,
                    )
                }
            }
        }
    }
}

/** 一行日期选择：小标题 + 一张卡片 */
@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    Column {
        SectionTitle(label)
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardCornerRadius,
        ) {
            DatePickerRow(
                year = date.year,
                onYearChange = { onDateChange(adjustDate(date, year = it)) },
                month = date.monthValue,
                onMonthChange = { onDateChange(adjustDate(date, month = it)) },
                day = date.dayOfMonth,
                onDayChange = { onDateChange(adjustDate(date, day = it)) },
            )
        }
    }
}

/** yyyy-MM-dd → yyyy/MM/dd；无法解析时原样返回 */
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
