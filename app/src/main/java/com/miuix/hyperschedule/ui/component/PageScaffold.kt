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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.ui.theme.appBarBlur
import com.miuix.hyperschedule.ui.theme.blurAppBarColor
import com.miuix.hyperschedule.ui.theme.captureForBlur
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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
                    Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
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

// 页面内容列：毛玻璃、滚动手势与统一内边距
@Composable
fun PageScrollColumn(
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .captureForBlur(blurBackdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic()
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 12.dp)
                .padding(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
