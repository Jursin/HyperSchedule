package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.miuix.hyperschedule.data.model.TimeTableCombo
import com.miuix.hyperschedule.data.model.TimeTableComboRule

/** 组合作息及其日期段调度规则访问 */
@Dao
interface TimeTableComboDao {

    @Query("SELECT * FROM time_table_combos WHERE id = :id LIMIT 1")
    suspend fun getComboById(id: String): TimeTableCombo?

    @Query("SELECT * FROM time_table_combo_rules WHERE comboId = :comboId")
    suspend fun getRuleListByComboId(comboId: String): List<TimeTableComboRule>
}
