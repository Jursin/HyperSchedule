package top.jursin.hyperschedule.ui.page.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.data.importer.edu.Adapter
import top.jursin.hyperschedule.data.importer.edu.EduRepo
import top.jursin.hyperschedule.data.importer.edu.EduFavoriteStore
import top.jursin.hyperschedule.data.importer.edu.EduRepoStore
import top.jursin.hyperschedule.data.importer.edu.GLOBAL_TOOL_SCHOOL_ID
import top.jursin.hyperschedule.data.importer.edu.School as EduSchool
import top.jursin.hyperschedule.data.importer.edu.SchoolRepository
import top.jursin.hyperschedule.ui.component.BlurredTopAppBar
import top.jursin.hyperschedule.ui.component.GlassFab
import top.jursin.hyperschedule.ui.component.GlassTabs
import top.jursin.hyperschedule.ui.component.HintText
import top.jursin.hyperschedule.ui.component.TopBarIconButton
import top.jursin.hyperschedule.ui.component.bouncyScroll
import top.jursin.hyperschedule.ui.component.toast
import top.jursin.hyperschedule.ui.effect.LocalFabBackdrop
import top.jursin.hyperschedule.ui.effect.captureForBlur
import top.jursin.hyperschedule.ui.effect.rememberAppBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.FavoriteOutline
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Refresh
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

private fun EduSchool.inCategory(category: Int): Boolean =
    adapters.any { it.category == category && (category != Adapter.TOOL || it.id !in ShareToolIds) }

/** 校名、首字母或任一适配器名命中搜索词 */
private fun EduSchool.matchesQuery(text: String): Boolean =
    text.isEmpty() ||
        name.contains(text, true) ||
        initial.contains(text, true) ||
        adapters.any { it.name.contains(text, true) }

private sealed interface SchoolRow {
    data class Header(val letter: String) : SchoolRow

    /** 收藏分区表头 */
    data object Favorite : SchoolRow

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
    val favoriteStore = remember { EduFavoriteStore.get(context) }
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
    var searchExpanded by remember { mutableStateOf(false) }
    // 抽屉内容跟着学校走，收起动画期间不清空
    var sheetSchool by remember { mutableStateOf<EduSchool?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    var moreVisible by remember { mutableStateOf(false) }
    // 通用工具没有固定网址，进页面前先问一次
    var urlDialog by remember { mutableStateOf<Pair<EduSchool, Adapter>?>(null) }

    // 拉索引并刷新列表
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
                            silent -> null
                            it.added > 0 && announceAdded -> "新增${it.added}所学校适配"
                            it.changed -> "学校列表已更新"
                            else -> null
                        }
                    },
                    onFailure = { if (silent) null else "同步失败：${it.message ?: "网络不可用"}" },
                )
            text?.let { context.toast(it) }
        }
    }

    LaunchedEffect(Unit) {
        schools = withContext(Dispatchers.IO) { repository.schools() }
        // 进入页面时的自动同步没有变化就不打扰
        if (repoStore.state.autoSync) sync(silent = true)
    }

    val category = SchoolTabs[tab].second

    val shown =
        remember(schools, category, query) {
            val text = query.trim()
            schools.orEmpty().filter { it.inCategory(category) && it.matchesQuery(text) }
        }

    val favoriteSchools =
        remember(schools, category, query, favoriteStore.ids) {
            val text = query.trim()
            schools.orEmpty()
                .filter { it.id in favoriteStore.ids && it.inCategory(category) && it.matchesQuery(text) }
                .sortedBy { it.initial.uppercase() + it.name }
        }

    val rows =
        remember(shown, favoriteSchools) {
            buildList {
                if (favoriteSchools.isNotEmpty()) {
                    add(SchoolRow.Favorite)
                    favoriteSchools.forEach { add(SchoolRow.Item(it)) }
                }
                shown.groupBy { it.initial.uppercase().ifBlank { "#" } }.forEach { (letter, group) ->
                    if (isNotEmpty()) add(SchoolRow.Divider)
                    add(SchoolRow.Header(letter))
                    group.forEach { add(SchoolRow.Item(it)) }
                }
            }
        }

    val favoriteRowCount = if (favoriteSchools.isEmpty()) 0 else favoriteSchools.size + 1

    // 搜索态跨分类匹配
    val searchHits =
        remember(schools, query) {
            val text = query.trim()
            if (text.isEmpty()) {
                emptyList()
            } else {
                schools.orEmpty().filter { it.matchesQuery(text) }
            }
        }

    val letterIndex =
        remember(rows) {
            rows.withIndex()
                .mapNotNull { (index, row) ->
                    (row as? SchoolRow.Header)?.letter?.takeIf { it in IndexLetters }?.let { it to index }
                }.toMap()
        }

    // 当前字母跟列表走，拖动时由索引条自己接管
    val scrollLetter by remember(rows) {
        derivedStateOf {
            val end = (listState.firstVisibleItemIndex + 1).coerceAtMost(rows.size)
            rows.subList(0, end)
                .lastOrNull { it is SchoolRow.Header && it.letter in IndexLetters }
                ?.let { (it as SchoolRow.Header).letter }
        }
    }

    val inFavorites by remember(favoriteRowCount) {
        derivedStateOf { favoriteRowCount > 0 && listState.firstVisibleItemIndex < favoriteRowCount }
    }

    LaunchedEffect(tab, query) {
        if (rows.isNotEmpty()) {
            listState.scrollToItem(0)
            scrollBehavior.state.contentOffset = 0f
        }
    }

    fun jumpTo(letter: String) {
        val index = letterIndex[letter] ?: return
        scope.launch {
            listState.scrollToItem(index)
            scrollBehavior.state.contentOffset = -2f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BlurredTopAppBar(
                    title = "选择学校",
                    blurBackdrop = blurBackdrop,
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                    pinnedTitle = true,
                    actions = {
                        TopBarIconButton(onClick = { moreVisible = true }) {
                            Icon(imageVector = MiuixIcons.Glass.More, contentDescription = "更多")
                        }
                    },
                )
            },
            floatingActionButton = {
                CompositionLocalProvider(LocalFabBackdrop provides blurBackdrop) {
                    TooltipBox(text = "更新学校列表") {
                        GlassFab(onClick = { sync() }) {
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
                                    imageVector = MiuixIcons.Glass.Refresh,
                                    contentDescription = "更新学校列表",
                                    tint = MiuixTheme.colorScheme.onPrimary,
                                )
                            }
                        }
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
                        )
                        .captureForBlur(blurBackdrop),
            ) {
                SearchBar(
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = {
                                searchExpanded = false
                                focusManager.clearFocus()
                            },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = "搜索学校或教务系统",
                        )
                    },
                    outsideEndAction = {
                        IconButton(
                            onClick = {
                                searchExpanded = false
                                query = ""
                                focusManager.clearFocus()
                            },
                            modifier =
                                Modifier
                                    .padding(start = 8.dp)
                                    .size(44.dp)
                                    .background(MiuixTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Glass.Close,
                                contentDescription = "取消",
                                modifier = Modifier.size(28.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    insideMargin = SearchBarDefaults.InsideMargin.copy(width = 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    when {
                        schools == null ->
                            HintText(
                                text = "正在读取学校列表",
                                style = MiuixTheme.textStyles.body1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            )
                        searchHits.isEmpty() ->
                            HintText(
                                text = if (query.isBlank()) "输入关键词搜索学校或教务系统" else "没有匹配的学校或教务系统",
                                style = MiuixTheme.textStyles.body1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            )
                        else ->
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                                items(searchHits.size) { index ->
                                    val school = searchHits[index]
                                    SchoolItem(
                                        school = school,
                                        onClick = {
                                            searchExpanded = false
                                            focusManager.clearFocus()
                                            sheetSchool = school
                                            sheetVisible = true
                                        },
                                    )
                                }
                            }
                    }
                }

                if (!searchExpanded) {
                    GlassTabs(
                        tabs = SchoolTabs.map { it.first },
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.padding(horizontal = 20.dp).then(dismissKeyboardOnTap),
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(dismissKeyboardOnTap),
                    ) {
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
                                            .bouncyScroll(scrollBehavior),
                                    contentPadding = PaddingValues(bottom = 96.dp),
                                ) {
                                    items(rows.size) { index ->
                                        when (val row = rows[index]) {
                                            is SchoolRow.Header ->
                                                Text(
                                                    text = row.letter,
                                                    style = MiuixTheme.textStyles.body2,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                    modifier =
                                                        Modifier.padding(start = 28.dp, top = 14.dp, bottom = 2.dp),
                                                )

                                            SchoolRow.Favorite ->
                                                Icon(
                                                    imageVector = MiuixIcons.Glass.FavoriteOutline,
                                                    contentDescription = "收藏的学校",
                                                    modifier =
                                                        Modifier
                                                            .padding(start = 28.dp, top = 14.dp, bottom = 2.dp)
                                                            .size(16.dp),
                                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
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
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(horizontal = 28.dp),
                                                        thickness = 0.5.dp,
                                                        color = MiuixTheme.colorScheme.outline,
                                                    )
                                                }
                                        }
                                    }
                                }

                                AlphabetBar(
                                    available = letterIndex.keys,
                                    active = if (inFavorites) null else scrollLetter,
                                    onJump = ::jumpTo,
                                    showFavorite = favoriteRowCount > 0,
                                    favoriteActive = inFavorites,
                                    onJumpFavorite = {
                                        scope.launch {
                                            listState.scrollToItem(0)
                                            scrollBehavior.state.contentOffset = 0f
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(top = 4.dp, bottom = 64.dp)
                                            .width(AlphabetBarWidth)
                                            .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }

        sheetSchool?.let { school ->
            AdapterSheet(
                school = school,
                show = sheetVisible,
                favorite = school.id in favoriteStore.ids,
                onToggleFavorite = { favoriteStore.toggle(school.id) },
                onOpen = { adapter ->
                    sheetVisible = false
                    sheetSchool = null
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
