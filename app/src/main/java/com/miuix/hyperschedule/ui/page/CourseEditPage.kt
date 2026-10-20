package com.miuix.hyperschedule.ui.page

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.CoursePalette
import com.miuix.hyperschedule.ui.component.courseColorOf
import com.miuix.hyperschedule.ui.component.dayLabel
import com.miuix.hyperschedule.ui.component.formatCredit
import com.miuix.hyperschedule.ui.component.formatWeeks
import com.miuix.hyperschedule.ui.theme.captureForBlur
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import java.util.UUID
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val DeleteRed = Color(0xFFF44336)

/** 编辑页里的一个时段草稿：保存前只存在内存里 */
internal data class GroupDraft(
    val id: String,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val isCustomTime: Boolean,
    val customStartTime: String?,
    val customEndTime: String?,
    val position: String,
    val teacher: String,
    val weeks: Set<Int>,
    val remark: String,
)

/** 整页快照，用于判断是否有未保存改动 */
private data class EditState(
    val name: String,
    val colorInt: Int?,
    val colorArgb: Int?,
    val credit: String,
    val groups: List<GroupDraft>,
)

/** 从库里读出的初始草稿；[initialName] 为空时按预填的星期与节次造一张空卡 */
private fun buildSeed(
    initialName: String,
    prefillDay: Int,
    prefillStart: Int,
    prefillEnd: Int,
    allCourses: List<CourseWithWeeks>,
    tableId: String?,
    totalWeeks: Int,
): EditState {
    if (initialName.isBlank()) {
        val start = prefillStart.coerceAtLeast(1)
        return EditState(
            name = "",
            colorInt = null,
            colorArgb = null,
            credit = "",
            groups =
                listOf(
                    GroupDraft(
                        id = UUID.randomUUID().toString(),
                        day = prefillDay.takeIf { it in 1..7 } ?: 1,
                        startSection = start,
                        endSection = prefillEnd.coerceAtLeast(start),
                        isCustomTime = false,
                        customStartTime = null,
                        customEndTime = null,
                        position = "",
                        teacher = "",
                        weeks = (1..totalWeeks).toSet(),
                        remark = "",
                    )
                ),
        )
    }
    val rows =
        allCourses
            .filter { it.course.name == initialName && it.course.courseTableId == tableId }
            .sortedWith(
                compareBy(
                    { it.course.day },
                    { it.course.isCustomTime },
                    { it.course.startSection ?: 99 },
                    { it.course.customStartTime ?: "99:99" },
                )
            )
    return EditState(
        name = initialName,
        colorInt = rows.firstOrNull()?.course?.colorInt,
        colorArgb = rows.firstOrNull()?.course?.colorArgb,
        credit = rows.firstOrNull()?.course?.credit?.let(::formatCredit).orEmpty(),
        groups =
            rows.map { row ->
                GroupDraft(
                    id = row.course.id,
                    day = row.course.day,
                    startSection = row.course.startSection ?: 1,
                    endSection = row.course.endSection ?: row.course.startSection ?: 1,
                    isCustomTime = row.course.isCustomTime,
                    customStartTime = row.course.customStartTime,
                    customEndTime = row.course.customEndTime,
                    position = row.course.position,
                    teacher = row.course.teacher,
                    weeks = row.weeks.map { it.weekNumber }.toSet(),
                    remark = row.course.remark.orEmpty(),
                )
            },
    )
}

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
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f

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
    val dirty = currentState() != baseline

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
    var customColor by remember { mutableStateOf(Color(0xFF4CAF50)) }
    var dayDialogIndex by remember { mutableIntStateOf(-1) }
    var timeDialogIndex by remember { mutableIntStateOf(-1) }
    var weeksDialogIndex by remember { mutableIntStateOf(-1) }
    var discardDialog by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun requestBack() {
        if (dirty) discardDialog = true else onBack()
    }

    BackHandler(enabled = dirty) { discardDialog = true }

    fun addGroup() {
        val last = groups.lastOrNull()
        groups.add(
            GroupDraft(
                id = UUID.randomUUID().toString(),
                day = last?.day ?: 1,
                startSection = last?.startSection ?: 1,
                endSection = last?.endSection ?: last?.startSection ?: 1,
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
            trimmedName.isEmpty() -> toast("请填写课程名称")
            groups.any { it.weeks.isEmpty() } -> toast("请选择上课周次")
            groups.any { it.isCustomTime && (it.customStartTime.isNullOrBlank() || it.customEndTime.isNullOrBlank()) } ->
                toast("请选择上课时间")
            credit.isNotBlank() && credit.toFloatOrNull() == null -> toast("学分格式不正确")
            else -> {
                val id = tableId
                if (id == null) {
                    toast("课表未加载")
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
                                    group.toCourse(id, trimmedName, colorIndex, colorArgb, creditValue) to group.weeks.toList()
                                },
                        )
                        savedIds = keepIds
                        baseline = snapshot
                        toast("已保存")
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
                onBack = ::requestBack,
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .captureForBlur(blurBackdrop)
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        top = paddingValues.calculateTopPadding() + 4.dp,
                        end = 12.dp,
                        bottom = 120.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item(key = "basic") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        InfoRow(icon = MiuixIcons.File, label = "课程名称", top = 17.dp, bottom = 14.dp) {
                            InlineField(value = name, onValueChange = { name = it }, hint = "必填")
                        }
                        ThinDivider()
                        InfoRow(icon = MiuixIcons.Create, label = "学分", top = 14.dp, bottom = 17.dp) {
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
                        isDark = isDark,
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButton(
                    onClick = ::addGroup,
                    containerColor = MiuixTheme.colorScheme.secondaryVariant,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = "添加时段",
                        tint = MiuixTheme.colorScheme.onSecondaryVariant,
                    )
                }
                FloatingActionButton(onClick = onSave) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "保存",
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
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

    if (discardDialog) {
        ConfirmDialog(
            title = "放弃修改",
            message = "本次编辑尚未保存，返回后修改将丢失。",
            appBlur = appBlur,
            destructive = true,
            confirmText = "放弃",
            onDismiss = { discardDialog = false },
            onConfirm = {
                discardDialog = false
                onBack()
            },
        )
    }
}

@Composable
private fun GroupCard(
    group: GroupDraft,
    canDelete: Boolean,
    isDark: Boolean,
    onChange: (GroupDraft) -> Unit,
    onPickDay: () -> Unit,
    onPickTime: () -> Unit,
    onPickWeeks: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoRow(icon = MiuixIcons.Location, label = "地点", top = 17.dp, bottom = 14.dp) {
                    InlineField(
                        value = group.position,
                        onValueChange = { onChange(group.copy(position = it)) },
                        hint = "选填",
                    )
                }
                InfoRow(icon = MiuixIcons.Contacts, label = "教师", top = 14.dp, bottom = 17.dp) {
                    InlineField(
                        value = group.teacher,
                        onValueChange = { onChange(group.copy(teacher = it)) },
                        hint = "选填",
                    )
                }
                ThinDivider()
                InfoRow(icon = MiuixIcons.Years, label = "上课周次", top = 14.dp, bottom = 17.dp, onClick = onPickWeeks) {
                    if (group.weeks.isEmpty()) {
                        ValueText("必填", placeholder = true)
                    } else {
                        ValueText(formatWeeks(group.weeks.toList()))
                    }
                }
                InfoRow(icon = MiuixIcons.Weeks, label = "上课星期", top = 14.dp, bottom = 17.dp, onClick = onPickDay) {
                    ValueText(dayLabel(group.day))
                }
                InfoRow(icon = MiuixIcons.WorldClock, label = "上课时间", top = 14.dp, bottom = 17.dp, onClick = onPickTime) {
                    val text = timeText(group)
                    if (text.isEmpty()) ValueText("必填", placeholder = true) else ValueText(text)
                }
                ThinDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldIcon(MiuixIcons.Notes)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "备注",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${group.remark.length} / 300",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                RemarkField(
                    value = group.remark,
                    onValueChange = { onChange(group.copy(remark = it.take(300))) },
                    modifier = Modifier.fillMaxWidth().padding(start = 44.dp, end = 16.dp, top = 8.dp, bottom = 17.dp),
                )
            }
        }
        if (canDelete) {
            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = onDelete,
                colors =
                    if (isDark) {
                        ButtonDefaults.buttonColors(color = Color(0xFF2A2A2A))
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = DeleteRed,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "删除", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = DeleteRed)
            }
        }
    }
}

/** 一行 = 左侧图标 + 标签 + 右侧内容（内容区整体右对齐）；给了 [onClick] 时整行可点 */
@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    top: Dp,
    bottom: Dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldIcon(icon)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}

/** 字段左侧的小图标 */
@Composable
private fun FieldIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MiuixTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ValueText(text: String, placeholder: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        color =
            if (placeholder) {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            } else {
                MiuixTheme.colorScheme.onSurface
            },
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 行内输入框：无底框，右对齐；空值显示灰色提示，有值用正文色 */
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle =
            TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            ),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) {
                    ValueText(hint, placeholder = true)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun RemarkField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = TextStyle(fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface),
        minLines = 2,
        maxLines = 4,
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = "选填",
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun ThinDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f)),
    )
}

@Composable
private fun ColorCard(
    colorInt: Int?,
    colorArgb: Int?,
    isDark: Boolean,
    onPick: (index: Int, argb: Int?) -> Unit,
    onPickCustom: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(top = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldIcon(MiuixIcons.Background)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "课程颜色",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        val columns = 6
        val totalItems = CoursePalette.size + 1
        val rowsCount = (totalItems + columns - 1) / columns
        for (row in 0 until rowsCount) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < CoursePalette.size) {
                        ColorCell(
                            color = CoursePalette[index],
                            selected = colorArgb == null && colorInt == index,
                            isDark = isDark,
                            onClick = { onPick(index, null) },
                            modifier = Modifier.weight(1f),
                        )
                    } else if (index == CoursePalette.size) {
                        ColorCell(
                            color = if (colorArgb != null) Color(colorArgb) else null,
                            selected = colorArgb != null,
                            isDark = isDark,
                            onClick = onPickCustom,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 颜色格：选中时外圈主题色描边，内部填淡化的课程色 */
@Composable
private fun ColorCell(
    color: Color?,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderAlpha = if (selected) 1f else 0f
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .squircleBorder(
                        width = 2.dp,
                        color = MiuixTheme.colorScheme.primary.copy(alpha = borderAlpha),
                        cornerRadius = 12.dp,
                    )
                    .padding(4.dp)
                    .squircleClip(8.dp)
                    .background(
                        when {
                            color != null -> color.copy(alpha = if (isDark) 0.22f else 0.16f)
                            isDark -> Color(0xFF363636)
                            else -> MiuixTheme.colorScheme.surface
                        }
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "自定义颜色",
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 冲突判断用的时段 */
private data class Slot(
    val day: Int,
    val startSection: Int?,
    val endSection: Int?,
    val isCustomTime: Boolean,
    val startTime: String?,
    val endTime: String?,
    val weeks: Set<Int>,
)

private fun GroupDraft.toSlot() =
    Slot(day, startSection, endSection, isCustomTime, customStartTime, customEndTime, weeks)

private fun Course.toSlot(weeks: Set<Int>) =
    Slot(day, startSection, endSection, isCustomTime, customStartTime, customEndTime, weeks)

/** "HH:mm" 定长，字符串比较等价于时间比较 */
private fun Slot.timeRange(sectionTimes: List<Pair<String, String>>): Pair<String, String>? =
    if (isCustomTime) {
        val from = startTime?.takeIf { it.isNotBlank() }
        val to = endTime?.takeIf { it.isNotBlank() }
        if (from == null || to == null) null else from to to
    } else {
        val start = startSection ?: return null
        val end = endSection ?: start
        val from = sectionTimes.getOrNull(start - 1)?.first
        val to = sectionTimes.getOrNull(end - 1)?.second
        if (from == null || to == null) null else from to to
    }

private fun Slot.overlaps(other: Slot, sectionTimes: List<Pair<String, String>>): Boolean {
    if (day != other.day) return false
    if (!isCustomTime && !other.isCustomTime) {
        val a1 = startSection ?: return false
        val a2 = endSection ?: a1
        val b1 = other.startSection ?: return false
        val b2 = other.endSection ?: b1
        return a1 <= b2 && b1 <= a2
    }
    val a = timeRange(sectionTimes) ?: return false
    val b = other.timeRange(sectionTimes) ?: return false
    return a.first < b.second && b.first < a.second
}

private fun GroupDraft.toCourse(
    tableId: String,
    name: String,
    colorInt: Int,
    colorArgb: Int?,
    credit: Float?,
): Course =
    Course(
        id = id,
        courseTableId = tableId,
        name = name,
        teacher = teacher,
        position = position,
        day = day,
        startSection = if (isCustomTime) null else startSection,
        endSection = if (isCustomTime) null else endSection,
        isCustomTime = isCustomTime,
        customStartTime = if (isCustomTime) customStartTime else null,
        customEndTime = if (isCustomTime) customEndTime else null,
        colorInt = colorInt,
        colorArgb = colorArgb,
        remark = remark.takeIf { it.isNotBlank() },
        credit = credit,
    )

private fun timeText(group: GroupDraft): String =
    if (group.isCustomTime) {
        val from = group.customStartTime.orEmpty()
        val to = group.customEndTime.orEmpty()
        if (from.isBlank() || to.isBlank()) "" else "$from - $to"
    } else {
        val start = group.startSection
        val end = group.endSection
        if (start == end) "第${start}节" else "第${start}-${end}节"
    }

/** 学分只保留数字与一个小数点，最多一位小数 */
private fun sanitizeCredit(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    if (dot < 0) return filtered.take(3)
    val intPart = filtered.substring(0, dot).take(2)
    val decimals = filtered.substring(dot + 1).filter { it.isDigit() }.take(1)
    return "$intPart.$decimals"
}

private fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
