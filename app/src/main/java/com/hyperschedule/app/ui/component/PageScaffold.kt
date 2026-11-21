package com.hyperschedule.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.theme.PageBottomInset
import com.hyperschedule.app.theme.PageHorizontalMargin
import com.hyperschedule.app.theme.PanelCorner
import com.hyperschedule.app.theme.isDarkTheme
import com.hyperschedule.app.theme.SectionSpacing
import com.hyperschedule.app.ui.effect.LocalPageGlassBackdrop
import com.hyperschedule.app.ui.effect.captureForBlur
import com.hyperschedule.app.ui.effect.wallpaperVisible
import com.hyperschedule.app.ui.effect.glassTint
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationRailState
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 两套顶栏共用的横向内边距 */
private val BarHorizontalPadding = GlassTopAppBarDefaults.HorizontalPadding

@Composable
fun TopBarLeadingLabel(text: String) {
    val base = MiuixTheme.textStyles.title4
    Text(
        text = text,
        style =
            base.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = base.fontSize,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 浮窗内顶栏要补的高度，返回按钮圆心对齐窗口圆角的圆心 */
@Composable
private fun windowTopBarInset(): Dp =
    if (LocalInsideFloatingWindow.current) {
        PanelCorner - TopAppBarDefaults.CollapsedHeight / 2
    } else {
        0.dp
    }

/** 浮窗内顶栏整体下移，补出来的一截用 [color] 铺底 */
@Composable
private fun Modifier.windowTopBarTrim(color: Color): Modifier {
    val inset = windowTopBarInset()
    return if (inset > 0.dp) this.background(color).padding(top = inset) else this
}

/** 当前顶栏是否是玻璃版 */
val LocalTopBarGlass = staticCompositionLocalOf { false }

@Composable
fun BlurredTopAppBar(
    title: String,
    blurBackdrop: Backdrop?,
    scrollBehavior: ScrollBehavior,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    leadingStart: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    pinnedTitle: Boolean = false,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    color: Color? = null,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    materialPinned: Boolean = false,
) {
    val insideWindow = LocalInsideFloatingWindow.current
    // 浮窗内一律用单行顶栏
    val singleLine = pinnedTitle || insideWindow
    // 侧栏折叠成胶囊条时让开标题，跟着 morph 进度连续淡出
    val rail = LocalRailState.current
    val titleAlpha = { titleColor.alpha * (rail?.progress ?: 1f) }
    val glass = blurBackdrop?.takeIf { isRuntimeShaderSupported() }
    // 导航槽的左边距
    val slotStart = BarHorizontalPadding
    // 有返回按钮时标签跟着按钮走，不再补差
    val leadingInset =
        if (onBack == null) (leadingStart - slotStart).coerceAtLeast(0.dp) else 0.dp
    val navigationIcon = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TopBarIconButton(onClick = onBack) {
                    Icon(imageVector = MiuixIcons.Glass.ChevronBackward, contentDescription = "返回")
                }
            }
            if (leading != null) {
                Box(modifier = Modifier.padding(start = leadingInset)) { leading() }
            }
        }
    }
    if (glass != null) {
        val barFill = color?.takeIf { it.alpha > 0f }
        GlassAppBar(
            title = title,
            topInset = if (insideWindow) windowTopBarInset() else 0.dp,
            modifier = if (barFill != null) Modifier.background(barFill) else Modifier,
            bandEnabled = barFill == null,
            singleLine = singleLine,
            pinnedMaterial = materialPinned,
            backdrop = glass,
            scrollBehavior = scrollBehavior,
            titleAlpha = titleAlpha,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
        return
    }

    val barColor = color ?: if (wallpaperVisible()) Color.Transparent else MiuixTheme.colorScheme.surface
    val barTitleColor = if (rail != null && !rail.expanded) titleColor.copy(alpha = 0f) else titleColor
    val barModifier = Modifier.windowTopBarTrim(barColor)
    TopAppBar(
        title = title,
        modifier = barModifier,
        expandable = !singleLine,
        color = barColor,
        titleColor = barTitleColor,
        scrollBehavior = scrollBehavior,
        titlePadding = titlePadding,
        navigationIconPadding = BarHorizontalPadding,
        actionIconPadding = BarHorizontalPadding,
        navigationIcon = navigationIcon,
        actions = actions,
        bottomContent = bottomContent,
    )
}

@Composable
private fun GlassAppBar(
    title: String,
    modifier: Modifier,
    topInset: Dp,
    bandEnabled: Boolean,
    singleLine: Boolean,
    pinnedMaterial: Boolean,
    backdrop: Backdrop,
    scrollBehavior: ScrollBehavior,
    titleAlpha: () -> Float,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    bottomContent: @Composable () -> Unit,
) {
    val glassStyle = GlassDefaults.style(isDarkTheme())
    // 栏内按钮换成玻璃圆片
    CompositionLocalProvider(LocalTopBarGlass provides true) {
        Box(modifier = modifier.fillMaxWidth()) {
            val barModifier = Modifier.fillMaxWidth().padding(top = topInset)
            // 浮窗有底色时不叠玻璃带
            val backgroundAlpha: () -> Float = { if (bandEnabled) 1f else 0f }
            // 没有着色器时的圆片底色
            val buttonFill = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.68f)
            if (pinnedMaterial) {
                GlassTopAppBar(
                    title = title,
                    isContentScrolled = true,
                    modifier = barModifier,
                    expandable = !singleLine,
                    progressiveBlur = bandEnabled,
                    backdrop = backdrop,
                    style = glassStyle,
                    scrollBehavior = scrollBehavior,
                    titleAlpha = titleAlpha,
                    backgroundAlpha = backgroundAlpha,
                    fill = buttonFill,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    bottomContent = bottomContent,
                )
            } else {
                // 跟随 scrollBehavior
                GlassTopAppBar(
                    title = title,
                    modifier = barModifier,
                    expandable = !singleLine,
                    progressiveBlur = bandEnabled,
                    backdrop = backdrop,
                    style = glassStyle,
                    scrollBehavior = scrollBehavior,
                    titleAlpha = titleAlpha,
                    backgroundAlpha = backgroundAlpha,
                    fill = buttonFill,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    bottomContent = bottomContent,
                )
            }
        }
    }
}

/** 顶栏图标按钮 */
@Composable
fun TopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = GlassTopAppBarDefaults.ButtonSize,
    minHeight: Dp = GlassTopAppBarDefaults.ButtonSize,
    tooltip: String? = null,
    content: @Composable () -> Unit,
) {
    val button: @Composable () -> Unit = {
        if (LocalTopBarGlass.current) {
            GlassIconButton(
                onClick = { if (enabled) onClick() },
                modifier = modifier,
                inheritTopBarMaterial = false,
                material = glassTint(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f), 20f),
                style = GlassDefaults.style(isDarkTheme()),
            ) {
                Box(modifier = Modifier.alpha(if (enabled) 1f else 0.4f)) {
                    content()
                }
            }
        } else {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                minWidth = minWidth,
                minHeight = minHeight,
                content = content,
            )
        }
    }
    if (tooltip == null) button() else TooltipBox(text = tooltip) { button() }
}

/** 页面内容的水平边距，浮窗里换成窗口内边距 */
val LocalPageHorizontalMargin = staticCompositionLocalOf { PageHorizontalMargin }

/** 正文距顶栏的间距，浮窗里换成窗口内边距 */
val LocalPageTopPadding = staticCompositionLocalOf { 10.dp }

/** 当前页面是否画在整页浮窗里 */
val LocalInsideFloatingWindow = staticCompositionLocalOf { false }

val LocalRailState = staticCompositionLocalOf<GlassNavigationRailState?> { null }

@Composable
fun PageScrollColumn(
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    topInset: Dp,
    topPadding: Dp = LocalPageTopPadding.current,
    bottomPadding: Dp = PageBottomInset,
    // 翻页容器里只有当前页传 true
    syncMaterial: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val horizontalPadding = LocalPageHorizontalMargin.current
    val scrollState = rememberScrollState()
    // 各页共用一个 behavior，翻页后要按本页真实位置改写
    LaunchedEffect(syncMaterial, scrollState) {
        if (syncMaterial) {
            scrollBehavior.state.contentOffset = -scrollState.value.toFloat()
            // 上一页的弹性位移不能留给这一页
            scrollBehavior.state.overscrollOffset = 0f
        }
    }
    CompositionLocalProvider(LocalPageGlassBackdrop provides blurBackdrop) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pageScroll(blurBackdrop, scrollBehavior)
                    .clearFocusOnPress(focusManager)
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        top = topInset + topPadding,
                        end = horizontalPadding,
                        bottom = bottomPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            content = content,
        )
    }
}

/** 页面滚动容器共用的毛玻璃与滚动手势 */
@Composable
fun Modifier.pageScroll(
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
): Modifier =
    this
        .captureForBlur(blurBackdrop)
        .bouncyScroll(scrollBehavior)
        .nestedScroll(scrollBehavior.nestedScrollConnection)

@Composable
fun Modifier.bouncyScroll(scrollBehavior: ScrollBehavior? = null): Modifier {
    val state = scrollBehavior?.state
    val onOffsetChanged: ((Float) -> Unit)? = remember(state) {
        state?.let { { offset: Float -> it.overscrollOffset = offset } }
    }
    return overScrollVertical(onOffsetChanged = onOffsetChanged).scrollEndHaptic()
}

@Composable
fun pageContentPadding(
    paddingValues: PaddingValues,
    bottom: Dp,
): PaddingValues =
    PaddingValues(
        start = LocalPageHorizontalMargin.current,
        top = paddingValues.calculateTopPadding() + LocalPageTopPadding.current,
        end = LocalPageHorizontalMargin.current,
        bottom = bottom,
    )

/** 按下页面内任意位置（包括按钮）时收起输入焦点与键盘 */
internal fun Modifier.clearFocusOnPress(focusManager: FocusManager): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                    focusManager.clearFocus()
                }
            }
        }
    }

/** 右下角保存按钮，不可保存时用禁用配色 */
@Composable
fun SaveFab(canSave: Boolean, onClick: () -> Unit) {
    GlassFab(
        onClick = onClick,
        color =
            if (canSave) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.disabledPrimaryButton
            },
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.Ok,
            contentDescription = "保存",
            tint =
                if (canSave) {
                    MiuixTheme.colorScheme.onPrimary
                } else {
                    MiuixTheme.colorScheme.disabledOnPrimaryButton
                },
        )
    }
}

/** 右下角附加操作按钮（如添加规则），不可用时用禁用配色 */
@Composable
fun AddFab(enabled: Boolean, description: String, onClick: () -> Unit) {
    GlassFab(
        onClick = { if (enabled) onClick() },
        color =
            if (enabled) {
                MiuixTheme.colorScheme.secondaryVariant
            } else {
                MiuixTheme.colorScheme.disabledSecondaryVariant
            },
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.Add,
            contentDescription = description,
            tint =
                if (enabled) {
                    MiuixTheme.colorScheme.onSecondaryVariant
                } else {
                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                },
        )
    }
}
