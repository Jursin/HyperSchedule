package com.miuix.hyperschedule.ui.page

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.importer.edu.Adapter
import com.miuix.hyperschedule.data.importer.edu.School
import com.miuix.hyperschedule.data.importer.edu.SchoolRepository
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.SheetMaxHeight
import com.miuix.hyperschedule.theme.sheetCardColor
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.Person
import com.miuix.hyperschedule.ui.component.School as SchoolIcon
import com.miuix.hyperschedule.ui.component.SheetScaffold
import com.miuix.hyperschedule.ui.component.SheetScrollColumn
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 首字母索引条的字母表 */
private val IndexLetters = ('A'..'Z').map { it.toString() }

/** 分享码入口已覆盖这两个通用工具 */
private val ShareToolIds = setOf("WakeUp", "StarLink")

/** 三类学校，与适配器分类一一对应 */
private val SchoolTabs =
    listOf(
        "本科/专科" to Adapter.BACHELOR,
        "研究生" to Adapter.POSTGRADUATE,
        "通用工具" to Adapter.TOOL,
    )

/** 一行的类型 */
private sealed interface SchoolRow {
    data class Header(val letter: String) : SchoolRow

    data class Item(val school: School) : SchoolRow

    data object Divider : SchoolRow
}

/** 教务系统导入第一步，选择学校；更新入口在右下角 */
@Composable
fun EduImportPage(
    repository: SchoolRepository,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val listState = rememberLazyListState()

    // null 表示还在读取索引
    var schools by remember { mutableStateOf<List<School>?>(null) }
    var updating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    // 抽屉内容跟着学校走，收起动画期间不清空
    var sheetSchool by remember { mutableStateOf<School?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        schools = withContext(Dispatchers.IO) { repository.schools() }
    }

    fun update() {
        if (updating) return
        updating = true
        scope.launch {
            val result = runCatching { repository.update() }
            if (result.isSuccess) schools = withContext(Dispatchers.IO) { repository.schools() }
            updating = false
            val text =
                result.fold(
                    onSuccess = { if (it) "学校列表已更新" else "已是最新版本" },
                    onFailure = { "更新失败，${it.message ?: "网络不可用"}" },
                )
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    val shown =
        remember(schools, tab, query) {
            val text = query.trim()
            val category = SchoolTabs[tab].second
            schools.orEmpty().filter { school ->
                val byTab =
                    school.adapters.any {
                        it.category == category && (category != Adapter.TOOL || it.id !in ShareToolIds)
                    }
                val byQuery =
                    text.isEmpty() ||
                        school.name.contains(text, true) ||
                        school.initial.contains(text, true) ||
                        school.adapters.any { it.name.contains(text, true) }
                byTab && byQuery
            }
        }

    val rows =
        remember(shown) {
            buildList {
                shown.groupBy { it.initial.uppercase().ifBlank { "#" } }.forEach { (letter, group) ->
                    if (isNotEmpty()) add(SchoolRow.Divider)
                    add(SchoolRow.Header(letter))
                    group.forEach { add(SchoolRow.Item(it)) }
                }
            }
        }

    val letterIndex =
        remember(rows) {
            rows.withIndex()
                .mapNotNull { (index, row) -> (row as? SchoolRow.Header)?.let { it.letter to index } }
                .toMap()
        }

    // 当前字母跟列表走，拖动时由索引条自己接管
    val scrollLetter by remember(rows) {
        derivedStateOf {
            val end = (listState.firstVisibleItemIndex + 1).coerceAtMost(rows.size)
            rows.subList(0, end).lastOrNull { it is SchoolRow.Header }?.let { (it as SchoolRow.Header).letter }
        }
    }

    LaunchedEffect(tab, query) {
        if (rows.isNotEmpty()) listState.scrollToItem(0)
    }

    fun jumpTo(
        letter: String,
        animate: Boolean,
    ) {
        val index = letterIndex[letter] ?: return
        scope.launch { if (animate) listState.animateScrollToItem(index) else listState.scrollToItem(index) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BlurredTopAppBar(
                    title = "选择学校",
                    blurBackdrop = blurBackdrop,
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { update() }) {
                    if (updating) {
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
                            contentDescription = "更新学校列表",
                            tint = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding(),
                        ),
            ) {
                InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    label = "搜索学校或教务系统",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )

                TabRow(
                    tabs = SchoolTabs.map { it.first },
                    selectedTabIndex = tab,
                    onTabSelected = { tab = it },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        schools == null -> LoadingHint("正在读取学校列表", showProgress = true)
                        rows.isEmpty() -> LoadingHint(if (query.isBlank()) "暂无学校数据" else "没有匹配的学校")
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                                        .overScrollVertical()
                                        .scrollEndHaptic(),
                                contentPadding = PaddingValues(bottom = 96.dp),
                            ) {
                                items(rows.size) { index ->
                                    when (val row = rows[index]) {
                                        is SchoolRow.Header ->
                                            Text(
                                                text = row.letter,
                                                style = MiuixTheme.textStyles.body2,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                modifier = Modifier.padding(start = 26.dp, top = 16.dp, bottom = 4.dp),
                                            )

                                        is SchoolRow.Item ->
                                            SchoolItem(
                                                school = row.school,
                                                onClick = {
                                                    sheetSchool = row.school
                                                    sheetVisible = true
                                                },
                                            )

                                        SchoolRow.Divider ->
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
                                                thickness = 0.5.dp,
                                                color = MiuixTheme.colorScheme.outline,
                                            )
                                    }
                                }
                            }

                            AlphabetBar(
                                available = letterIndex.keys,
                                active = scrollLetter,
                                onJump = ::jumpTo,
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 2.dp, top = 12.dp, bottom = 88.dp)
                                        .width(20.dp)
                                        .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }

        sheetSchool?.let { school ->
            AdapterSheet(
                school = school,
                show = sheetVisible,
                onDismiss = { sheetVisible = false },
                onDismissFinished = { sheetSchool = null },
            )
        }
    }
}

/** 适配器抽屉，条目暂不接后续动作 */
@Composable
private fun AdapterSheet(
    school: School,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val adapters = school.adapters.filter { it.id !in ShareToolIds }

    SheetScaffold(
        title = school.name,
        titleIcon = SchoolIcon,
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        if (adapters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "该学校暂无可用方案",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        } else {
            SheetScrollColumn(maxHeight = SheetMaxHeight) {
                adapters.forEach { AdapterCard(it) }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** 适配器卡片，名称、分类、描述与贡献者 */
@Composable
private fun AdapterCard(adapter: Adapter) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        insideMargin = PaddingValues(0.dp),
        pressFeedbackType = PressFeedbackType.None,
        colors =
            CardDefaults.defaultColors(
                color = sheetCardColor(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = adapter.name,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = adapter.categoryLabel(),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = adapter.description.ifBlank { "暂无描述" },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            if (adapter.maintainer.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "贡献者：${adapter.maintainer}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

private fun Adapter.categoryLabel(): String =
    when (category) {
        Adapter.BACHELOR -> "本科"
        Adapter.POSTGRADUATE -> "研究生"
        Adapter.TOOL -> "通用工具"
        else -> "其他"
    }

/** 一行学校 */
@Composable
private fun SchoolItem(
    school: School,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Text(
        text = school.name,
        style = MiuixTheme.textStyles.body1,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                }
                .padding(horizontal = 26.dp, vertical = 20.dp),
    )
}

/** 右侧 A–Z 索引条，可点可拖 */
@Composable
private fun AlphabetBar(
    available: Set<String>,
    active: String?,
    onJump: (letter: String, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<String?>(null) }

    fun pick(
        y: Float,
        height: Int,
        drag: Boolean,
    ) {
        if (height <= 0) return
        val letter = IndexLetters[((y / height) * IndexLetters.size).toInt().coerceIn(0, IndexLetters.lastIndex)]
        if (letter !in available || (drag && letter == dragging)) return
        if (drag) dragging = letter
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onJump(letter, !drag)
    }

    Column(
        modifier =
            modifier.pointerInput(available) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y, size.height, drag = true) },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    change.consume()
                    pick(change.position.y, size.height, drag = true)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IndexLetters.forEach { letter ->
            val enabled = letter in available
            val selected = enabled && letter == (dragging ?: active)
            Text(
                text = letter,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color =
                    when {
                        selected -> MiuixTheme.colorScheme.primary
                        enabled -> MiuixTheme.colorScheme.onSurfaceVariantActions
                        else -> MiuixTheme.colorScheme.outline
                    },
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (enabled) {
                                Modifier.clickable(interactionSource = null, indication = null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onJump(letter, false)
                                }
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}

/** 居中的提示，用于加载中与空列表 */
@Composable
private fun LoadingHint(
    text: String,
    showProgress: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
