package com.hyperschedule.app.ui.page.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.local.SettingsStore
import com.hyperschedule.app.getAppVersion
import com.hyperschedule.app.getAppVersionCode
import com.hyperschedule.app.ui.component.ConfirmDialog
import com.hyperschedule.app.ui.component.MarkdownText
import com.hyperschedule.app.ui.component.PreferenceScreen
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.pref.ArrowPreference
import com.hyperschedule.app.ui.component.pref.OverlayDropdownPreference
import com.hyperschedule.app.ui.component.pref.SwitchPreference
import com.hyperschedule.app.update.ChangelogEntry
import com.hyperschedule.app.update.UpdateController
import com.hyperschedule.app.update.UpdateDialogState
import com.hyperschedule.app.update.UpdateSource
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdatePage(
    store: SettingsStore,
    controller: UpdateController,
    onOpenLog: () -> Unit,
    onBack: () -> Unit,
) {
    val state = store.state
    val context = LocalContext.current
    var showProxyDialog by remember { mutableStateOf(false) }
    var proxyInput by remember { mutableStateOf(state.githubProxyUrl) }
    PreferenceScreen(title = "更新", appBlur = state.blur, onBack = onBack) {
        PreferenceSection {
            ArrowPreference(
                title = "检查更新",
                summary = if (controller.checking) "正在检查" else "当前版本 v${getAppVersion(context)} (${getAppVersionCode(context)})",
                onClick = {
                    // 手动检查也记一次日期，避免重启后再静默查一遍
                    store.update { it.copy(lastUpdateCheck = LocalDate.now().toString()) }
                    controller.checkForUpdate(state.updateSource, state.acceptPrerelease, state.githubProxyUrl)
                },
            )
            ArrowPreference(
                title = "更新日志",
                onClick = onOpenLog,
            )
            OverlayDropdownPreference(
                title = "更新源",
                items = UpdateSource.entries.map { it.label },
                selectedIndex = UpdateSource.entries.indexOf(state.updateSource),
                onSelectedIndexChange = { index ->
                    store.update { it.copy(updateSource = UpdateSource.entries[index]) }
                },
            )
            SwitchPreference(
                checked = state.acceptPrerelease,
                onCheckedChange = { store.update { it.copy(acceptPrerelease = it.acceptPrerelease.not()) } },
                title = "接收测试版",
            )
            SwitchPreference(
                checked = state.checkUpdateOnStart,
                onCheckedChange = { store.update { it.copy(checkUpdateOnStart = it.checkUpdateOnStart.not()) } },
                title = "自动检查更新",
                summary = "打开应用时自动检查新版本",
            )
            ArrowPreference(
                title = "加速地址",
                summary = state.githubProxyUrl.ifBlank { "设置 GitHub 加速地址" },
                onClick = {
                    proxyInput = state.githubProxyUrl
                    showProxyDialog = true
                },
            )
        }
    }
    if (showProxyDialog) {
        ConfirmDialog(
            title = "设置 GitHub 加速地址",
            appBlur = state.blur,
            onDismiss = { showProxyDialog = false },
            onConfirm = {
                store.update { it.copy(githubProxyUrl = proxyInput.trim()) }
                showProxyDialog = false
            },
        ) {
            TextField(
                value = proxyInput,
                onValueChange = { proxyInput = it },
                label = "URL",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 更新流程的弹窗，挂在根组合上以便任意页面都能看到 */
@Composable
fun UpdateDialogs(controller: UpdateController, appBlur: Boolean) {
    when (val dialog = controller.dialog) {
        is UpdateDialogState.Available ->
            ConfirmDialog(
                title = "检测到新版本",
                summary = "v${dialog.version}",
                appBlur = appBlur,
                confirmText = "更新",
                onConfirm = controller::startUpdate,
                onDismiss = controller::dismissDialog,
            ) {
                ReleaseNotesView(notes = dialog.notes)
            }

        is UpdateDialogState.Downloading ->
            ConfirmDialog(
                title = "正在下载更新",
                appBlur = appBlur,
                confirmText = "停止下载",
                cancelText = null,
                destructive = true,
                onConfirm = controller::stopUpdate,
                // 点外部或返回只收起弹窗，下载继续在通知栏显示
                onDismiss = controller::hideProgress,
            ) {
                LinearProgressIndicator(progress = dialog.progress, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "已下载 ${(dialog.progress * 100).toInt()}%",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

        UpdateDialogState.None, is UpdateDialogState.ReadyToInstall -> Unit
    }
}

/** 更新日志 */
@Composable
private fun ReleaseNotesView(notes: List<ChangelogEntry>) {
    val maxHeight = (LocalWindowInfo.current.containerDpSize.height * 0.45f).coerceAtLeast(180.dp)
    if (notes.isEmpty()) {
        Text(
            text = "该版本未提供更新日志",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        return
    }
    val markdown =
        remember(notes) {
            notes.joinToString("\n\n") { note ->
                if (notes.size > 1) "## v${note.version}\n\n${note.body}" else note.body
            }
        }
    MarkdownText(
        markdown = markdown,
        modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
    )
}
