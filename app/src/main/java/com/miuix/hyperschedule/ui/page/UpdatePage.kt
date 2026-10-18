package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.ui.SwitchPreference
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.PageScrollColumn
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun UpdatePage(store: SettingsStore, onBack: () -> Unit, onCheckUpdate: () -> Unit) {
    val state = store.state
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(state.blur)
    var showProxyDialog by remember { mutableStateOf(false) }
    var proxyInput by remember { mutableStateOf(state.githubProxyUrl) }
    var updateChannelIndex by remember { mutableIntStateOf(0) }
    var updateSourceIndex by remember { mutableIntStateOf(0) }
    Scaffold(topBar = { BlurredTopAppBar("更新", blurBackdrop, scrollBehavior, onBack = onBack) }) { paddingValues ->
        PageScrollColumn(blurBackdrop, scrollBehavior, paddingValues, bottomPadding = 24.dp) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(title = "检查更新", summary = "从 GitHub 检查最新版本", onClick = onCheckUpdate)
                    OverlayDropdownPreference(
                        title = "更新通道",
                        items = listOf("发行版", "预发行版"),
                        selectedIndex = updateChannelIndex,
                        onSelectedIndexChange = { updateChannelIndex = it },
                    )
                    OverlayDropdownPreference(
                        title = "更新源",
                        items = listOf("GitHub", "Gitee"),
                        selectedIndex = updateSourceIndex,
                        onSelectedIndexChange = { updateSourceIndex = it },
                    )
                    SwitchPreference("自动检查更新", "打开应用时自动检查新版本", state.checkUpdateOnStart) {
                        store.update { it.copy(checkUpdateOnStart = !it.checkUpdateOnStart) }
                    }
                    // 加速地址只对 GitHub 源有意义，选 Gitee 时收起
                    if (updateSourceIndex == 0) {
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
        }
    }
    if (showProxyDialog) {
        ConfirmDialog(
            title = "设置 GitHub 加速地址",
            appBlur = state.blur,
            onConfirm = {
                store.update { it.copy(githubProxyUrl = proxyInput.trim()) }
                showProxyDialog = false
            },
            onDismiss = { showProxyDialog = false },
            content = {
                TextField(value = proxyInput, onValueChange = { proxyInput = it }, label = "URL", singleLine = true, modifier = Modifier.fillMaxWidth())
            },
        )
    }
}
