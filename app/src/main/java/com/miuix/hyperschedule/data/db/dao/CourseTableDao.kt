package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miuix.hyperschedule.data.model.CourseTable

/** 课表元数据访问；课程等子表靠外键级联 */
@Dao
interface CourseTableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(courseTable: CourseTable)

    /** 最早创建的课表，作为当前课表 */
    @Query("SELECT * FROM course_tables ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstTableOnce(): CourseTable?
}
