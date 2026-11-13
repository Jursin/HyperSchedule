package com.hyperschedule.app.ui.navigation

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.hyperschedule.app.theme.WideLayoutMinWidth
import com.hyperschedule.app.theme.useWindowedPages
import com.hyperschedule.app.ui.component.BackToNowButton
import com.hyperschedule.app.ui.component.BackToNowHostInset
import com.hyperschedule.app.ui.component.BackToNowHostState
import com.hyperschedule.app.ui.component.LocalBackToNowHost
import com.hyperschedule.app.ui.component.LocalTabPageIndex
import com.hyperschedule.app.ui.component.LocalWindowDismissState
import com.hyperschedule.app.ui.component.WindowDismissState
import com.hyperschedule.app.ui.effect.LocalWallpaper
import com.hyperschedule.app.ui.effect.WallpaperBackground
import com.hyperschedule.app.ui.effect.captureForBlur
import com.hyperschedule.app.ui.effect.rememberBarBackdrop
import com.hyperschedule.app.ui.effect.rememberWallpaperState
import com.hyperschedule.app.ui.page.settings.LicensePage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 宽屏外层纵向 Pager 只负责切页，纵向余量到此截断，滚动全部交给内层 */
private val VerticalScrollShield =
    object : NestedScrollConnection {
        // 不抢任何位移，内层滚动照常
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero

        // 内层滚到头的那部分在这里收住，不再往 pager 递
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = Offset(0f, available.y)
    }

@Composable
fun MainScaffold(
    appBlur: Boolean,
    predictiveBackEnabled: Boolean,
    initialPage: Int = 0,
    wallpaper: String = "",
    wallpaperBlur: Int = 0,
    onWallpaperSaved: (String) -> Unit = {},
    wallpaperCropPage: @Composable (uri: String, onSaved: (String) -> Unit, onBack: () -> Unit) -> Unit =
        { _, _, _ -> },
    settingsPage: @Composable (navigate: (Page) -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    themePage: @Composable (onBack: () -> Unit) -> Unit = {},
    updatePage: @Composable (onOpenLog: () -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    updateLogPage: @Composable (onBack: () -> Unit) -> Unit = {},
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
    scheduleTransferPage: @Composable (navigate: (Page) -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    eduImportPage: @Composable (navigate: (Page) -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    eduImportWebPage:
        @Composable
        (
            schoolId: String,
            adapterId: String,
            url: String,
            onBack: () -> Unit,
            onImported: () -> Unit,
        ) -> Unit = { _, _, _, _, _ -> },
    backupPage: @Composable (onBack: () -> Unit) -> Unit = {},
    holidayPage: @Composable (onBack: () -> Unit) -> Unit = {},
    reminderPage: @Composable (onBack: () -> Unit) -> Unit = {},
    wearPushPage: @Composable (onBack: () -> Unit) -> Unit = {},
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
    // 「今」按钮跨页共享，切 tab 时按钮不随页面滑走，只就地收起/展开
    val backToNowHost = remember { BackToNowHostState() }
    val overlayHost = remember { OverlayHostState() }
    // 浮窗整块收起的共享状态，进度由这里驱动，各浮窗每帧读取
    val windowDismissState = remember { WindowDismissState() }

    // 正在进/出的页面层级，0 首页、1 一级子页面、2 及以上浮窗里的浮窗
    val navLevel = remember { mutableIntStateOf(1) }
    val windowTransition = remember(navLevel) { windowPageTransition(navLevel) }

    val onBack: () -> Unit =
        remember(backStack) {
            {
                // 整块收起动画中忽略返回
                if (windowDismissState.active) return@remember
                // 弹出后的层级 = 弹出后的栈深（首页占 0）
                navLevel.intValue = backStack.size - 1
                backStack.removeLastOrNull()
            }
        }

    // 点浮窗外整块收起，所有浮窗读同一个进度一起下滑，走完再一次性清栈
    val dismissToHome: () -> Unit =
        remember(backStack) {
            {
                if (backStack.size > 1 && !windowDismissState.active) {
                    windowDismissState.progress = 0f
                    windowDismissState.active = true
                    coroutineScope.launch {
                        animate(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = WindowSlideMillis, easing = WindowSlideEasing),
                        ) { value, _ -> windowDismissState.progress = value }
                        windowDismissState.progress = 1f
                        // 卡片此时已在屏幕外，退栈动画看不见
                        navLevel.intValue = 1
                        while (backStack.size > 1) backStack.removeLastOrNull()
                        // 代数前进，被收起的浮窗从此待在屏幕外
                        windowDismissState.generation++
                        windowDismissState.active = false
                    }
                }
            }
        }

    val navigate: (Page) -> Unit =
        remember(backStack) {
            { page ->
                if (backStack.lastOrNull() != page) {
                    // 新页的层级 = 压栈前的栈深
                    navLevel.intValue = backStack.size
                    backStack.add(page)
                }
            }
        }

    // 教务导入完成后直接回到「导入与导出」页
    val backToScheduleTransfer: () -> Unit =
        remember(backStack) {
            {
                navLevel.intValue = 2
                while (backStack.size > 1 && backStack.lastOrNull() != Page.ScheduleTransfer) {
                    backStack.removeLastOrNull()
                }
            }
        }

    val interceptPredictiveBack = !predictiveBackEnabled && backStack.size > 1

    // 回到上一层，在非首个 Tab 时先回到首个 Tab
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf { backStack.size == 1 && mainPagerState.selectedPage != 0 }
    }
    val pagerBackEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = pagerBackEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )

    // 用 snapshotFlow 而非 LaunchedEffect(currentPage)，避免滑动时每帧重组
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { mainPagerState.syncPage() }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
    ) {
        val wide = maxWidth >= WideLayoutMinWidth
        // 浮窗页面按短边判断，平板横竖屏都是浮窗，手机始终铺满
        val windowed = useWindowedPages(maxWidth, maxHeight)
        CompositionLocalProvider(
            LocalOverlayHost provides overlayHost,
            // 浮窗整块收起的共享进度，所有浮窗读同一个进度一起下滑
            LocalWindowDismissState provides windowDismissState,
        ) {
            // 首次组合时的页码，固定不变，避免滑动时每帧重组
            val firstPage = remember { pagerState.currentPage }
            val systemCorner = rememberNavSystemCornerRadius()

            NavDisplay(
                backStack = navBackStack,
                onBack = onBack,
                transition = if (windowed) windowTransition else NavTransitions.MiuixDefault,
                effects =
                    NavDisplayEffects(
                        enableCornerClip = true,
                        cornerClipRadius = if (windowed) systemCorner else 32.dp,
                        cornerClipMode = if (windowed) NavCornerClipMode.All else NavCornerClipMode.Leading,
                        // 遮罩由主页那层的 WindowScrim 画
                        dimAmount = 0f,
                        // 背衬跟内容层同色，露出的边角不出现第二块底色
                        backdropColor = MiuixTheme.colorScheme.surface,
                        blockInputDuringTransition = false,
                    ),
            ) {
                entry<Page.Home> {
                    NavEntry(interceptPredictiveBack, onBack, windowed = false) {
                        // 壁纸只铺一层，位置在 Tab pager 之外，切 Tab 时不会跟着滑
                        val wallpaperState = rememberWallpaperState(wallpaper, appBlur)
                        // 底栏采「壁纸 + 页面内容」，滚动穿过去的内容也会被糊进玻璃
                        val barBackdrop =
                            rememberBarBackdrop(appBlur, wallpaperState)
                        // 宽屏改用左侧栏
                        Box(modifier = Modifier.fillMaxSize()) {
                            val pageContent: @Composable (Int) -> Unit = { page ->
                                CompositionLocalProvider(
                                    LocalMainTabNavigating provides mainPagerState.isNavigating,
                                    LocalPageIsCurrent provides (page == mainPagerState.selectedPage),
                                    LocalPageReselect provides mainPagerState.reselectTick,
                                    LocalTabPageIndex provides page,
                                    LocalBackToNowHost provides backToNowHost,
                                ) {
                                    content(page, navigate)
                                }
                            }
                            CompositionLocalProvider(
                                LocalWallpaper provides wallpaperState,
                            ) {
                                // 壁纸铺满整屏，宽屏侧栏也压在它上面
                                WallpaperBackground(
                                    state = wallpaperState,
                                    blurRadius = wallpaperBlur,
                                )
                                Row(modifier = Modifier.fillMaxSize()) {
                                    if (wide) {
                                        SideNavBar(
                                            selectedPage = mainPagerState.targetPage.value,
                                            onSelect = { mainPagerState.animateToPage(it) },
                                            onOpenSettings = { navigate(Page.Settings) },
                                        )
                                    }
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    // 页面内容录进底栏的采样层，壁纸由那层自己垫底
                                                    .captureForBlur(barBackdrop),
                                        ) {
                                            // 宽屏上下、窄屏左右切换，切页只走导航，否则顶栏拖动会被外层 pager 抢走
                                            if (wide) {
                                                VerticalPager(
                                                    state = pagerState,
                                                    userScrollEnabled = false,
                                                    beyondViewportPageCount = 1,
                                                    modifier = Modifier.fillMaxSize(),
                                                ) { page ->
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .fillMaxSize()
                                                                .nestedScroll(VerticalScrollShield),
                                                    ) { pageContent(page) }
                                                }
                                            } else {
                                                HorizontalPager(
                                                    state = pagerState,
                                                    userScrollEnabled = false,
                                                    beyondViewportPageCount = 1,
                                                    modifier = Modifier.fillMaxSize(),
                                                ) { page -> pageContent(page) }
                                            }
                                        }
                                        // 「今」按钮与底栏同层，切页时先收起，避免「先展开再收起」的闪动
                                        val backToNowSlot = backToNowHost.slotOf(mainPagerState.selectedPage)
                                        BackToNowButton(
                                            visible = !mainPagerState.isNavigating && backToNowSlot?.visible?.value == true,
                                            onClick = { backToNowSlot?.onClick?.invoke() },
                                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BackToNowHostInset),
                                            blurBackdrop = barBackdrop,
                                        )
                                        if (!wide) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(bottom = NavBarMetrics.floatingBottomMargin),
                                            ) {
                                                BottomNavBar(
                                                    initialPage = firstPage,
                                                    targetPage = mainPagerState.targetPage,
                                                    blurBackdrop = barBackdrop,
                                                    onSelect = { mainPagerState.animateToPage(it) },
                                                    onOpenSettings = { navigate(Page.Settings) },
                                                    modifier = Modifier.fillMaxWidth(0.63f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 遮罩只在主页这层，栈里有浮窗页时压暗主页，浮窗自身始终原色
                            if (windowed) WindowScrim(backStack, windowDismissState)
                            // 浮层（课表详情抽屉等）画在最上层，宽屏时也盖住侧栏
                            overlayHost.content?.invoke()
                        }
                    }
                }
                entry<Page.License> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        LicensePage(
                            appBlur = appBlur,
                            onBack = onBack,
                        )
                    }
                }
                entry<Page.Settings> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        settingsPage(navigate, onBack)
                    }
                }
                entry<Page.Appearance> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        themePage(onBack)
                    }
                }
                entry<Page.Update> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        updatePage({ navigate(Page.UpdateLog) }, onBack)
                    }
                }
                entry<Page.UpdateLog> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        updateLogPage(onBack)
                    }
                }
                entry<Page.About> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        aboutPage(onBack) { navigate(Page.License) }
                    }
                }
                entry<Page.CourseDetail> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        courseDetailPage(key.name, key.week, onBack)
                    }
                }
                entry<Page.CourseManage> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        courseManagePage(navigate, onBack)
                    }
                }
                entry<Page.CourseEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        courseEditPage(key.name, key.day, key.startSection, key.endSection, key.focusId, onBack)
                    }
                }
                entry<Page.TimeTableManage> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        timeTableManagePage(navigate, onBack)
                    }
                }
                entry<Page.CourseTableManage> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        courseTableManagePage(onBack)
                    }
                }
                entry<Page.ScheduleTransfer> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        scheduleTransferPage(navigate, onBack)
                    }
                }
                entry<Page.EduImport> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        eduImportPage(navigate, onBack)
                    }
                }
                entry<Page.EduImportWeb> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        eduImportWebPage(key.schoolId, key.adapterId, key.url, onBack, backToScheduleTransfer)
                    }
                }
                entry<Page.Backup> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        backupPage(onBack)
                    }
                }
                entry<Page.Holiday> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        holidayPage(onBack)
                    }
                }
                entry<Page.Reminder> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        reminderPage(onBack)
                    }
                }
                entry<Page.WearPush> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        wearPushPage(onBack)
                    }
                }
                entry<Page.TimeTableEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        timeTableEditPage(key.planId, onBack)
                    }
                }
                entry<Page.ComboEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        comboEditPage(key.comboId, onBack)
                    }
                }
                entry<Page.WallpaperCrop> { key ->
                    // 裁剪框要按整屏比例算，这一页始终铺满，不套浮窗
                    NavEntry(interceptPredictiveBack, onBack, windowed = false) {
                        wallpaperCropPage(
                            key.uri,
                            { path ->
                                onWallpaperSaved(path)
                                onBack()
                            },
                            onBack,
                        )
                    }
                }
            }
        }
    }
}
