package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置类页面：毛玻璃顶栏 + 12dp 间距的滚动内容，右下角可放 FAB */
@Composable
fun PreferenceScreen(
    title: String,
    appBlur: Boolean,
    onBack: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    Scaffold(
        topBar = {
            BlurredTopAppBar(
                title = title,
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                actions = actions,
            )
        },
        floatingActionButton = {
            Box(Modifier.clearFocusOnPress(LocalFocusManager.current)) {
                floatingActionButton()
            }
        },
    ) { paddingValues ->
        PageScrollColumn(
            blurBackdrop = blurBackdrop,
            scrollBehavior = scrollBehavior,
            contentPadding = paddingValues,
            content = content,
        )
    }
}

/** 设置分组标题，页面与对话框内共用同一套内边距 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    SmallTitle(
        text = text,
        modifier = modifier,
        insideMargin = PaddingValues(16.dp, 8.dp, 16.dp, 0.dp),
    )
}

/** 一段设置：小标题 + 卡片，标题与卡片间距与设置页一致 */
@Composable
fun PreferenceSection(
    title: String? = null,
    cardColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (title != null) SectionTitle(title)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = cardColor),
    ) {
        content()
    }
}

/** 一段条目：小标题 + 每项各自一张卡片 */
@Composable
fun PreferenceItemSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (title != null) SectionTitle(title)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        content = content,
    )
}
