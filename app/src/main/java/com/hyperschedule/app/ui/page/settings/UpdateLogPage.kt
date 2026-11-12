package com.hyperschedule.app.ui.page.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.theme.CardCornerRadius
import com.hyperschedule.app.theme.PageBottomInset
import com.hyperschedule.app.theme.SectionSpacing
import com.hyperschedule.app.theme.warningColor
import com.hyperschedule.app.theme.warningContainerColor
import com.hyperschedule.app.ui.component.BlurredTopAppBar
import com.hyperschedule.app.ui.component.MarkdownText
import com.hyperschedule.app.ui.component.pageContentPadding
import com.hyperschedule.app.ui.component.pageScroll
import com.hyperschedule.app.ui.effect.rememberAppBlurBackdrop
import com.hyperschedule.app.update.ChangelogEntry
import com.hyperschedule.app.update.ReleaseMeta
import com.hyperschedule.app.update.UpdateSource
import com.hyperschedule.app.update.fetchReleaseMeta
import com.hyperschedule.app.update.loadChangelog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 全部更新日志，一版一张卡片 */
@Composable
fun UpdateLogPage(appBlur: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val entries = remember(context) { loadChangelog(context) }
    // 发版信息拿到了才显示，取不到就静默
    var releases by remember { mutableStateOf(emptyMap<String, ReleaseMeta>()) }

    LaunchedEffect(Unit) {
        try {
            releases = withContext(Dispatchers.IO) { fetchReleaseMeta(UpdateSource.GitHub) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 日期和角标空着
        }
    }

    Scaffold(
        topBar = { BlurredTopAppBar("更新日志", blurBackdrop, scrollBehavior, onBack = onBack) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScroll(blurBackdrop, scrollBehavior),
            contentPadding = pageContentPadding(paddingValues, bottom = PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            overscrollEffect = null,
        ) {
            items(entries) { entry -> ChangelogCard(entry, releases[entry.version]) }
        }
    }
}

@Composable
private fun ChangelogCard(entry: ChangelogEntry, meta: ReleaseMeta?) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = CardCornerRadius) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "v${entry.version}",
                        style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (meta?.prerelease == true) PrereleaseBadge()
                }
                meta?.date?.let { date ->
                    Text(
                        text = date,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            MarkdownText(entry.body)
        }
    }
}

/** 预发布版本的角标 */
@Composable
private fun PrereleaseBadge() {
    Text(
        text = "测试版",
        style = MiuixTheme.textStyles.footnote1,
        fontWeight = FontWeight.Medium,
        color = warningColor(),
        modifier =
            Modifier
                .background(
                    color = warningContainerColor(),
                    shape = RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
