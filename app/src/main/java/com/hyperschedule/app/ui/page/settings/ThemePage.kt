package com.hyperschedule.app.ui.page.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.hyperschedule.app.data.local.AccentColor
import com.hyperschedule.app.data.local.PaletteStyle
import com.hyperschedule.app.data.local.SettingsStore
import com.hyperschedule.app.data.local.ThemeMode
import com.hyperschedule.app.ui.component.PreferenceScreen
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.pref.DropdownPreference
import com.hyperschedule.app.ui.component.pref.SwitchPreference
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

@Composable
fun ThemePage(store: SettingsStore, onBack: () -> Unit) {
    val state = store.state
    // 各开关依赖的系统能力不同，不支持的版本直接不显示
    val supportsBlur = isRuntimeShaderSupported()
    val supportsPredictiveBack = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    PreferenceScreen(title = "外观与主题", appBlur = state.blur, onBack = onBack) {
        PreferenceSection("颜色与主题") {
            EnumDropdown("主题模式", ThemeMode.entries, state.themeMode, ThemeMode::title) { mode ->
                store.update { it.copy(themeMode = mode) }
            }
            SwitchPreference(
                checked = state.useMonet,
                onCheckedChange = { checked -> store.update { it.copy(useMonet = checked) } },
                title = "Monet 取色",
            )
            AnimatedVisibility(
                visible = state.useMonet,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    EnumDropdown("色彩风格", PaletteStyle.entries, state.paletteStyle, PaletteStyle::title) { style ->
                        store.update { it.copy(paletteStyle = style) }
                    }
                    EnumDropdown("强调色", AccentColor.entries, state.accentColor, AccentColor::title) { accent ->
                        store.update { it.copy(accentColor = accent) }
                    }
                    SwitchPreference(
                        checked = state.pureBlack,
                        onCheckedChange = { checked -> store.update { it.copy(pureBlack = checked) } },
                        title = "纯黑背景",
                        summary = "深色模式下使用纯黑背景",
                    )
                }
            }
        }

        PreferenceSection("界面与效果") {
            if (supportsBlur) {
                SwitchPreference(
                    checked = state.blur,
                    onCheckedChange = { checked -> store.update { it.copy(blur = checked) } },
                    title = "模糊效果",
                    summary = "为应用启用模糊效果",
                )
            }
            if (supportsPredictiveBack) {
                SwitchPreference(
                    checked = state.predictiveBack,
                    onCheckedChange = { checked -> store.update { it.copy(predictiveBack = checked) } },
                    title = "预测性返回动画",
                    summary = "返回滑动前提前预览即将跳转至的界面",
                )
            }
        }
    }
}

/** 枚举下拉，选项文案与选中项都按枚举自身取值 */
@Composable
private fun <T> EnumDropdown(
    title: String,
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    DropdownPreference(
        title = title,
        items = entries.map(label),
        selectedIndex = entries.indexOf(selected).coerceAtLeast(0),
        onSelectedIndexChange = { onSelect(entries[it]) },
    )
}
