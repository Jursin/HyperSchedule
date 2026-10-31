package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.PageBottomInset
import com.miuix.hyperschedule.theme.PageHorizontalMargin
import com.miuix.hyperschedule.theme.PageTopPadding
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.PanelCorner
import com.miuix.hyperschedule.ui.effect.appBarBlur
import com.miuix.hyperschedule.ui.effect.blurAppBarColor
import com.miuix.hyperschedule.ui.effect.captureForBlur
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 顶栏标题的左右内边距 */
internal val TopBarTitlePadding = 28.dp

/** 顶栏图标的边距 */
internal val TopBarIconPadding = 14.dp

/** 浮窗内顶栏要补的高度：返回按钮圆心对齐窗口圆角的圆心 */
@Composable
private fun windowTopBarInset(): Dp =
    if (LocalInsideFloatingWindow.current) {
        PanelCorner - TopAppBarDefaults.SmallTopAppBarCenterHeight / 2
    } else {
        0.dp
    }

/** 浮窗内顶栏的图标边距 */
@Composable
private fun windowTopBarIconPadding(): Dp =
    if (LocalInsideFloatingWindow.current) {
        PanelCorner - IconButtonDefaults.MinWidth / 2
    } else {
        TopBarIconPadding
    }

/** 浮窗内顶栏整体下移，补出来的一截用 [color] 铺底 */
@Composable
private fun Modifier.windowTopBarTrim(color: Color): Modifier {
    val inset = windowTopBarInset()
    return if (inset > 0.dp) this.background(color).padding(top = inset) else this
}

@Composable
fun BlurredTopAppBar(
    title: String,
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    pinnedTitle: Boolean = false,
    titlePadding: Dp = TopBarTitlePadding,
    color: Color? = null,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
) {
    val navigationIcon = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "返回")
                }
            }
            leading?.invoke()
        }
    }
    val insideWindow = LocalInsideFloatingWindow.current
    val barColor = color ?: blurAppBarColor(blurBackdrop)
    // 浮窗内顶栏下移、图标边距收紧
    val iconPadding = windowTopBarIconPadding()
    val barModifier = Modifier.appBarBlur(blurBackdrop).windowTopBarTrim(barColor)
    // 浮窗内一律用单行顶栏，和原生日历的设置页一致
    val singleLine = pinnedTitle || insideWindow
    // 单行顶栏的标题居中，左右 padding 会挤掉标题宽度
    val effectiveTitlePadding = if (insideWindow) 0.dp else titlePadding
    if (singleLine) {
        SmallTopAppBar(
            title = title,
            modifier = barModifier,
            color = barColor,
            titleColor = titleColor,
            scrollBehavior = scrollBehavior,
            titlePadding = effectiveTitlePadding,
            navigationIconPadding = iconPadding,
            actionIconPadding = iconPadding,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        TopAppBar(
            title = title,
            modifier = barModifier,
            color = barColor,
            titleColor = titleColor,
            scrollBehavior = scrollBehavior,
            titlePadding = effectiveTitlePadding,
            navigationIconPadding = iconPadding,
            actionIconPadding = iconPadding,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}

/** 页面内容的水平边距，浮窗里换成窗口内边距 */
val LocalPageHorizontalMargin = staticCompositionLocalOf { PageHorizontalMargin }

/** 正文距顶栏的间距，浮窗里换成窗口内边距 */
val LocalPageTopPadding = staticCompositionLocalOf { PageTopPadding }

/** 当前页面是否画在整页浮窗里 */
val LocalInsideFloatingWindow = staticCompositionLocalOf { false }

/** 页面内容列 */
@Composable
fun PageScrollColumn(
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    topInset: Dp,
    topPadding: Dp = LocalPageTopPadding.current,
    bottomPadding: Dp = PageBottomInset,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val horizontalPadding = LocalPageHorizontalMargin.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .pageScroll(blurBackdrop, scrollBehavior)
                .clearFocusOnPress(focusManager)
                .verticalScroll(rememberScrollState())
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

/** 页面滚动容器共用的毛玻璃与滚动手势 */
fun Modifier.pageScroll(
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
): Modifier =
    this
        .captureForBlur(blurBackdrop)
        .bouncyScroll()
        .nestedScroll(scrollBehavior.nestedScrollConnection)

/** 滚动容器的回弹与滚到底触感 */
fun Modifier.bouncyScroll(): Modifier = overScrollVertical().scrollEndHaptic()

/** 页面内容统一内边距 */
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

/** 右下角保存按钮：不可保存时用禁用配色 */
@Composable
fun SaveFab(canSave: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor =
            if (canSave) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.disabledPrimaryButton
            },
    ) {
        Icon(
            imageVector = MiuixIcons.Ok,
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

/** 右下角附加操作按钮（如添加规则）：不可用时用禁用配色 */
@Composable
fun AddFab(enabled: Boolean, description: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor =
            if (enabled) {
                MiuixTheme.colorScheme.secondaryVariant
            } else {
                MiuixTheme.colorScheme.disabledSecondaryVariant
            },
    ) {
        Icon(
            imageVector = MiuixIcons.Add,
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
