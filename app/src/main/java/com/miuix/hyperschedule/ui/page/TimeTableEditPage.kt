package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.SpecialTimeBlock
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.repository.PlanDetail
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.schedule.SECTION_COUNT_RANGE
import com.miuix.hyperschedule.schedule.cjkDigitGap
import com.miuix.hyperschedule.schedule.parseMinutes
import com.miuix.hyperschedule.ui.component.NumberPickerDialog
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.SaveFab
import com.miuix.hyperschedule.ui.component.TimeRangeDialog
import com.miuix.hyperschedule.ui.component.ValueLabel
import com.miuix.hyperschedule.ui.component.formatWeekdays
import com.miuix.hyperschedule.ui.component.pref.ArrowPreference
import com.miuix.hyperschedule.ui.component.rememberEditBackGuard
import com.miuix.hyperschedule.ui.component.toast
import java.util.UUID
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 编辑作息：所有改动留在内存，点保存才写入；直接返回会丢弃 */
@Composable
fun TimeTableEditPage(
    planId: String,
    repository: ScheduleRepository,
    tableId: String,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    var detail by remember(planId) { mutableStateOf<PlanDetail?>(null) }
    LaunchedEffect(planId, tableId) {
        detail =
            when {
                planId.isNotBlank() -> repository.loadPlan(planId)
                // 新建要复制当前课表生效的作息，没有课表就没法建
                tableId.isBlank() -> null
                else -> repository.newPublicScheduleDraft(tableId)
            }
        // 方案已被删除（例如另一处删掉了）就直接退回列表
        if (detail == null) onBack()
    }

    val loaded = detail
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize())
    } else {
        PlanEditContent(loaded, planId.isBlank(), repository, appBlur, onBack)
    }
}

@Composable
private fun PlanEditContent(
    detail: PlanDetail,
    isNew: Boolean,
    repository: ScheduleRepository,
    appBlur: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val planId = detail.plan.id
    val isExclusive = detail.plan.isExclusive

    val seed = remember(detail) { buildDraft(detail) }
    // 节次别名（如「早自习」）编辑页不展示，保存时按序号原样带回
    val aliasByNumber = remember(detail) { detail.slots.associate { it.number to it.alias } }

    var name by remember { mutableStateOf(seed.name) }
    var sectionCount by remember { mutableIntStateOf(seed.sectionCount) }
    var splitAM by remember { mutableStateOf(seed.splitMorningAfternoon) }
    var splitAE by remember { mutableStateOf(seed.splitAfternoonEvening) }
    var classMinutes by remember { mutableIntStateOf(seed.classMinutes) }
    val slots = remember { mutableStateListOf<SlotDraft>().apply { addAll(seed.slots) } }
    val specials = remember { mutableStateListOf<SpecialDraft>().apply { addAll(seed.specials) } }

    fun snapshot() =
        PlanDraft(
            name = name,
            sectionCount = sectionCount,
            splitMorningAfternoon = splitAM,
            splitAfternoonEvening = splitAE,
            classMinutes = classMinutes,
            slots = slots.toList(),
            specials = specials.toList(),
        )

    var baseline by remember { mutableStateOf(seed) }
    val dirty = snapshot() != baseline

    var countDialog by remember { mutableStateOf(false) }
    var countDraft by remember { mutableIntStateOf(sectionCount) }
    var splitDialog by remember { mutableStateOf(false) }
    var durationDialog by remember { mutableStateOf(false) }
    var durationIndex by remember { mutableIntStateOf(classDurationIndex(classMinutes)) }
    var sectionTimeIndex by remember { mutableIntStateOf(-1) }
    var editingSpecial by remember { mutableStateOf<SpecialDraft?>(null) }

    val requestBack = rememberEditBackGuard(dirty, appBlur, onBack)

    fun applyCount(value: Int) {
        sectionCount = value
        while (slots.size < value) slots += SlotDraft("", "")
        while (slots.size > value) slots.removeAt(slots.lastIndex)
    }

    val splitAMValue = parseMinutes(splitAM)
    val splitAEValue = parseMinutes(splitAE)
    val canSave =
        (isExclusive || name.isNotBlank()) &&
            splitAMValue != null &&
            splitAEValue != null &&
            splitAEValue > splitAMValue &&
            slots.all { it.start.isNotBlank() && it.end.isNotBlank() }

    val onSave: () -> Unit = {
        if (canSave) {
            name = name.trim()
            val draft = snapshot()
            // 名称被清空的特殊课程不入库，从基线里消失即视为删除
            val kept = draft.specials.filter { it.name.isNotBlank() }
            scope.launch {
                repository.saveSingleSchedule(
                    plan =
                        detail.plan.copy(
                            name = if (isExclusive) null else draft.name,
                            defaultClassDuration = draft.classMinutes,
                            morningAfternoonSplit = draft.splitMorningAfternoon,
                            afternoonEveningSplit = draft.splitAfternoonEvening,
                        ),
                    slots =
                        draft.slots.mapIndexed { index, slot ->
                            TimeSlot(planId, index + 1, slot.start, slot.end, aliasByNumber[index + 1])
                        },
                    specials =
                        kept.map {
                            SpecialTimeBlock(
                                id = it.id,
                                timeTableId = planId,
                                name = it.name.trim(),
                                startTime = it.start,
                                endTime = it.end,
                                days = SpecialTimeBlock.encodeDays(it.days),
                            )
                        },
                    deletedSpecialIds = baseline.specials.map { it.id } - kept.map { it.id }.toSet(),
                )
                baseline = draft
                context.toast("已保存")
                onBack()
            }
        }
    }

    val slotList = slots.toList()
    val groups =
        remember(slotList, splitAMValue, splitAEValue) {
            groupSections(
                slots = slotList,
                splitAM = splitAMValue ?: DefaultMorningSplit,
                splitAE = splitAEValue ?: DefaultEveningSplit,
            )
        }

    PreferenceScreen(
        title =
            when {
                isExclusive -> "编辑专属作息"
                isNew -> "添加公共作息"
                else -> "编辑公共作息"
            },
        appBlur = appBlur,
        onBack = requestBack,
        floatingActionButton = { SaveFab(canSave = canSave, onClick = onSave) },
    ) {
        if (!isExclusive) {
            TextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = "作息名称",
                singleLine = true,
                textStyle = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PreferenceSection {
            ArrowPreference(
                title = "节次数量",
                endActions = { ValueLabel("共${sectionCount}节".cjkDigitGap()) },
                onClick = {
                    countDraft = sectionCount
                    countDialog = true
                },
            )
            ArrowPreference(
                title = "时段分隔",
                endActions = { ValueLabel("$splitAM · $splitAE") },
                onClick = { splitDialog = true },
            )
        }

        PreferenceSection("快捷设置") {
            ArrowPreference(
                title = "每节课时长",
                summary = "设置节次时间时按它推算结束时间",
                endActions = { ValueLabel("${classMinutes}分钟".cjkDigitGap()) },
                onClick = {
                    durationIndex = classDurationIndex(classMinutes)
                    durationDialog = true
                },
            )
        }

        SectionTimeSections(groups = groups, slots = slots, onPick = { sectionTimeIndex = it })

        SpecialCourseSection(
            specials = specials,
            onEdit = { editingSpecial = it },
            onDelete = { target -> specials.removeAll { it.id == target.id } },
            onAdd = {
                editingSpecial = SpecialDraft(UUID.randomUUID().toString(), "", "07:00", "07:40")
            },
        )
    }

    if (countDialog) {
        NumberPickerDialog(
            title = "节次数量",
            value = countDraft,
            onValueChange = { countDraft = it },
            range = SECTION_COUNT_RANGE,
            label = { "共${it}节".cjkDigitGap() },
            appBlur = appBlur,
            onDismiss = { countDialog = false },
            onConfirm = {
                countDialog = false
                if (countDraft != sectionCount) applyCount(countDraft)
            },
        )
    }

    if (splitDialog) {
        TimeRangeDialog(
            title = "时段分隔",
            start = splitAM,
            end = splitAE,
            startLabel = "午休",
            endLabel = "晚休",
            appBlur = appBlur,
            onDismiss = { splitDialog = false },
            onConfirm = { first, second ->
                splitAM = first
                splitAE = second
                splitDialog = false
            },
        )
    }

    if (durationDialog) {
        NumberPickerDialog(
            title = "每节课时长",
            value = durationIndex,
            onValueChange = { durationIndex = it },
            range = ClassDurationValues.indices,
            label = { "${ClassDurationValues[it]}分钟".cjkDigitGap() },
            appBlur = appBlur,
            onDismiss = { durationDialog = false },
            onConfirm = {
                classMinutes = ClassDurationValues[durationIndex]
                durationDialog = false
            },
        )
    }

    val timeIndex = sectionTimeIndex
    if (timeIndex in slots.indices) {
        val slot = slots[timeIndex]
        val previousEnd = slots.getOrNull(timeIndex - 1)?.end.orEmpty()
        val initialStart = slot.start.ifBlank { previousEnd.ifBlank { "08:00" } }
        TimeRangeDialog(
            title = "第${timeIndex + 1}节".cjkDigitGap(),
            start = initialStart,
            // 未设置时交给对话框按「开始 + 每节时长」推算
            end = slot.end,
            durationMinutes = classMinutes.coerceAtLeast(1),
            appBlur = appBlur,
            conflictRanges =
                slots.filterIndexed { index, _ -> index != timeIndex }.mapNotNull { other ->
                    val otherStart = parseMinutes(other.start)
                    val otherEnd = parseMinutes(other.end)
                    if (otherStart == null || otherEnd == null) null else otherStart to otherEnd
                },
            onDismiss = { sectionTimeIndex = -1 },
            onConfirm = { start, end ->
                slots[timeIndex] = SlotDraft(start, end)
                sectionTimeIndex = -1
            },
        )
    }

    editingSpecial?.let { special ->
        SpecialBlockDialog(
            initial = special,
            appBlur = appBlur,
            onDismiss = { editingSpecial = null },
            onConfirm = { updated ->
                val index = specials.indexOfFirst { it.id == updated.id }
                if (index >= 0) specials[index] = updated else specials += updated
                editingSpecial = null
            },
        )
    }
}

/** 节次列表：按上午/下午/晚上分组，一行一节 */
@Composable
private fun SectionTimeSections(
    groups: List<SectionGroup>,
    slots: List<SlotDraft>,
    onPick: (Int) -> Unit,
) {
    groups.forEach { group ->
        PreferenceSection(group.period.label()) {
            group.indices.forEach { slotIndex ->
                val slot = slots[slotIndex]
                ArrowPreference(
                    title = "第${slotIndex + 1}节".cjkDigitGap(),
                    endActions = {
                        if (slot.start.isBlank() || slot.end.isBlank()) {
                            ValueLabel("未设置", placeholder = true)
                        } else {
                            ValueLabel("${slot.start} – ${slot.end}")
                        }
                    },
                    onClick = { onPick(slotIndex) },
                )
            }
        }
    }
}

/** 特殊课程列表：一行一节，末尾是添加入口 */
@Composable
private fun SpecialCourseSection(
    specials: List<SpecialDraft>,
    onEdit: (SpecialDraft) -> Unit,
    onDelete: (SpecialDraft) -> Unit,
    onAdd: () -> Unit,
) {
    PreferenceSection("特殊节次") {
        specials.forEach { special ->
            SpecialRow(
                special = special,
                onEdit = { onEdit(special) },
                onDelete = { onDelete(special) },
            )
        }
        ArrowPreference(
            title = "添加特殊节次",
            summary = "早自习、晚自习等不占节次的固定时段",
            onClick = onAdd,
        )
    }
}

/** 特殊课程行：名称 + 时间，右侧删除 */
@Composable
private fun SpecialRow(
    special: SpecialDraft,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEdit,
                )
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = special.name.ifBlank { "未命名" },
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${special.start} – ${special.end} · ${formatWeekdays(special.days)}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = "删除特殊课程",
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
