package com.miuix.hyperschedule.ui.page

import android.os.Build
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
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.pref.OverlayDropdownPreference
import com.miuix.hyperschedule.ui.component.pref.SwitchPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

@Composable
fun ThemePage(store: SettingsStore, onBack: () -> Unit) {
    val state = store.state
    // 各开关依赖的系统能力不同，不支持的版本直接不显示
    val supportsBlur = isRuntimeShaderSupported()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val supportsPredictiveBack = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    PreferenceScreen(title = "外观与主题", appBlur = state.blur, onBack = onBack) {
        PreferenceSection {
            OverlayDropdownPreference(
                title = "主题模式",
                items = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
                onSelectedIndexChange = { index -> store.update { state -> state.copy(themeMode = ThemeMode.entries[index]) } },
            )
            if (supportsBlur) {
                SwitchPreference(checked = state.blur, onCheckedChange = { store.update { it.copy(blur = !it.blur) } }, title = "模糊效果", summary = "为应用启用模糊效果")
            }
            if (supportsPredictiveBack) {
                SwitchPreference(
                    checked = state.predictiveBack,
                    onCheckedChange = { store.update { it.copy(predictiveBack = !it.predictiveBack) } },
                    title = "预测性返回动画",
                    summary = "返回滑动前提前预览即将跳转至的界面",
                )
            }
            SwitchPreference(
                checked = state.customColor,
                onCheckedChange = { store.update { it.copy(customColor = !it.customColor) } },
                title = "自定义颜色",
                summary = "自定义应用主题配色方案",
            )
            if (state.customColor) {
                if (supportsDynamicColor) {
                    SwitchPreference(
                        checked = state.dynamicColor,
                        onCheckedChange = { store.update { it.copy(dynamicColor = !it.dynamicColor) } },
                        title = "动态取色",
                        summary = "基于系统壁纸颜色生成配色方案",
                    )
                }
                OverlayDropdownPreference(
                    title = "调色板风格",
                    items = PaletteStyle.entries.map { it.title },
                    selectedIndex = PaletteStyle.entries.indexOf(state.paletteStyle),
                    onSelectedIndexChange = { index -> store.update { state -> state.copy(paletteStyle = PaletteStyle.entries[index]) } },
                )
                if (!supportsDynamicColor || !state.dynamicColor) {
                    PresetColorGrid(state.paletteStyle, state.seedColor) { color ->
                        store.update { it.copy(seedColor = color) }
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
