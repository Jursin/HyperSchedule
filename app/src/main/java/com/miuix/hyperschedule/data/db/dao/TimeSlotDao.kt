package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miuix.hyperschedule.data.model.TimeSlot
import kotlinx.coroutines.flow.Flow

/** 节次时间段访问 */
@Dao
interface TimeSlotDao {

    @Query("SELECT * FROM time_slots WHERE timeTableId = :timeTableId ORDER BY number ASC")
    fun getTimeSlotsByTimeTableId(timeTableId: String): Flow<List<TimeSlot>>

    @Query("SELECT * FROM time_slots WHERE timeTableId = :timeTableId ORDER BY number ASC")
    suspend fun getTimeSlotsOnceByTimeTableId(timeTableId: String): List<TimeSlot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeSlots: List<TimeSlot>)

    @Query("DELETE FROM time_slots WHERE timeTableId = :timeTableId")
    suspend fun deleteAllTimeSlotsByTimeTableId(timeTableId: String)
}
