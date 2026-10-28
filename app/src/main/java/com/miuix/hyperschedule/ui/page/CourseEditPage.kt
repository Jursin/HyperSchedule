package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.PageBottomInset
import com.miuix.hyperschedule.theme.PresetColors
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.isDarkTheme
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.AddFab
import com.miuix.hyperschedule.ui.component.AwardStar
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.Book
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.SaveFab
import com.miuix.hyperschedule.ui.component.courseColorOf
import com.miuix.hyperschedule.ui.component.formatCredit
import com.miuix.hyperschedule.ui.component.pageContentPadding
import com.miuix.hyperschedule.ui.component.pageScroll
import com.miuix.hyperschedule.ui.component.rememberEditBackGuard
import com.miuix.hyperschedule.ui.component.toast
import com.miuix.hyperschedule.ui.page.courseedit.ColorCard
import com.miuix.hyperschedule.ui.page.courseedit.EditState
import com.miuix.hyperschedule.ui.page.courseedit.GroupCard
import com.miuix.hyperschedule.ui.page.courseedit.GroupDraft
import com.miuix.hyperschedule.ui.page.courseedit.InfoRow
import com.miuix.hyperschedule.ui.page.courseedit.InlineField
import com.miuix.hyperschedule.ui.page.courseedit.Slot
import com.miuix.hyperschedule.ui.page.courseedit.ThinDivider
import com.miuix.hyperschedule.ui.page.courseedit.buildSeed
import com.miuix.hyperschedule.ui.page.courseedit.overlaps
import com.miuix.hyperschedule.ui.page.courseedit.sanitizeCredit
import com.miuix.hyperschedule.ui.page.courseedit.toArgbInt
import com.miuix.hyperschedule.ui.page.courseedit.toCourse
import com.miuix.hyperschedule.ui.page.courseedit.toSlot
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import java.util.UUID
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * 课程编辑页：所有改动都留在内存，点保存才一次写入；直接返回会丢弃。
 * [initialName] 为空表示新建，此时用 [prefillDay] / [prefillStart] / [prefillEnd] 预填第一张卡。
 */
@Composable
fun CourseEditPage(
    initialName: String,
    prefillDay: Int,
    prefillStart: Int,
    prefillEnd: Int,
    focusId: String,
    table: CourseTableData?,
    allCourses: List<CourseWithWeeks>,
    repository: ScheduleRepository,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val tableId = table?.tableId
    val sectionCount = (table?.slots?.size ?: 0).coerceAtLeast(1)
    val totalWeeks = (table?.config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS).coerceAtLeast(1)
    val sectionTimes = table?.sectionTimes.orEmpty()
    val isDark = isDarkTheme()

    // 进入时读一次库，之后全部改内存
    val seed = remember { buildSeed(initialName, prefillDay, prefillStart, prefillEnd, allCourses, tableId, totalWeeks) }

    var name by remember { mutableStateOf(seed.name) }
    var colorInt by remember { mutableStateOf(seed.colorInt) }
    var colorArgb by remember { mutableStateOf(seed.colorArgb) }
    var credit by remember { mutableStateOf(seed.credit) }
    val groups = remember { mutableStateListOf<GroupDraft>().apply { addAll(seed.groups) } }
    // 库里已有、当前草稿仍保留的时段 id；保存时用它算出要删除的行
    var savedIds by remember { mutableStateOf(seed.groups.map { it.id }.toSet()) }

    fun currentState() = EditState(name, colorInt, colorArgb, credit, groups.toList())

    var baseline by remember { mutableStateOf(seed) }
    // 只在草稿实际变化时重算，避免输入框每次重组都全量比较
    val dirty by remember { derivedStateOf { currentState() != baseline } }

    // 数据晚于首帧到达时（如进程重建）补一次填充
    var seeded by remember { mutableStateOf(seed.groups.isNotEmpty() || initialName.isBlank()) }
    LaunchedEffect(allCourses, tableId) {
        if (seeded || tableId == null) return@LaunchedEffect
        val loaded = buildSeed(initialName, prefillDay, prefillStart, prefillEnd, allCourses, tableId, totalWeeks)
        if (loaded.groups.isEmpty()) return@LaunchedEffect
        name = loaded.name
        colorInt = loaded.colorInt
        colorArgb = loaded.colorArgb
        credit = loaded.credit
        groups.clear()
        groups.addAll(loaded.groups)
        savedIds = loaded.groups.map { it.id }.toSet()
        baseline = loaded
        seeded = true
    }

    // 从课程详情进来时定位到对应的时段卡片
    val listState = rememberLazyListState()
    LaunchedEffect(focusId, seeded) {
        val index = groups.indexOfFirst { it.id == focusId }
        if (index >= 0) listState.scrollToItem(index + 2)
    }

    var colorDialog by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(PresetColors.first().color) }
    var dayDialogIndex by remember { mutableIntStateOf(-1) }
    var timeDialogIndex by remember { mutableIntStateOf(-1) }
    var weeksDialogIndex by remember { mutableIntStateOf(-1) }

    val requestBack = rememberEditBackGuard(dirty, appBlur, onBack)

    fun addGroup() {
        val last = groups.lastOrNull()
        groups.add(
            GroupDraft(
                id = UUID.randomUUID().toString(),
                day = last?.day ?: 1,
                startSection = last?.startSection ?: 1,
                endSection = maxOf(last?.startSection ?: 1, last?.endSection ?: 1),
                isCustomTime = last?.isCustomTime ?: false,
                customStartTime = last?.customStartTime,
                customEndTime = last?.customEndTime,
                position = "",
                teacher = "",
                weeks = (1..totalWeeks).toSet(),
                remark = "",
            )
        )
    }

    val onSave: () -> Unit = {
        val trimmedName = name.trim()
        when {
            trimmedName.isEmpty() -> context.toast("请填写课程名称")
            groups.any { it.weeks.isEmpty() } -> context.toast("请选择上课周次")
            groups.any { it.isCustomTime && (it.customStartTime.isNullOrBlank() || it.customEndTime.isNullOrBlank()) } ->
                context.toast("请选择上课时间")
            credit.isNotBlank() && credit.toFloatOrNull() == null -> context.toast("学分格式不正确")
            else -> {
                if (tableId == null) {
                    context.toast("课表未加载")
                } else {
                    // 保存时规整名称与学分，避免回显值与库里的不一致导致立刻又算「未保存」
                    name = trimmedName
                    val creditValue = credit.trim().toFloatOrNull()
                    credit = creditValue?.let(::formatCredit).orEmpty()
                    val snapshot = currentState()
                    val colorIndex = colorInt ?: 0
                    val keepIds = groups.map { it.id }.toSet()
                    scope.launch {
                        repository.saveCourseEdits(
                            deletedIds = (savedIds - keepIds).toList(),
                            courses =
                                groups.map { group ->
                                    group.toCourse(tableId, trimmedName, colorIndex, colorArgb, creditValue) to group.weeks.toList()
                                },
                        )
                        savedIds = keepIds
                        baseline = snapshot
                        context.toast("已保存")
                        onBack()
                    }
                }
            }
        }
    }

    /** 与第 [index] 组时段冲突的其它时段已占用的周次 */
    fun occupiedWeeksOf(index: Int): Set<Int> {
        val me = groups.getOrNull(index)?.toSlot() ?: return emptySet()
        val others = mutableListOf<Slot>()
        groups.forEachIndexed { i, group -> if (i != index) others += group.toSlot() }
        allCourses
            .filter { it.course.courseTableId == tableId && it.course.id !in savedIds }
            .forEach { others += it.course.toSlot(it.weeks.map { w -> w.weekNumber }.toSet()) }
        return others.filter { it.overlaps(me, sectionTimes) }.flatMapTo(mutableSetOf()) { it.weeks }
    }

    Scaffold(
        topBar = {
            BlurredTopAppBar(
                // 标题固定为进入时的课程名，打字时不跟着变
                title = initialName.ifBlank { "添加课程" },
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                onBack = requestBack,
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().pageScroll(blurBackdrop, scrollBehavior),
                contentPadding = pageContentPadding(paddingValues, bottom = PageBottomInset),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                overscrollEffect = null,
            ) {
                item(key = "basic") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardCornerRadius,
                    ) {
                        InfoRow(icon = Book, label = "课程名称") {
                            InlineField(value = name, onValueChange = { name = it }, hint = "必填")
                        }
                        ThinDivider()
                        InfoRow(icon = AwardStar, label = "学分") {
                            InlineField(
                                value = credit,
                                onValueChange = { credit = sanitizeCredit(it) },
                                hint = "选填",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }

                item(key = "color") {
                    ColorCard(
                        colorInt = colorInt,
                        colorArgb = colorArgb,
                        isDark = isDark,
                        onPick = { index, argb ->
                            colorInt = index
                            colorArgb = argb
                        },
                        onPickCustom = {
                            customColor = courseColorOf(colorInt ?: 0, colorArgb)
                            colorDialog = true
                        },
                    )
                }

                itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
                    GroupCard(
                        group = group,
                        canDelete = groups.size > 1,
                        onChange = { groups[index] = it },
                        onPickDay = { dayDialogIndex = index },
                        onPickTime = { timeDialogIndex = index },
                        onPickWeeks = { weeksDialogIndex = index },
                        onDelete = { if (groups.size > 1) groups.removeAt(index) },
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                horizontalAlignment = Alignment.End,
            ) {
                AddFab(enabled = true, description = "添加时段", onClick = ::addGroup)
                SaveFab(canSave = true, onClick = onSave)
            }
        }
    }

    if (colorDialog) {
        ConfirmDialog(
            title = "选择颜色",
            appBlur = appBlur,
            onDismiss = { colorDialog = false },
            onConfirm = {
                colorInt = colorInt ?: 0
                colorArgb = customColor.toArgbInt()
                colorDialog = false
            },
        ) {
            ColorPalette(
                color = customColor,
                onColorChanged = { customColor = it },
                cornerRadius = 20.dp,
                indicatorRadius = 12.dp,
            )
        }
    }

    val dayIndex = dayDialogIndex
    if (dayIndex in groups.indices) {
        DayPickerDialog(
            current = groups[dayIndex].day,
            appBlur = appBlur,
            onDismiss = { dayDialogIndex = -1 },
            onConfirm = { day ->
                groups[dayIndex] = groups[dayIndex].copy(day = day)
                dayDialogIndex = -1
            },
        )
    }

    val timeIndex = timeDialogIndex
    if (timeIndex in groups.indices) {
        TimeDialog(
            group = groups[timeIndex],
            sectionCount = sectionCount,
            sectionTimes = sectionTimes,
            appBlur = appBlur,
            onDismiss = { timeDialogIndex = -1 },
            onConfirm = { custom, start, end, startTime, endTime ->
                groups[timeIndex] =
                    groups[timeIndex].copy(
                        isCustomTime = custom,
                        startSection = start,
                        endSection = end,
                        customStartTime = startTime,
                        customEndTime = endTime,
                    )
                timeDialogIndex = -1
            },
        )
    }

    val weeksIndex = weeksDialogIndex
    if (weeksIndex in groups.indices) {
        WeeksDialog(
            initial = groups[weeksIndex].weeks,
            totalWeeks = totalWeeks,
            occupied = occupiedWeeksOf(weeksIndex),
            appBlur = appBlur,
            onDismiss = { weeksDialogIndex = -1 },
            onConfirm = { weeks ->
                groups[weeksIndex] = groups[weeksIndex].copy(weeks = weeks)
                weeksDialogIndex = -1
            },
        )
    }
}
