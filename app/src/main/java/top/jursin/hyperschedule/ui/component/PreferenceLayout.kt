package top.jursin.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import top.jursin.hyperschedule.theme.CardCornerRadius
import top.jursin.hyperschedule.theme.CardInsidePadding
import top.jursin.hyperschedule.theme.SectionSpacing
import top.jursin.hyperschedule.theme.SectionTitleVerticalPadding
import top.jursin.hyperschedule.theme.useLargeScreenWindow
import top.jursin.hyperschedule.ui.effect.LocalFabBackdrop
import top.jursin.hyperschedule.ui.effect.LocalPageGlassBackdrop
import top.jursin.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 下拉刷新的四个状态文案 */
private val PullRefreshTexts = listOf("下拉刷新", "松手刷新", "正在刷新", "刷新完成")

@Composable
fun PreferenceScreen(
    title: String,
    appBlur: Boolean,
    onBack: (() -> Unit)? = null,
    // 传了 onRefresh 才装下拉刷新，其余页面保持原样
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    // 平板上标题常驻，不随滚动从大标题收成小标题
    val pinnedTitle = useLargeScreenWindow()
    Scaffold(
        topBar = {
            BlurredTopAppBar(
                title = title,
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                actions = actions,
                pinnedTitle = pinnedTitle,
            )
        },
        floatingActionButton = {
            CompositionLocalProvider(LocalFabBackdrop provides blurBackdrop) {
                Box(Modifier.clearFocusOnPress(LocalFocusManager.current)) {
                    floatingActionButton()
                }
            }
        },
    ) { paddingValues ->
        val page: @Composable () -> Unit = {
            CompositionLocalProvider(LocalPageGlassBackdrop provides blurBackdrop) {
                PageScrollColumn(
                    blurBackdrop = blurBackdrop,
                    scrollBehavior = scrollBehavior,
                    topInset = paddingValues.calculateTopPadding(),
                    content = content,
                )
            }
        }
        if (onRefresh == null) {
            page()
        } else {
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                // 只作用于指示器：让它从栏底下探出；内容自己的顶栏留白在 PageScrollColumn 里
                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                refreshTexts = PullRefreshTexts,
            ) {
                page()
            }
        }
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    SmallTitle(
        text = text,
        modifier = modifier,
        insideMargin =
            PaddingValues(
                start = CardInsidePadding,
                top = SectionTitleVerticalPadding,
                end = CardInsidePadding,
                bottom = SectionTitleVerticalPadding,
            ),
    )
}

@Composable
fun PreferenceSection(
    title: String? = null,
    cardColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardCornerRadius,
            colors = CardDefaults.defaultColors(color = cardColor),
        ) {
            content()
        }
    }
}

@Composable
fun PreferenceItemSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) SectionTitle(title)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            content = content,
        )
    }
}
