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

    /** 课程全部周；week 为进入时浏览到的教学周，用于打开后定位 */
    @Serializable data class CourseDetail(val name: String, val week: Int) : Page()

    /** 课程编辑（同名课程所有时段）；name 为空表示新建，day/startSection/endSection 用于预填第一张卡 */
    @Serializable
    data class CourseEdit(
        val name: String,
        val day: Int = 0,
        val startSection: Int = 0,
        val endSection: Int = 0,
        val focusId: String = "", // 进入后定位到的时段（课程 id）
    ) : Page()
}
