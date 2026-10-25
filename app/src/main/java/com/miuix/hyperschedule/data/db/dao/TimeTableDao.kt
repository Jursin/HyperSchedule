package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import com.miuix.hyperschedule.data.model.SpecialTimeBlock
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.model.TimeTable
import com.miuix.hyperschedule.data.model.TimeTableSlotCount
import kotlinx.coroutines.flow.Flow

private const val SQL_TIME_TABLE = "SELECT * FROM time_tables WHERE id = :id"
private const val SQL_PUBLIC_TIME_TABLES = "SELECT * FROM time_tables WHERE name IS NOT NULL ORDER BY createdAt ASC"
private const val SQL_SLOTS = "SELECT * FROM time_slots WHERE timeTableId = :timeTableId ORDER BY number ASC"
private const val SQL_SPECIALS = "SELECT * FROM special_time_blocks WHERE timeTableId = :timeTableId ORDER BY startTime ASC"
private const val SQL_BINDING = "SELECT * FROM course_time_bindings WHERE courseTableId = :courseTableId"

/** 作息表及其节次、特殊课程、与课表的绑定 */
@Dao
interface TimeTableDao {

    @Query(SQL_TIME_TABLE)
    suspend fun getTimeTableOnce(id: String): TimeTable?

    @Query(SQL_TIME_TABLE)
    fun observeTimeTable(id: String): Flow<TimeTable?>

    @Query(SQL_PUBLIC_TIME_TABLES)
    fun observePublicTimeTables(): Flow<List<TimeTable>>

    @Query(SQL_PUBLIC_TIME_TABLES)
    suspend fun getPublicTimeTablesOnce(): List<TimeTable>

    @Upsert
    suspend fun upsert(timeTable: TimeTable)

    @Query("DELETE FROM time_tables WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(SQL_SLOTS)
    fun observeSlots(timeTableId: String): Flow<List<TimeSlot>>

    @Query(SQL_SLOTS)
    suspend fun getSlotsOnce(timeTableId: String): List<TimeSlot>

    @Query("SELECT timeTableId, COUNT(*) AS count FROM time_slots GROUP BY timeTableId")
    fun observeSlotCounts(): Flow<List<TimeTableSlotCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlots(timeSlots: List<TimeSlot>)

    @Query("DELETE FROM time_slots WHERE timeTableId = :timeTableId")
    suspend fun deleteSlotsByTimeTableId(timeTableId: String)

    @Query("DELETE FROM time_slots WHERE timeTableId = :timeTableId AND number > :count")
    suspend fun deleteSlotsAbove(timeTableId: String, count: Int)

    @Query(SQL_SPECIALS)
    fun observeSpecials(timeTableId: String): Flow<List<SpecialTimeBlock>>

    @Query(SQL_SPECIALS)
    suspend fun getSpecialsOnce(timeTableId: String): List<SpecialTimeBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpecials(blocks: List<SpecialTimeBlock>)

    @Query("DELETE FROM special_time_blocks WHERE id IN (:ids)")
    suspend fun deleteSpecialsByIds(ids: List<String>)

    @Query("DELETE FROM special_time_blocks WHERE timeTableId = :timeTableId")
    suspend fun deleteSpecialsByTimeTableId(timeTableId: String)

    @Query(SQL_BINDING)
    fun observeBinding(courseTableId: String): Flow<CourseTimeBinding?>

    @Query(SQL_BINDING)
    suspend fun getBindingOnce(courseTableId: String): CourseTimeBinding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBinding(binding: CourseTimeBinding)

    @Query("DELETE FROM course_time_bindings WHERE targetId = :targetId")
    suspend fun deleteBindingsByTargetId(targetId: String)
}
