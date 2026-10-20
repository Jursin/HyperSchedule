package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miuix.hyperschedule.data.model.CourseTableConfig
import kotlinx.coroutines.flow.Flow

/** 课表配置访问，与课表一对一 */
@Dao
interface CourseTableConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: CourseTableConfig)

    @Query("SELECT * FROM course_table_config WHERE courseTableId = :courseTableId")
    fun getConfigById(courseTableId: String): Flow<CourseTableConfig?>

    @Query("SELECT * FROM course_table_config WHERE courseTableId = :courseTableId LIMIT 1")
    suspend fun getConfigOnce(courseTableId: String): CourseTableConfig?
}
