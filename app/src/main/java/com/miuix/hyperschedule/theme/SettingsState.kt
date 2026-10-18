package com.miuix.hyperschedule.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS

private inline fun <reified T : Enum<T>> SharedPreferences.enum(name: String, default: T): T =
    getString(name, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var state by mutableStateOf(load())
        private set

    fun update(transform: (SettingsState) -> SettingsState) {
        state = transform(state)
        preferences.edit {
            putString("themeMode", state.themeMode.name)
            putBoolean("blur", state.blur)
            putBoolean("predictiveBack", state.predictiveBack)
            putBoolean("customColor", state.customColor)
            putBoolean("dynamicColor", state.dynamicColor)
            putBoolean("floatingNav", state.floatingNav)
            putInt("launchPage", state.launchPage)
            putInt("seedColor", state.seedColor)
            putBoolean("checkUpdateOnStart", state.checkUpdateOnStart)
            putString("githubProxyUrl", state.githubProxyUrl)
            putString("paletteStyle", state.paletteStyle.name)
            putString("startDate", state.startDate)
            putInt("totalWeeks", state.totalWeeks)
        }
    }

    private fun load() = SettingsState(
        themeMode = preferences.enum("themeMode", ThemeMode.System),
        blur = preferences.getBoolean("blur", true),
        predictiveBack = preferences.getBoolean("predictiveBack", true),
        customColor = preferences.getBoolean("customColor", false),
        dynamicColor = preferences.getBoolean("dynamicColor", true),
        floatingNav = preferences.getBoolean("floatingNav", false),
        launchPage = preferences.getInt("launchPage", 0).coerceIn(0, 1),
        seedColor = preferences.getInt("seedColor", 0xFF6750A4.toInt()),
        checkUpdateOnStart = preferences.getBoolean("checkUpdateOnStart", true),
        githubProxyUrl = preferences.getString("githubProxyUrl", "").orEmpty(),
        paletteStyle = preferences.enum("paletteStyle", PaletteStyle.TonalSpot),
        // 空串 = 未设置
        startDate = preferences.getString("startDate", "").orEmpty(),
        totalWeeks = preferences.getInt("totalWeeks", DEFAULT_TOTAL_WEEKS),
    )
}

data class SettingsState(
    val themeMode: ThemeMode,
    val blur: Boolean,
    val predictiveBack: Boolean,
    val customColor: Boolean,
    val dynamicColor: Boolean,
    val floatingNav: Boolean,
    val launchPage: Int,
    val checkUpdateOnStart: Boolean,
    val githubProxyUrl: String,
    val paletteStyle: PaletteStyle,
    val seedColor: Int,
    val startDate: String,
    val totalWeeks: Int,
)
