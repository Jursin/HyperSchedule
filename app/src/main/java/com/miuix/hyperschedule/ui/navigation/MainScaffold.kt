package com.miuix.hyperschedule.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.miuix.hyperschedule.ui.component.BackToNowButton
import com.miuix.hyperschedule.ui.component.BackToNowHostState
import com.miuix.hyperschedule.ui.component.LocalBackToNowHost
import com.miuix.hyperschedule.ui.component.LocalTabPageIndex
import com.miuix.hyperschedule.ui.page.LicensePage
import com.miuix.hyperschedule.ui.effect.captureForBlur
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun NavEntry(
    interceptPredictiveBack: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = state,
        isBackEnabled = interceptPredictiveBack,
        onBackCompleted = onBack,
    )
    content()
}

/**
 * 跨页浮层宿主：抽屉这类要盖住底栏的内容挂到这里，
 * 由 MainScaffold 在底栏之后绘制，保证层级在最上面。
 */
class OverlayHostState {
    internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalOverlayHost = compositionLocalOf<OverlayHostState?> { null }

/** 把内容挂到 [LocalOverlayHost]；[content] 为 null 时撤下 */
@Composable
fun ProvideOverlay(content: (@Composable () -> Unit)?) {
    val host = LocalOverlayHost.current ?: return
    val latest = rememberUpdatedState(content)
    DisposableEffect(host, content != null) {
        host.content = if (content != null) ({ latest.value?.invoke() }) else null
        onDispose { if (content != null) host.content = null }
    }
}

/** 底部导航切换动画进行中；页面据此临时关掉日期/周 pager 的手势，避免与过渡动画互抢 */
val LocalMainTabNavigating = compositionLocalOf { false }

/** 当前页面槽是不是用户正在看的 Tab（只按 Tab 判断，二级页盖住不算离开）；页面据此实现「切走再回来复位到今日/本周」 */
val LocalPageIsCurrent = compositionLocalOf { true }

/** 全局最后一次按下的位置，页面据此判断点击是否落在交互区外 */
val LocalLastTapDown = staticCompositionLocalOf<MutableState<Offset?>> { mutableStateOf(null) }

@Composable
fun MainScaffold(
    appBlur: Boolean,
    predictiveBackEnabled: Boolean,
    initialPage: Int = 0,
    themePage: @Composable (onBack: () -> Unit) -> Unit = {},
    updatePage: @Composable (onBack: () -> Unit) -> Unit = {},
    aboutPage: @Composable (onBack: () -> Unit, onLicenseClick: () -> Unit) -> Unit = { _, _ -> },
    courseDetailPage: @Composable (name: String, week: Int, onBack: () -> Unit) -> Unit = { _, _, _ -> },
    courseManagePage: @Composable (navigate: (Page) -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    courseEditPage:
        @Composable (
            name: String,
            day: Int,
            startSection: Int,
            endSection: Int,
            focusId: String,
            onBack: () -> Unit,
        ) -> Unit = { _, _, _, _, _, _ -> },
    timeTableManagePage: @Composable (navigate: (Page) -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    courseTableManagePage: @Composable (onBack: () -> Unit) -> Unit = {},
    scheduleImportPage: @Composable (navigate: (Page) -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    eduImportPage: @Composable (onBack: () -> Unit) -> Unit = {},
    scheduleExportPage: @Composable (onBack: () -> Unit) -> Unit = {},
    backupPage: @Composable (onBack: () -> Unit) -> Unit = {},
    holidayPage: @Composable (onBack: () -> Unit) -> Unit = {},
    reminderPage: @Composable (onBack: () -> Unit) -> Unit = {},
    timeTableEditPage: @Composable (planId: String, onBack: () -> Unit) -> Unit = { _, _ -> },
    comboEditPage: @Composable (comboId: String, onBack: () -> Unit) -> Unit = { _, _ -> },
    content: @Composable (page: Int, navigate: (Page) -> Unit) -> Unit,
) {
    val navBackStack = rememberNavBackStack<Page>(Page.Home)
    val backStack: MutableList<NavKey> = navBackStack
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val mainPagerState = rememberMainPagerState(pagerState, coroutineScope)
    val navBarBackdrop = rememberAppBlurBackdrop(appBlur)
    // 「今」按钮跨页共享：切 tab 时按钮不随页面滑走，只就地收起/展开
    val backToNowHost = remember { BackToNowHostState() }
    val overlayHost = remember { OverlayHostState() }

    // 全局按下位置（见 LocalLastTapDown）
    val lastTapDown = remember { mutableStateOf<Offset?>(null) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val onBack: () -> Unit =
        remember(backStack) {
            { backStack.removeLastOrNull() }
        }

    val navigate: (Page) -> Unit =
        remember(backStack) {
            { page -> if (backStack.lastOrNull() != page) backStack.add(page) }
        }

    val interceptPredictiveBack = !predictiveBackEnabled && backStack.size > 1

    // 回到上一层：在非首个 Tab 时先回到首个 Tab
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf { backStack.size == 1 && mainPagerState.selectedPage != 0 }
    }
    val pagerBackEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = pagerBackEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )

    // 用 snapshotFlow 而非 LaunchedEffect(currentPage)：后者在组合期读，滑动每帧都会重组整棵子树
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { mainPagerState.syncPage() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                // 记录全局按下位置（页面据此判断点击是否落在交互区外）；Initial 阶段不消费
                .onGloballyPositioned { rootCoordinates = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.firstOrNull { it.changedToDown() } ?: continue
                            val coordinates = rootCoordinates ?: continue
                            lastTapDown.value = coordinates.localToRoot(down.position)
                        }
                    }
                },
    ) {
        CompositionLocalProvider(
            LocalLastTapDown provides lastTapDown,
            LocalOverlayHost provides overlayHost,
        ) {
            // 首次组合时的页码，固定不变；不在组合期读 currentPage，否则滑动每帧都会重组
            val firstPage = remember { pagerState.currentPage }

            NavDisplay(
                backStack = navBackStack,
                onBack = onBack,
                transition = NavTransitions.MiuixDefault,
                effects =
                    NavDisplayEffects(
                        enableCornerClip = true,
                        cornerClipRadius = 32.dp,
                        cornerClipMode = NavCornerClipMode.Leading,
                        dimAmount = 0.5f,
                        backdropColor = MiuixTheme.colorScheme.surface,
                        blockInputDuringTransition = false,
                    ),
            ) {
                entry<Page.Home> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                // 关闭用户滑动，切页只走底部导航：顶栏不在内层 pager 覆盖范围内，
                                // 手指在顶栏上横拖会被外层 Tab pager 抢走直接切页
                                userScrollEnabled = false,
                                beyondViewportPageCount = 1,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .captureForBlur(navBarBackdrop),
                            ) { page ->
                                CompositionLocalProvider(
                                    LocalMainTabNavigating provides mainPagerState.isNavigating,
                                    LocalPageIsCurrent provides (page == mainPagerState.selectedPage),
                                    LocalTabPageIndex provides page,
                                    LocalBackToNowHost provides backToNowHost,
                                ) {
                                    content(page, navigate)
                                }
                            }
                            // 「今」按钮：与底栏同层，不在 tab pager 内，切页时原地动画
                            // 切页期间先收起：目标页复位到今日/本周后按钮本就该消失，避免「先展开再收起」的闪动
                            val backToNowSlot = backToNowHost.slotOf(mainPagerState.selectedPage)
                            BackToNowButton(
                                visible = !mainPagerState.isNavigating && backToNowSlot?.visible?.value == true,
                                onClick = { backToNowSlot?.onClick?.invoke() },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                                blurBackdrop = navBarBackdrop,
                            )
                            // 宽度是本项目的选择（3 个 tab）；只贴底 24dp
                            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                                BottomNavBar(
                                    initialPage = firstPage,
                                    targetPage = mainPagerState.targetPage,
                                    blurBackdrop = navBarBackdrop,
                                    onSelect = { mainPagerState.animateToPage(it) },
                                    modifier = Modifier.fillMaxWidth(0.63f),
                                )
                            }
                            // 浮层（课表详情抽屉等）画在底栏之后，层级最高
                            overlayHost.content?.invoke()
                        }
                    }
                }
                entry<Page.License> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        LicensePage(
                            appBlur = appBlur,
                            onBack = onBack,
                        )
                    }
                }
                entry<Page.Appearance> {
                    NavEntry(interceptPredictiveBack, onBack) { themePage(onBack) }
                }
                entry<Page.Update> {
                    NavEntry(interceptPredictiveBack, onBack) { updatePage(onBack) }
                }
                entry<Page.About> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        aboutPage(onBack) { backStack.add(Page.License) }
                    }
                }
                entry<Page.CourseDetail> { key ->
                    NavEntry(interceptPredictiveBack, onBack) {
                        courseDetailPage(key.name, key.week, onBack)
                    }
                }
                entry<Page.CourseManage> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        courseManagePage(navigate, onBack)
                    }
                }
                entry<Page.CourseEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack) {
                        courseEditPage(key.name, key.day, key.startSection, key.endSection, key.focusId, onBack)
                    }
                }
                entry<Page.TimeTableManage> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        timeTableManagePage(navigate, onBack)
                    }
                }
                entry<Page.CourseTableManage> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        courseTableManagePage(onBack)
                    }
                }
                entry<Page.ScheduleImport> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        scheduleImportPage(navigate, onBack)
                    }
                }
                entry<Page.EduImport> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        eduImportPage(onBack)
                    }
                }
                entry<Page.ScheduleExport> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        scheduleExportPage(onBack)
                    }
                }
                entry<Page.Backup> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        backupPage(onBack)
                    }
                }
                entry<Page.Holiday> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        holidayPage(onBack)
                    }
                }
                entry<Page.Reminder> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        reminderPage(onBack)
                    }
                }
                entry<Page.TimeTableEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack) {
                        timeTableEditPage(key.planId, onBack)
                    }
                }
                entry<Page.ComboEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack) {
                        comboEditPage(key.comboId, onBack)
                    }
                }
            }
        }
    }
}
