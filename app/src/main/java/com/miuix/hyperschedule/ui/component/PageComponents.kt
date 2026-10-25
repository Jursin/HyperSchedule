package com.miuix.hyperschedule.ui.component

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.PageBottomInset
import com.miuix.hyperschedule.theme.PageTopPadding
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.ui.effect.appBarBlur
import com.miuix.hyperschedule.ui.effect.blurAppBarColor
import com.miuix.hyperschedule.ui.effect.captureForBlur
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
    val barModifier = Modifier.appBarBlur(blurBackdrop)
    val barColor = blurAppBarColor(blurBackdrop)
    if (pinnedTitle) {
        SmallTopAppBar(
            title = title,
            modifier = barModifier,
            color = barColor,
            scrollBehavior = scrollBehavior,
            titlePadding = 28.dp,
            navigationIconPadding = 20.dp,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        TopAppBar(
            title = title,
            modifier = barModifier,
            color = barColor,
            scrollBehavior = scrollBehavior,
            titlePadding = 28.dp,
            navigationIconPadding = 20.dp,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}

/** 页面内容列：毛玻璃、滚动手势与统一内边距 */
@Composable
fun PageScrollColumn(
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    topPadding: Dp = PageTopPadding,
    bottomPadding: Dp = PageBottomInset,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .pageScroll(blurBackdrop, scrollBehavior)
                .clearFocusOnPress(focusManager)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 12.dp)
                .padding(top = topPadding, bottom = bottomPadding),
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
        .scrollEndHaptic()
        .overScrollVertical()
        .nestedScroll(scrollBehavior.nestedScrollConnection)

/** 页面内容统一内边距：左右 12dp，顶部与顶栏留 4dp，底部由页面决定 */
fun pageContentPadding(paddingValues: PaddingValues, bottom: Dp): PaddingValues =
    PaddingValues(
        start = 12.dp,
        top = paddingValues.calculateTopPadding() + PageTopPadding,
        end = 12.dp,
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
