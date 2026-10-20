package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.miuix.hyperschedule.data.model.CourseWeek

/** 课程与周次的关联访问 */
@Dao
interface CourseWeekDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(courseWeeks: List<CourseWeek>)

    /** 全量替换某门课的周次：先清旧关系再插新关系 */
    @Transaction
    suspend fun updateCourseWeeks(courseId: String, courseWeeks: List<CourseWeek>) {
        deleteByCourseId(courseId)
        insertAll(courseWeeks)
    }

    @Query("DELETE FROM course_weeks WHERE courseId = :courseId")
    suspend fun deleteByCourseId(courseId: String)
}
