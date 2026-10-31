package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.getAppVersion
import com.miuix.hyperschedule.getAppVersionCode
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.theme.useLargeScreenWindow
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.pref.ArrowPreference
import com.miuix.hyperschedule.ui.component.pref.OverlayDropdownPreference
import com.miuix.hyperschedule.ui.component.pref.SwitchPreference
import com.miuix.hyperschedule.ui.effect.WindowBlurEffect
import com.miuix.hyperschedule.update.UpdateController
import com.miuix.hyperschedule.update.UpdateDialogState
import com.miuix.hyperschedule.update.UpdateSource
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun UpdatePage(store: SettingsStore, controller: UpdateController, onBack: () -> Unit) {
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
            // 加速地址只对 GitHub 源有意义，选 Gitee 时收起
            if (state.updateSource == UpdateSource.GitHub) {
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
                title = "检查到新版本",
                message = "确定更新到最新版本 v${dialog.version} 吗？",
                appBlur = appBlur,
                confirmText = "更新",
                onConfirm = controller::startUpdate,
                onDismiss = controller::dismissDialog,
            )

        is UpdateDialogState.Downloading ->
            ConfirmDialog(
                title = "正在下载更新",
                appBlur = appBlur,
                confirmText = "停止下载",
                cancelText = null,
                destructive = true,
                onConfirm = controller::stopUpdate,
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

        UpdateDialogState.AwaitingPermission -> StatusDialog("正在等待授权", "请在系统设置中允许安装应用", appBlur)

        // 后台下载完成后靠通知把用户叫回来，回到前台立刻拉起安装器
        UpdateDialogState.None, is UpdateDialogState.ReadyToInstall -> Unit

        UpdateDialogState.Installing ->
            StatusDialog("正在安装更新", "安装完成后应用会自动关闭，重新打开即可用上新版本", appBlur)

        UpdateDialogState.NeedPermission ->
            ConfirmDialog(
                title = "需要安装应用权限",
                message = "更新应用需要安装应用权限，请在系统设置中开启。",
                appBlur = appBlur,
                confirmText = "去设置",
                onConfirm = controller::openInstallSettings,
                onDismiss = controller::dismissDialog,
            )
    }
}

/** 只有一句说明的等待态弹窗 */
@Composable
private fun StatusDialog(title: String, text: String, appBlur: Boolean) {
    WindowDialog(
        show = true,
        title = title,
        // 平板（横竖屏）居中弹出，手机贴底
        largeScreen = useLargeScreenWindow(),
        content = {
            WindowBlurEffect(useBlur = appBlur)
            Text(text = text)
        },
    )
}
