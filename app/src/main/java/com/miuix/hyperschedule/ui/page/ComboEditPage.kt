package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.miuix.hyperschedule.data.model.TimeTable
import com.miuix.hyperschedule.data.model.TimeTableComboRule
import com.miuix.hyperschedule.data.repository.ComboDetail
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.schedule.cjkDigitGap
import com.miuix.hyperschedule.schedule.formatDate
import com.miuix.hyperschedule.schedule.parseDateOrNull
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.ui.component.AddFab
import com.miuix.hyperschedule.ui.component.DatePickerDialog
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.SaveFab
import com.miuix.hyperschedule.ui.component.ValueLabel
import com.miuix.hyperschedule.ui.component.WideActionButton
import com.miuix.hyperschedule.ui.component.rememberEditBackGuard
import com.miuix.hyperschedule.ui.component.toast
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 整页快照，用于判断是否有未保存改动 */
private data class ComboDraft(
    val name: String,
    val baseId: String?,
    val rules: List<TimeTableComboRule>,
)

/** 一条新规则：默认从今天开始，单日 */
private fun newRule(comboId: String, targetId: String, start: LocalDate): TimeTableComboRule =
    TimeTableComboRule(
        id = UUID.randomUUID().toString(),
        comboId = comboId,
        targetTimeTableId = targetId,
        startDate = start.toString(),
        endDate = start.toString(),
    )

/** 规则日期解析，坏数据回落到今天 */
private fun dateOfOrToday(text: String): LocalDate = parseDateOrNull(text) ?: LocalDate.now()

/** 任意两条规则的日期区间是否有交集（日期为 yyyy-MM-dd，可直接比较） */
private fun rulesOverlap(rules: List<TimeTableComboRule>): Boolean =
    rules.sortedBy { it.startDate }.zipWithNext().any { (current, next) -> next.startDate <= current.endDate }

/** 编辑组合作息：基准作息 + 按日期区间切换的规则 */
@Composable
fun ComboEditPage(
    comboId: String,
    repository: ScheduleRepository,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    var detail by remember(comboId) { mutableStateOf<ComboDetail?>(null) }
    var publics by remember { mutableStateOf<List<TimeTable>>(emptyList()) }

    LaunchedEffect(comboId) {
        val loaded =
            if (comboId.isBlank()) {
                repository.newComboDraft()
            } else {
                repository.loadCombo(comboId)
            }
        publics = repository.publicSchedulesOnce()
        // 组合已被删除就直接退回列表
        if (loaded == null) onBack() else detail = loaded
    }

    val loaded = detail
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize())
    } else {
        ComboEditContent(loaded, comboId.isBlank(), publics, repository, appBlur, onBack)
    }
}

@Composable
private fun ComboEditContent(
    detail: ComboDetail,
    isNew: Boolean,
    publics: List<TimeTable>,
    repository: ScheduleRepository,
    appBlur: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val seed =
        remember(detail, publics) {
            val rules =
                detail.rules.ifEmpty {
                    listOf(newRule(detail.combo.id, publics.firstOrNull()?.id.orEmpty(), LocalDate.now()))
                }
            ComboDraft(detail.combo.name, detail.combo.baseTimeTableId, rules)
        }
    var name by remember { mutableStateOf(seed.name) }
    var baseId by remember { mutableStateOf(seed.baseId) }
    val rules = remember { mutableStateListOf<TimeTableComboRule>().apply { addAll(seed.rules) } }

    val dirty = ComboDraft(name, baseId, rules.toList()) != seed

    var dateRuleIndex by remember { mutableIntStateOf(-1) }
    var dateIsStart by remember { mutableStateOf(true) }
    var dateDraft by remember { mutableStateOf(LocalDate.now()) }

    val requestBack = rememberEditBackGuard(dirty, appBlur, onBack)

    fun addRule() {
        // 接在最后一条规则之后，避免新增即与已有规则重叠
        val start =
            rules.maxOfOrNull { it.endDate }
                ?.let { parseDateOrNull(it)?.plusDays(1) }
                ?: LocalDate.now()
        rules += newRule(detail.combo.id, publics.firstOrNull()?.id.orEmpty(), start)
    }

    val canSave =
        name.isNotBlank() &&
            rules.isNotEmpty() &&
            rules.all {
                it.targetTimeTableId.isNotBlank() &&
                    it.startDate.isNotBlank() &&
                    it.endDate.isNotBlank() &&
                    it.startDate <= it.endDate
            }

    val onSave: () -> Unit = {
        if (canSave) {
            if (rulesOverlap(rules)) {
                context.toast("存在重叠规则")
            } else {
                val sorted = rules.sortedBy { it.startDate }
                scope.launch {
                    repository.saveCombo(
                        combo = detail.combo.copy(name = name.trim(), baseTimeTableId = baseId),
                        rules = sorted.map { it.copy(comboId = detail.combo.id) },
                    )
                    context.toast("已保存")
                    onBack()
                }
            }
        }
    }

    PreferenceScreen(
        title = if (isNew) "添加组合作息" else "编辑组合作息",
        appBlur = appBlur,
        onBack = requestBack,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                AddFab(
                    enabled = publics.isNotEmpty(),
                    description = "添加规则",
                    onClick = ::addRule,
                )
                SaveFab(canSave = canSave, onClick = onSave)
            }
        },
    ) {
        TextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = "组合作息名称",
            singleLine = true,
            textStyle = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.fillMaxWidth(),
        )

        val baseOptions =
            remember(publics) {
                listOf("专属作息") + publics.map { it.name.orEmpty().ifBlank { "未命名" } }
            }
        val targetOptions = remember(publics) { publics.map { it.name.orEmpty().ifBlank { "未命名" } } }

        PreferenceSection {
            WindowDropdownPreference(
                title = "基准作息",
                summary = "规则都不匹配时的回退作息",
                items = baseOptions,
                selectedIndex = publics.indexOfFirst { it.id == baseId } + 1,
                onSelectedIndexChange = { index ->
                    baseId = if (index == 0) null else publics.getOrNull(index - 1)?.id
                },
            )
        }

        val noTarget = targetOptions.isEmpty()
        rules.forEachIndexed { index, rule ->
            PreferenceSection("切换规则${index + 1}".cjkDigitGap()) {
                WindowDropdownPreference(
                    title = "目标作息",
                    items = if (noTarget) listOf("请先创建公共作息") else targetOptions,
                    selectedIndex = publics.indexOfFirst { it.id == rule.targetTimeTableId }.coerceAtLeast(0),
                    enabled = !noTarget,
                    onSelectedIndexChange = { selected ->
                        publics.getOrNull(selected)?.let { target ->
                            rules[index] = rule.copy(targetTimeTableId = target.id)
                        }
                    },
                )
                ArrowPreference(
                    title = "开始日期",
                    endActions = { ValueLabel(formatDate(dateOfOrToday(rule.startDate)), Modifier.padding(start = 8.dp)) },
                    onClick = {
                        dateRuleIndex = index
                        dateIsStart = true
                        dateDraft = dateOfOrToday(rule.startDate)
                    },
                )
                ArrowPreference(
                    title = "结束日期",
                    endActions = { ValueLabel(formatDate(dateOfOrToday(rule.endDate)), Modifier.padding(start = 8.dp)) },
                    onClick = {
                        dateRuleIndex = index
                        dateIsStart = false
                        dateDraft = dateOfOrToday(rule.endDate)
                    },
                )
            }
            if (rules.size > 1) {
                WideActionButton(
                    text = "删除",
                    icon = MiuixIcons.Delete,
                    onClick = { rules.removeAt(index) },
                    contentColor = MiuixTheme.colorScheme.error,
                )
            }
        }
    }

    if (dateRuleIndex in rules.indices) {
        val startDate = parseDateOrNull(rules[dateRuleIndex].startDate)
        val endDate = parseDateOrNull(rules[dateRuleIndex].endDate)
        DatePickerDialog(
            title = if (dateIsStart) "开始日期" else "结束日期",
            date = dateDraft,
            onDateChange = { dateDraft = it },
            appBlur = appBlur,
            confirmEnabled =
                when {
                    dateIsStart -> endDate == null || dateDraft <= endDate
                    else -> startDate == null || dateDraft >= startDate
                },
            onDismiss = { dateRuleIndex = -1 },
            onConfirm = {
                val rule = rules[dateRuleIndex]
                rules[dateRuleIndex] =
                    if (dateIsStart) {
                        rule.copy(startDate = dateDraft.toString())
                    } else {
                        rule.copy(endDate = dateDraft.toString())
                    }
                dateRuleIndex = -1
            },
        )
    }
}
