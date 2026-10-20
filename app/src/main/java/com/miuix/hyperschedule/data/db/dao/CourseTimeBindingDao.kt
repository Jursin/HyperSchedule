package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import kotlinx.coroutines.flow.Flow

/** 课表与作息方案的绑定访问 */
@Dao
interface CourseTimeBindingDao {

    @Query("SELECT * FROM course_time_bindings WHERE courseTableId = :courseTableId LIMIT 1")
    suspend fun getBindingByCourseTableId(courseTableId: String): CourseTimeBinding?

    @Query("SELECT * FROM course_time_bindings WHERE courseTableId = :courseTableId LIMIT 1")
    fun observeBindingByCourseTableId(courseTableId: String): Flow<CourseTimeBinding?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(binding: CourseTimeBinding)
}
