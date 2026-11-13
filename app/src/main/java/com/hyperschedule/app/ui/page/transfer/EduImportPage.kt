package com.hyperschedule.app.ui.page.transfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.importer.edu.Adapter
import com.hyperschedule.app.data.importer.edu.EduRepo
import com.hyperschedule.app.data.importer.edu.EduRepoStore
import com.hyperschedule.app.data.importer.edu.GLOBAL_TOOL_SCHOOL_ID
import com.hyperschedule.app.data.importer.edu.School as EduSchool
import com.hyperschedule.app.data.importer.edu.SchoolRepository
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.ui.component.BlurredTopAppBar
import com.hyperschedule.app.ui.component.EduRepoSheet
import com.hyperschedule.app.ui.component.bouncyScroll
import com.hyperschedule.app.ui.component.toast
import com.hyperschedule.app.ui.effect.rememberAppBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 分享码入口已覆盖这两个通用工具 */
internal val ShareToolIds = setOf("WakeUp", "StarLink")

/** 三类学校，与适配器分类一一对应 */
private val SchoolTabs =
    listOf(
        "本科/专科" to Adapter.BACHELOR,
        "研究生" to Adapter.POSTGRADUATE,
        "通用工具" to Adapter.TOOL,
    )

private sealed interface SchoolRow {
    data class Header(val letter: String) : SchoolRow

    data class Item(val school: EduSchool) : SchoolRow

    data object Divider : SchoolRow
}

/** 教务系统导入第一步，选择学校，更新入口在右下角 */
@Composable
fun EduImportPage(
    repository: SchoolRepository,
    repoStore: EduRepoStore,
    appBlur: Boolean = false,
    onOpenAdapter: (school: EduSchool, adapter: Adapter, url: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val listState = rememberLazyListState()
    // 点搜索框以外的区域收起键盘
    val dismissKeyboardOnTap =
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.changedToDown() }) {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                }
            }
        }

    // null 表示还在读取索引
    var schools by remember { mutableStateOf<List<EduSchool>?>(null) }
    var updating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    // 抽屉内容跟着学校走，收起动画期间不清空
    var sheetSchool by remember { mutableStateOf<EduSchool?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    var moreVisible by remember { mutableStateOf(false) }
    // 通用工具没有固定网址，进页面前先问一次
    var urlDialog by remember { mutableStateOf<Pair<EduSchool, Adapter>?>(null) }

    // 拉索引并刷新列表；silent 只压掉无变化的提示，换仓库时新增数不作数
    fun sync(silent: Boolean = false, announceAdded: Boolean = true) {
        if (updating) return
        updating = true
        scope.launch {
            val result = runCatching { repository.update() }
            if (result.isSuccess) schools = withContext(Dispatchers.IO) { repository.schools() }
            updating = false
            val text =
                result.fold(
                    onSuccess = {
                        when {
                            it.added > 0 && announceAdded -> "新增${it.added}所学校适配".cjkDigitGap()
                            silent -> null
                            it.changed -> "学校列表已更新"
                            else -> "已是最新版本"
                        }
                    },
                    onFailure = { if (silent) null else "更新失败，${it.message ?: "网络不可用"}" },
                )
            text?.let { context.toast(it) }
        }
    }

    LaunchedEffect(Unit) {
        schools = withContext(Dispatchers.IO) { repository.schools() }
        // 进入页面时的自动同步没有变化就不打扰
        if (repoStore.state.autoSync) sync(silent = true)
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
                    actions = {
                        IconButton(onClick = { moreVisible = true }) {
                            Icon(imageVector = MiuixIcons.More, contentDescription = "更多")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { sync() }) {
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
                    modifier = Modifier.padding(horizontal = 20.dp).then(dismissKeyboardOnTap),
                )

                Box(modifier = Modifier.fillMaxSize().then(dismissKeyboardOnTap)) {
                    when {
                        schools == null -> LoadingHint("正在读取学校列表", showProgress = true)
                        rows.isEmpty() ->
                            LoadingHint(
                                text =
                                    when {
                                        query.isNotBlank() -> "没有匹配的学校"
                                        updating -> "正在同步学校列表"
                                        else -> "暂无学校数据"
                                    },
                                showProgress = query.isBlank() && updating,
                            )
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                                        .bouncyScroll(),
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
                onOpen = { adapter ->
                    sheetVisible = false
                    // 通用教务要先问网址，演示与分享工具集直接进空白页
                    if (adapter.category == Adapter.TOOL && school.id != GLOBAL_TOOL_SCHOOL_ID) {
                        urlDialog = school to adapter
                    } else {
                        onOpenAdapter(school, adapter, "")
                    }
                },
                onDismiss = { sheetVisible = false },
                onDismissFinished = { sheetSchool = null },
            )
        }

        EduRepoSheet(
            show = moreVisible,
            config = repoStore.state,
            onApply = { config ->
                // 留空的自定义仓库不生效，提示一下免得以为没保存
                if (config.repo == EduRepo.Custom && config.customUrl.isBlank()) {
                    context.toast("未填写仓库地址，已保留上次设置")
                }
                // 换了仓库后两边学校对不上，新增数不作数
                val switched = repoStore.update { config }
                sync(announceAdded = !switched)
            },
            onDismiss = { moreVisible = false },
        )

        urlDialog?.let { (school, adapter) ->
            UrlInputDialog(
                appBlur = appBlur,
                onDismiss = { urlDialog = null },
                onConfirm = { url ->
                    urlDialog = null
                    onOpenAdapter(school, adapter, url)
                },
            )
        }
    }
}
