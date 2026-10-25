package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.miuix.hyperschedule.data.model.TimeTableCombo
import com.miuix.hyperschedule.data.model.TimeTableComboRule
import kotlinx.coroutines.flow.Flow

private const val SQL_COMBO = "SELECT * FROM time_table_combos WHERE id = :id"
private const val SQL_RULES = "SELECT * FROM time_table_combo_rules WHERE comboId = :comboId ORDER BY startDate ASC"

/** 组合作息与它的日期规则 */
@Dao
interface TimeTableComboDao {

    @Query("SELECT * FROM time_table_combos ORDER BY createdAt ASC")
    fun observeCombos(): Flow<List<TimeTableCombo>>

    @Query(SQL_COMBO)
    fun observeCombo(id: String): Flow<TimeTableCombo?>

    @Query(SQL_COMBO)
    suspend fun getComboOnce(id: String): TimeTableCombo?

    @Query("SELECT COUNT(*) FROM time_table_combos")
    suspend fun countCombos(): Int

    @Query(SQL_RULES)
    fun observeRules(comboId: String): Flow<List<TimeTableComboRule>>

    @Query(SQL_RULES)
    suspend fun getRulesOnce(comboId: String): List<TimeTableComboRule>

    @Query("SELECT * FROM time_table_combo_rules ORDER BY comboId ASC, startDate ASC")
    fun observeAllRules(): Flow<List<TimeTableComboRule>>

    @Upsert
    suspend fun upsertCombo(combo: TimeTableCombo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<TimeTableComboRule>)

    @Query("DELETE FROM time_table_combo_rules WHERE comboId = :comboId")
    suspend fun deleteRulesByComboId(comboId: String)

    @Query("DELETE FROM time_table_combo_rules WHERE targetTimeTableId = :timeTableId")
    suspend fun deleteRulesByTargetId(timeTableId: String)

    @Query("UPDATE time_table_combos SET baseTimeTableId = NULL WHERE baseTimeTableId = :timeTableId")
    suspend fun clearBaseTimeTableId(timeTableId: String)

    @Query("DELETE FROM time_table_combos WHERE id = :id")
    suspend fun deleteComboById(id: String)
}
