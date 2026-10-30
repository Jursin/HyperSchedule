package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseTable
import com.miuix.hyperschedule.data.model.CourseTableConfig
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import com.miuix.hyperschedule.data.model.CourseWeek
import com.miuix.hyperschedule.data.model.SpecialTimeBlock
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.model.TimeTable
import com.miuix.hyperschedule.data.model.TimeTableCombo
import com.miuix.hyperschedule.data.model.TimeTableComboRule

/** 备份专用的整表读写，清空按引用方到被引用方 */
@Dao
interface BackupDao {

    // 全量读出

    @Query("SELECT * FROM course_tables ORDER BY createdAt ASC")
    suspend fun tables(): List<CourseTable>

    @Query("SELECT * FROM course_table_config ORDER BY courseTableId ASC")
    suspend fun configs(): List<CourseTableConfig>

    @Query("SELECT * FROM courses ORDER BY courseTableId ASC, day ASC, id ASC")
    suspend fun courses(): List<Course>

    @Query("SELECT * FROM course_weeks ORDER BY courseId ASC, weekNumber ASC")
    suspend fun weeks(): List<CourseWeek>

    @Query("SELECT * FROM time_tables ORDER BY createdAt ASC")
    suspend fun timeTables(): List<TimeTable>

    @Query("SELECT * FROM time_slots ORDER BY timeTableId ASC, number ASC")
    suspend fun slots(): List<TimeSlot>

    @Query("SELECT * FROM special_time_blocks ORDER BY timeTableId ASC, startTime ASC")
    suspend fun specials(): List<SpecialTimeBlock>

    @Query("SELECT * FROM course_time_bindings ORDER BY courseTableId ASC")
    suspend fun bindings(): List<CourseTimeBinding>

    @Query("SELECT * FROM time_table_combos ORDER BY createdAt ASC")
    suspend fun combos(): List<TimeTableCombo>

    @Query("SELECT * FROM time_table_combo_rules ORDER BY comboId ASC, startDate ASC")
    suspend fun comboRules(): List<TimeTableComboRule>

    // 清空顺序即外键依赖顺序

    @Query("DELETE FROM time_table_combo_rules") suspend fun clearComboRules()

    @Query("DELETE FROM time_table_combos") suspend fun clearCombos()

    @Query("DELETE FROM course_time_bindings") suspend fun clearBindings()

    @Query("DELETE FROM course_weeks") suspend fun clearWeeks()

    @Query("DELETE FROM course_table_config") suspend fun clearConfigs()

    @Query("DELETE FROM courses") suspend fun clearCourses()

    @Query("DELETE FROM special_time_blocks") suspend fun clearSpecials()

    @Query("DELETE FROM time_slots") suspend fun clearSlots()

    @Query("DELETE FROM course_tables") suspend fun clearTables()

    @Query("DELETE FROM time_tables") suspend fun clearTimeTables()

    // 写入顺序与外键依赖相反

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(rows: List<CourseTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(rows: List<CourseTableConfig>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(rows: List<Course>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeks(rows: List<CourseWeek>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBindings(rows: List<CourseTimeBinding>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeTables(rows: List<TimeTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(rows: List<TimeSlot>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecials(rows: List<SpecialTimeBlock>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCombos(rows: List<TimeTableCombo>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComboRules(rows: List<TimeTableComboRule>)
}
