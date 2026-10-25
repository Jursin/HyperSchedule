package com.miuix.hyperschedule.data.model

/** 节假日 / 调休工作日；日期均为 yyyy-MM-dd，按开始日期所属年份分桶存储 */
data class HolidayEntry(
    val date: String,
    val name: String,
    val type: Int,
    /** 假期结束日；空 = 单日 */
    val endDate: String = "",
    /** 调休：补哪一天的课（绝对日期） */
    val followDate: String = "",
    /** 手工添加或编辑过，远端数据更新时保留 */
    val custom: Boolean = false,
) {
    companion object {
        /** 假期 */
        const val TYPE_HOLIDAY = 0

        /** 调休工作日 */
        const val TYPE_WORKSWAP = 1
    }
}
