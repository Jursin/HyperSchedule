package com.miuix.hyperschedule.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.miuix.hyperschedule.theme.WideLayoutMinWidth
import com.miuix.hyperschedule.theme.PanelCorner
import com.miuix.hyperschedule.theme.WidePageHeightFraction
import com.miuix.hyperschedule.theme.WidePageMaxWidth
import com.miuix.hyperschedule.theme.useWindowedPages
import com.miuix.hyperschedule.ui.component.BackToNowButton
import com.miuix.hyperschedule.ui.component.BackToNowHostState
import com.miuix.hyperschedule.ui.component.LocalBackToNowHost
import com.miuix.hyperschedule.ui.component.LocalTabPageIndex
import com.miuix.hyperschedule.ui.component.LocalWindowDismissState
import com.miuix.hyperschedule.ui.component.WidePageWindow
import com.miuix.hyperschedule.ui.component.WindowDismissState
import com.miuix.hyperschedule.ui.page.LicensePage
import com.miuix.hyperschedule.ui.effect.captureForBlur
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 浮窗滑入滑出时长与缓动 */
private const val WindowSlideMillis = 330

private val WindowSlideEasing = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }

/** 浮窗页面的进出场：一级从底边滑入，二级及更深从下层浮窗右缘滑入 */
private fun windowPageTransition(level: State<Int>): NavTransition =
    object : NavTransition {
        // 默认 1f 只留顶层下面一层，叠两层浮窗时最底下的整页会被剔除
        override val opaqueDepth: Float = 8f

        override val motion: NavMotion =
            NavMotion(programmatic = NavSettleSpec.Tween(WindowSlideMillis, WindowSlideEasing))

        override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier =
            // 裁剪和平移要在同一个 draw 里，分开写会错位，右缘短暂露出遮罩色
            this.drawWithContent {
                val depth = scope.relativeDepth
                val progress = (-depth).coerceIn(0f, 1f)
                if (progress <= 0f) {
                    drawContent()
                    return@drawWithContent
                }
                val w = size.width
                val h = size.height
                val cardHeight = minOf(w, h) * WidePageHeightFraction
                if (level.value >= 2) {
                    // 二级及更深：位移一个浮窗宽度，裁在浮窗圆角轮廓内
                    val cardWidth = minOf(w, WidePageMaxWidth.toPx())
                    val radius = PanelCorner.toPx()
                    val left = (w - cardWidth) / 2f
                    val top = (h - cardHeight) / 2f
                    val path =
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(left, top, left + cardWidth, top + cardHeight),
                                    cornerRadius = CornerRadius(radius, radius),
                                ),
                            )
                        }
                    // 对齐到整设备像素，圆角边缘不会泛细线
                    val dx = (progress * minOf(w, cardWidth)).roundToInt().toFloat()
                    clipPath(path) {
                        translate(dx, 0f) {
                            this@drawWithContent.drawContent()
                        }
                    }
                } else {
                    // 一级：从屏幕底边滑入
                    val dy = (progress * (h + cardHeight) / 2f).roundToInt().toFloat()
                    translate(0f, dy) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
    }

/** 主页的整屏遮罩，栈里有浮窗页就压暗主页，浮窗整块收起时同步淡出 */
@Composable
private fun WindowScrim(
    backStack: List<NavKey>,
    dismissState: WindowDismissState,
) {
    val open = backStack.size > 1
    val dismissing = dismissState.active
    val dim = remember { Animatable(0f) }
    LaunchedEffect(open, dismissing) {
        when {
            // 整块收起：遮罩跟卡片同步淡出
            dismissing -> dim.animateTo(0f, tween(durationMillis = WindowSlideMillis, easing = WindowSlideEasing))
            // 打开时遮罩很快压下去（日历约 120ms）
            open -> dim.animateTo(1f, tween(durationMillis = 140, easing = WindowSlideEasing))
            // 关闭时等卡片滑出去再淡出
            else -> dim.animateTo(0f, tween(240, delayMillis = 200, easing = WindowSlideEasing))
        }
    }
    val progress = dim.value
    if (progress <= 0f) return
    val dimColor = MiuixTheme.colorScheme.windowDimming
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(dimColor.copy(alpha = dimColor.alpha * progress)),
    )
}

@Composable
private fun NavEntry(
    interceptPredictiveBack: Boolean,
    onBack: () -> Unit,
    onDismissToHome: () -> Unit = onBack,
    windowed: Boolean,
    content: @Composable () -> Unit,
) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = state,
        isBackEnabled = interceptPredictiveBack,
        onBackCompleted = onBack,
    )
    // 宽屏下整页收成居中浮窗，手机和首页始终铺满；点浮窗外区域即关闭整个浮窗
    if (windowed) WidePageWindow(onDismiss = onDismissToHome) { content() } else content()
}

/** 跨页浮层宿主：抽屉这类要盖住底栏的内容挂到这里，在底栏之后绘制 */
class OverlayHostState {
    internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var owner: Any? = null

    internal fun show(key: Any, value: @Composable () -> Unit) {
        owner = key
        content = value
    }

    /** 只撤自己挂的内容，别的页面退出不能把这一页的抽屉一起清掉 */
    internal fun hide(key: Any) {
        if (owner !== key) return
        owner = null
        content = null
    }
}

val LocalOverlayHost = compositionLocalOf<OverlayHostState?> { null }

/** 把内容挂到 [LocalOverlayHost]；[content] 为 null 时撤下 */
@Composable
fun ProvideOverlay(content: (@Composable () -> Unit)?) {
    val host = LocalOverlayHost.current ?: return
    val latest = rememberUpdatedState(content)
    val owner = remember { Any() }
    DisposableEffect(host, content != null) {
        if (content != null) host.show(owner) { latest.value?.invoke() } else host.hide(owner)
        onDispose { host.hide(owner) }
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
    eduImportPage: @Composable (navigate: (Page) -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    eduImportWebPage:
        @Composable (schoolId: String, adapterId: String, onBack: () -> Unit, onImported: () -> Unit) -> Unit =
        { _, _, _, _ -> },
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
    // 浮窗整块收起的共享状态：进度由这里驱动，各浮窗每帧读取
    val windowDismissState = remember { WindowDismissState() }

    // 全局按下位置（见 LocalLastTapDown）
    val lastTapDown = remember { mutableStateOf<Offset?>(null) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // 正在进/出的页面层级：0 首页、1 一级子页面、2 及以上浮窗里的浮窗
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

    // 点浮窗外整块收起：所有浮窗读同一个进度一起下滑，走完再一次性清栈
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
                        // 卡片此时已在屏幕外，Miuix 的退栈动画看不见
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

    // 教务导入完成后直接回到「课表导入」页
    val backToScheduleImport: () -> Unit =
        remember(backStack) {
            {
                navLevel.intValue = 2
                while (backStack.size > 1 && backStack.lastOrNull() != Page.ScheduleImport) {
                    backStack.removeLastOrNull()
                }
            }
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

    BoxWithConstraints(
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
        val wide = maxWidth >= WideLayoutMinWidth
        // 浮窗页面按短边判断：平板横竖屏都是浮窗，手机始终铺满
        val windowed = useWindowedPages(maxWidth, maxHeight)
        CompositionLocalProvider(
            LocalLastTapDown provides lastTapDown,
            LocalOverlayHost provides overlayHost,
            // 浮窗整块收起的共享进度：所有浮窗读同一个进度一起下滑
            LocalWindowDismissState provides windowDismissState,
        ) {
            // 首次组合时的页码，固定不变；不在组合期读 currentPage，否则滑动每帧都会重组
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
                        // 宽屏改用左侧栏
                        Box(modifier = Modifier.fillMaxSize()) {
                            val pageContent: @Composable (Int) -> Unit = { page ->
                                CompositionLocalProvider(
                                    LocalMainTabNavigating provides mainPagerState.isNavigating,
                                    LocalPageIsCurrent provides (page == mainPagerState.selectedPage),
                                    LocalTabPageIndex provides page,
                                    LocalBackToNowHost provides backToNowHost,
                                ) {
                                    content(page, navigate)
                                }
                            }
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (wide) {
                                    SideNavBar(
                                        selectedPage = mainPagerState.targetPage.value,
                                        onSelect = { mainPagerState.animateToPage(it) },
                                    )
                                }
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                ) {
                                    // 关闭用户滑动，切页只走导航，否则顶栏上的拖动会被外层 pager 抢走
                                    val pagerModifier =
                                        Modifier
                                            .fillMaxSize()
                                            .captureForBlur(navBarBackdrop)
                                    // 宽屏上下切换，窄屏左右切换
                                    if (wide) {
                                        VerticalPager(
                                            state = pagerState,
                                            userScrollEnabled = false,
                                            beyondViewportPageCount = 1,
                                            modifier = pagerModifier,
                                        ) { page -> pageContent(page) }
                                    } else {
                                        HorizontalPager(
                                            state = pagerState,
                                            userScrollEnabled = false,
                                            beyondViewportPageCount = 1,
                                            modifier = pagerModifier,
                                        ) { page -> pageContent(page) }
                                    }
                                    // 「今」按钮与底栏同层，切页时先收起，避免「先展开再收起」的闪动
                                    val backToNowSlot = backToNowHost.slotOf(mainPagerState.selectedPage)
                                    BackToNowButton(
                                        visible = !mainPagerState.isNavigating && backToNowSlot?.visible?.value == true,
                                        onClick = { backToNowSlot?.onClick?.invoke() },
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                                        blurBackdrop = navBarBackdrop,
                                    )
                                    // 宽度是本项目的选择（3 个 tab）；只贴底 24dp
                                    if (!wide) {
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
                            // 遮罩只在主页这层：栈里有浮窗页时压暗主页，浮窗自身始终原色
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
                entry<Page.Appearance> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        themePage(onBack)
                    }
                }
                entry<Page.Update> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        updatePage(onBack)
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
                entry<Page.ScheduleImport> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        scheduleImportPage(navigate, onBack)
                    }
                }
                entry<Page.EduImport> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        eduImportPage(navigate, onBack)
                    }
                }
                entry<Page.EduImportWeb> { key ->
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        eduImportWebPage(key.schoolId, key.adapterId, onBack, backToScheduleImport)
                    }
                }
                entry<Page.ScheduleExport> {
                    NavEntry(interceptPredictiveBack, onBack, onDismissToHome = dismissToHome, windowed = windowed) {
                        scheduleExportPage(onBack)
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
            }
        }
    }
}
