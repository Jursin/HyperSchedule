package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.author
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.SheetMaxHeight
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.pageContentPadding
import com.miuix.hyperschedule.ui.component.pageScroll
import com.miuix.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun LicensePage(
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val uriHandler = LocalUriHandler.current
    val libs by
        produceLibraries {
            context.assets
                .open("aboutlibraries.json")
                .bufferedReader()
                .use { it.readText() }
        }
    val libraries =
        remember(libs) {
            libs?.libraries?.sortedBy { it.name.lowercase() }.orEmpty()
        }
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { BlurredTopAppBar("开放源代码许可", blurBackdrop, scrollBehavior, onBack = onBack) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScroll(blurBackdrop, scrollBehavior),
            contentPadding = pageContentPadding(paddingValues, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            overscrollEffect = null,
        ) {
            items(libraries, key = { it.uniqueId }) { library ->
                LibraryCard(library = library) {
                    selectedLibrary = library
                    showLicenseDialog = true
                }
            }
        }
    }

    selectedLibrary?.let { library ->
        ConfirmDialog(
            title = library.name,
            appBlur = appBlur,
            summary = library.artifactVersion,
            summaryColor = MiuixTheme.colorScheme.primary,
            show = showLicenseDialog,
            onDismiss = { showLicenseDialog = false },
            onDismissFinished = { selectedLibrary = null },
            onConfirm = {},
            actions = {
                if (!library.website.isNullOrBlank()) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { uriHandler.openUri(library.website!!) },
                        text = "访问主页",
                    )
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showLicenseDialog = false },
                    text = "关闭",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = SheetMaxHeight),
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .scrollEndHaptic()
                            .overScrollVertical(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                    overscrollEffect = null,
                ) {
                    items(library.licenses.toList()) { license ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = CardCornerRadius,
                            colors =
                                CardDefaults.defaultColors(
                                    color = MiuixTheme.colorScheme.surfaceContainerHighest,
                                ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = license.name,
                                    style = MiuixTheme.textStyles.title3,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier =
                                        Modifier.clickable {
                                            license.url?.let { uriHandler.openUri(it) }
                                        },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = license.licenseContent ?: "该许可证无公开文本",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    library: Library,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = library.name,
                    style = MiuixTheme.textStyles.title2,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                )
                library.artifactVersion?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = library.author,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            library.licenses.forEach { license ->
                Card(
                    cornerRadius = CardCornerRadius,
                    colors =
                        CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(
                            text = license.name,
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
