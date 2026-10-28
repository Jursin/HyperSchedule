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
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.CardInsidePadding
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.SectionTitleVerticalPadding
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置类页面 */
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
            topInset = paddingValues.calculateTopPadding(),
            content = content,
        )
    }
}

/** 设置分组标题 */
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

/** 一段设置 */
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

/** 一段条目 */
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
