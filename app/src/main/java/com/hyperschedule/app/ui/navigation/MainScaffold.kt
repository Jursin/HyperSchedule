package com.hyperschedule.app.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.hyperschedule.app.LocalAppGraph
import com.hyperschedule.app.reminder.ReminderScheduler
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
import com.hyperschedule.app.ui.state.rememberCourseTableData
import com.hyperschedule.app.ui.state.rememberCoursesWithWeeks
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

/** 关于页需要 RuntimeShader，所以整页要求 API 33 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainScaffold(
    appBlur: Boolean,
    predictiveBackEnabled: Boolean,
    initialPage: Int = 0,
    wallpaper: String = "",
    wallpaperBlur: Int = 0,
    onWallpaperSaved: (String) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val store = graph.settings
    val settings = store.state
    // 课表数据常驻订阅，进详情页不必再查一次库
    val table = rememberCourseTableData(graph.schedule, store)
    val allCourses = rememberCoursesWithWeeks(graph.schedule, table?.tableId)
    // 提醒重排的触发点：启动、改提醒设置、换课表、课程增删改
    LaunchedEffect(settings.reminder, settings.currentTableId, allCourses) {
        ReminderScheduler.resync(graph.context)
    }

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

    // 二级页面只需要一个页面标识，其余参数这里补齐
    val secondaryPage: @Composable (Page) -> Unit = { page ->
        SecondaryPage(
            key = page,
            navigate = navigate,
            onBack = onBack,
            backToScheduleTransfer = backToScheduleTransfer,
            onWallpaperSaved = onWallpaperSaved,
            table = table,
            allCourses = allCourses,
        )
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
                                    HomeTabContent(page, navigate, table, allCourses)
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
                        secondaryPage(Page.License)
                    }
                }
                entry<Page.Settings> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.Settings)
                    }
                }
                entry<Page.Appearance> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.Appearance)
                    }
                }
                entry<Page.Update> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.Update)
                    }
                }
                entry<Page.UpdateLog> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.UpdateLog)
                    }
                }
                entry<Page.About> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.About)
                    }
                }
                entry<Page.CourseDetail> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(key)
                    }
                }
                entry<Page.CourseManage> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.CourseManage)
                    }
                }
                entry<Page.CourseEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(key)
                    }
                }
                entry<Page.TimeTableManage> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.TimeTableManage)
                    }
                }
                entry<Page.CourseTableManage> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.CourseTableManage)
                    }
                }
                entry<Page.ScheduleTransfer> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.ScheduleTransfer)
                    }
                }
                entry<Page.EduImport> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.EduImport)
                    }
                }
                entry<Page.EduImportWeb> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(key)
                    }
                }
                entry<Page.Backup> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.Backup)
                    }
                }
                entry<Page.Holiday> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.Holiday)
                    }
                }
                entry<Page.Reminder> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.Reminder)
                    }
                }
                entry<Page.WearPush> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(Page.WearPush)
                    }
                }
                entry<Page.TimeTableEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(key)
                    }
                }
                entry<Page.ComboEdit> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        secondaryPage(key)
                    }
                }
                entry<Page.WallpaperCrop> { key ->
                    // 裁剪框要按整屏比例算，这一页始终铺满，不套浮窗
                    NavEntry(interceptPredictiveBack, onBack, windowed = false) {
                        secondaryPage(key)
                    }
                }
            }
        }
    }
}
