package com.miuix.hyperschedule.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var state by mutableStateOf(load())
        private set

    fun update(transform: (SettingsState) -> SettingsState) {
        state = transform(state)
        preferences.edit()
            .putString("themeMode", state.themeMode.name)
            .putBoolean("blur", state.blur)
            .putBoolean("predictiveBack", state.predictiveBack)
            .putBoolean("customColor", state.customColor)
            .putBoolean("dynamicColor", state.dynamicColor)
            .putBoolean("floatingNav", state.floatingNav)
            .putInt("seedColor", state.seedColor)
            .putBoolean("checkUpdateOnStart", state.checkUpdateOnStart)
            .putString("githubProxyUrl", state.githubProxyUrl)
            .putString("paletteStyle", state.paletteStyle.name)
            .apply()
    }

    private fun load() = SettingsState(
        themeMode = preferences.getString("themeMode", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.System,
        blur = preferences.getBoolean("blur", true),
        predictiveBack = preferences.getBoolean("predictiveBack", true),
        customColor = preferences.getBoolean("customColor", false),
        dynamicColor = preferences.getBoolean("dynamicColor", true),
        floatingNav = preferences.getBoolean("floatingNav", false),
        seedColor = preferences.getInt("seedColor", 0xFF6750A4.toInt()),
        checkUpdateOnStart = preferences.getBoolean("checkUpdateOnStart", true),
        githubProxyUrl = preferences.getString("githubProxyUrl", "").orEmpty(),
        paletteStyle = preferences.getString("paletteStyle", null)
            ?.let { runCatching { PaletteStyle.valueOf(it) }.getOrNull() }
            ?: PaletteStyle.TonalSpot,
    )
}

data class SettingsState(
    val themeMode: ThemeMode,
    val blur: Boolean,
    val predictiveBack: Boolean,
    val customColor: Boolean,
    val dynamicColor: Boolean,
    val floatingNav: Boolean,
    val checkUpdateOnStart: Boolean,
    val githubProxyUrl: String,
    val paletteStyle: PaletteStyle,
    val seedColor: Int,
)
