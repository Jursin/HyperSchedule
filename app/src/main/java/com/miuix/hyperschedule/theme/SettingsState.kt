package com.miuix.hyperschedule.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

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
            putBoolean("showNonCurrentWeek", state.showNonCurrentWeek)
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
        showNonCurrentWeek = preferences.getBoolean("showNonCurrentWeek", true),
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
    val showNonCurrentWeek: Boolean = true, // 课表里是否显示本周不上（灰显）的课
)
