package com.miuix.hyperschedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.PaletteStyle
import com.miuix.hyperschedule.theme.PresetColors
import com.miuix.hyperschedule.theme.ColorSwatchPreview
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.theme.ThemeMode
import com.miuix.hyperschedule.getAppVersion
import com.miuix.hyperschedule.getAppVersionCode
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.PageScrollColumn
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

private const val RepositoryUrl = "https://github.com/Jursin/CourseSchedule"

@Composable
fun SettingsPage(
    store: SettingsStore,
    onLicenseClick: () -> Unit,
    onCheckUpdate: () -> Unit,
    bottomPadding: Dp = 80.dp,
) {
    val state = store.state
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(state.blur)
    val themeOptions = listOf("跟随系统", "浅色", "深色")
    var showProxyDialog by remember { mutableStateOf(false) }
    var proxyInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = { BlurredTopAppBar("设置", blurBackdrop, scrollBehavior) },
    ) { paddingValues ->
        PageScrollColumn(
            blurBackdrop = blurBackdrop,
            scrollBehavior = scrollBehavior,
            contentPadding = paddingValues,
            topPadding = 4.dp,
            bottomPadding = bottomPadding,
        ) {
            SmallTitle(text = "外观设置", insideMargin = PaddingValues(16.dp, 8.dp, 16.dp, 0.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    OverlayDropdownPreference(
                        title = "主题模式",
                        items = themeOptions,
                        selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
                        onSelectedIndexChange = { index ->
                            store.update { it.copy(themeMode = ThemeMode.entries[index]) }
                        },
                    )
                    SwitchPreference("模糊效果", "为顶栏、底栏、对话框添加模糊效果", state.blur) {
                        store.update { it.copy(blur = it.blur.not()) }
                    }
                    SwitchPreference("预测性返回动画", "返回滑动前提前预览即将跳转至的界面", state.predictiveBack) {
                        store.update { it.copy(predictiveBack = it.predictiveBack.not()) }
                    }
                    SwitchPreference("自定义颜色", "自定义应用主题配色方案", state.customColor) {
                        store.update { it.copy(customColor = it.customColor.not()) }
                    }
                    if (state.customColor) {
                        SwitchPreference("动态取色", "基于系统壁纸颜色生成配色方案", state.dynamicColor) {
                            store.update { it.copy(dynamicColor = it.dynamicColor.not()) }
                        }
                        OverlayDropdownPreference(
                            title = "调色板风格",
                            items = PaletteStyle.entries.map { it.title },
                            selectedIndex = PaletteStyle.entries.indexOf(state.paletteStyle),
                            onSelectedIndexChange = { index ->
                                store.update { it.copy(paletteStyle = PaletteStyle.entries[index]) }
                            },
                        )
                        if (!state.dynamicColor) {
                            PresetColorGrid(
                                paletteStyle = state.paletteStyle,
                                selectedSeed = state.seedColor,
                                onSelect = { color -> store.update { it.copy(seedColor = color) } },
                            )
                        }
                    }
                    SwitchPreference("悬浮底栏", "切换悬浮式底部导航栏", state.floatingNav) {
                        store.update { it.copy(floatingNav = it.floatingNav.not()) }
                    }
                }
            }

            SmallTitle(text = "更新设置", insideMargin = PaddingValues(16.dp, 8.dp, 16.dp, 0.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = "检查更新",
                        summary = "从 GitHub 检查最新版本",
                        onClick = onCheckUpdate,
                    )
                    SwitchPreference("启动时检查更新", "打开应用时自动检查新版本", state.checkUpdateOnStart) {
                        store.update { it.copy(checkUpdateOnStart = it.checkUpdateOnStart.not()) }
                    }
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

            SmallTitle(text = "关于", insideMargin = PaddingValues(16.dp, 8.dp, 16.dp, 0.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    BasicComponent(
                        title = "版本",
                        summary = "${getAppVersion(context)} (${getAppVersionCode(context)})",
                    )
                    ArrowPreference(
                        title = "查看源代码",
                        summary = RepositoryUrl,
                        onClick = { uriHandler.openUri(RepositoryUrl) },
                    )
                    ArrowPreference(
                        title = "开放源代码许可",
                        summary = "查看应用所使用的第三方开源库及其许可证信息",
                        onClick = onLicenseClick,
                    )
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
                TextField(
                    value = proxyInput,
                    onValueChange = { proxyInput = it },
                    label = "URL",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun PresetColorGrid(
    paletteStyle: PaletteStyle,
    selectedSeed: Int,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        val columns = (maxWidth / 80.dp).toInt().coerceAtLeast(1)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PresetColors.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    rowItems.forEach { preset ->
                        Box(
                            modifier = Modifier.width(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ColorSwatchPreview(
                                preset = preset,
                                paletteStyle = paletteStyle,
                                selected = selectedSeed == preset.color.toArgb(),
                                onClick = { onSelect(preset.color.toArgb()) },
                            )
                        }
                    }
                }
            }
        }
    }
}
