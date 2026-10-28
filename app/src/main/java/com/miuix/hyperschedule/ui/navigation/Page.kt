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

    /** 课程管理：按课名聚合所有时段 */
    @Serializable data object CourseManage : Page()

    /** 课程编辑（同名课程所有时段）；name 为空表示新建，day/startSection/endSection 用于预填第一张卡 */
    @Serializable
    data class CourseEdit(
        val name: String,
        val day: Int = 0,
        val startSection: Int = 0,
        val endSection: Int = 0,
        val focusId: String = "", // 进入后定位到的时段（课程 id）
    ) : Page()

    /** 作息管理：作息方案列表 */
    @Serializable data object TimeTableManage : Page()

    /** 课表管理：课表列表，切换当前课表 */
    @Serializable data object CourseTableManage : Page()

    /** 课表导入：导入方式与文件入口 */
    @Serializable data object ScheduleImport : Page()

    /** 教务系统导入，选择学校 */
    @Serializable data object EduImport : Page()

    /** 课表导出：选择课表与导出格式 */
    @Serializable data object ScheduleExport : Page()

    /** 备份与恢复：本地 / WebDAV 备份 */
    @Serializable data object Backup : Page()

    /** 节假日与调休：假期区间与调休补班日 */
    @Serializable data object Holiday : Page()

    /** 课程提醒：提醒开关与通知权限 */
    @Serializable data object Reminder : Page()

    /** 编辑作息方案（专属/公共）；[planId] 为空表示新建 */
    @Serializable data class TimeTableEdit(val planId: String = "") : Page()

    /** 编辑组合作息；[comboId] 为空表示新建 */
    @Serializable data class ComboEdit(val comboId: String = "") : Page()
}
