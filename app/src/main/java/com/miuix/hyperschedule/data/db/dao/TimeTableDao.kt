package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miuix.hyperschedule.data.model.TimeTable

/** 作息方案访问 */
@Dao
interface TimeTableDao {

    @Query("SELECT * FROM time_tables WHERE id = :id LIMIT 1")
    suspend fun getTimeTableById(id: String): TimeTable?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(timeTable: TimeTable): Long
}
