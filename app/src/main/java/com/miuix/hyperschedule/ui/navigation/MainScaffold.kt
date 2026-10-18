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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.miuix.hyperschedule.ui.component.BackToNowButton
import com.miuix.hyperschedule.ui.component.BackToNowHostState
import com.miuix.hyperschedule.ui.component.LocalBackToNowHost
import com.miuix.hyperschedule.ui.component.LocalLastTapDown
import com.miuix.hyperschedule.ui.component.LocalTabPageIndex
import com.miuix.hyperschedule.ui.page.LicensePage
import com.miuix.hyperschedule.ui.theme.captureForBlur
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
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

/** 底部导航切换动画进行中；页面据此临时关掉日期/周 pager 的手势，避免与过渡动画互抢 */
val LocalMainTabNavigating = compositionLocalOf { false }

/** 当前页面槽是不是用户正在看的 Tab（含被设置等二级页盖住的情况）；页面据此实现「切走再回来复位到今日/本周」 */
val LocalPageIsCurrent = compositionLocalOf { true }

@Composable
fun MainScaffold(
    appBlur: Boolean,
    predictiveBackEnabled: Boolean,
    initialPage: Int = 0,
    appearancePage: @Composable (onBack: () -> Unit) -> Unit = {},
    updatePage: @Composable (onBack: () -> Unit) -> Unit = {},
    aboutPage: @Composable (onBack: () -> Unit, onLicenseClick: () -> Unit) -> Unit = { _, _ -> },
    content: @Composable (page: Int, bottomPadding: Dp, navigate: (Page) -> Unit) -> Unit,
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
        CompositionLocalProvider(LocalLastTapDown provides lastTapDown) {
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
                                    LocalPageIsCurrent provides (page == mainPagerState.selectedPage && backStack.size == 1),
                                    LocalTabPageIndex provides page,
                                    LocalBackToNowHost provides backToNowHost,
                                ) {
                                    content(page, 80.dp, navigate)
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
                    NavEntry(interceptPredictiveBack, onBack) { appearancePage(onBack) }
                }
                entry<Page.Update> {
                    NavEntry(interceptPredictiveBack, onBack) { updatePage(onBack) }
                }
                entry<Page.About> {
                    NavEntry(interceptPredictiveBack, onBack) {
                        aboutPage(onBack) { backStack.add(Page.License) }
                    }
                }
            }
        }
    }
}
