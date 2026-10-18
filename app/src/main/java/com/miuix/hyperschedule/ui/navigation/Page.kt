package com.miuix.hyperschedule.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed class Page : NavKey {
    @Serializable data object Home : Page()

    @Serializable data object Appearance : Page()

    @Serializable data object Update : Page()

    @Serializable data object About : Page()

    @Serializable data object License : Page()
}
