package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.ColorSwatchPreview
import com.miuix.hyperschedule.theme.PaletteStyle
import com.miuix.hyperschedule.theme.PresetColors
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.theme.ThemeMode
import com.miuix.hyperschedule.ui.SwitchPreference
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.PageScrollColumn
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun AppearancePage(store: SettingsStore, onBack: () -> Unit) {
    val state = store.state
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(state.blur)
    Scaffold(topBar = { BlurredTopAppBar("外观与主题", blurBackdrop, scrollBehavior, onBack = onBack) }) { paddingValues ->
        PageScrollColumn(blurBackdrop, scrollBehavior, paddingValues, bottomPadding = 24.dp) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    OverlayDropdownPreference(
                        title = "主题模式",
                        items = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
                        onSelectedIndexChange = { index -> store.update { state -> state.copy(themeMode = ThemeMode.entries[index]) } },
                    )
                    SwitchPreference("模糊效果", "为应用启用模糊效果", state.blur) {
                        store.update { it.copy(blur = !it.blur) }
                    }
                    SwitchPreference("预测性返回动画", "返回滑动前提前预览即将跳转至的界面", state.predictiveBack) {
                        store.update { it.copy(predictiveBack = !it.predictiveBack) }
                    }
                    SwitchPreference("自定义颜色", "自定义应用主题配色方案", state.customColor) {
                        store.update { it.copy(customColor = !it.customColor) }
                    }
                    if (state.customColor) {
                        SwitchPreference("动态取色", "基于系统壁纸颜色生成配色方案", state.dynamicColor) {
                            store.update { it.copy(dynamicColor = !it.dynamicColor) }
                        }
                        OverlayDropdownPreference(
                            title = "调色板风格",
                            items = PaletteStyle.entries.map { it.title },
                            selectedIndex = PaletteStyle.entries.indexOf(state.paletteStyle),
                            onSelectedIndexChange = { index -> store.update { state -> state.copy(paletteStyle = PaletteStyle.entries[index]) } },
                        )
                        if (!state.dynamicColor) {
                            PresetColorGrid(state.paletteStyle, state.seedColor) { color ->
                                store.update { it.copy(seedColor = color) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetColorGrid(paletteStyle: PaletteStyle, selectedSeed: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val columns = (maxWidth / 80.dp).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PresetColors.chunked(columns).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    rowItems.forEach { preset ->
                        Box(modifier = Modifier.width(80.dp), contentAlignment = Alignment.Center) {
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
