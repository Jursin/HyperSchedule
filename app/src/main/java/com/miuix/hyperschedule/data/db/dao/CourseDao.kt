package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Transaction
import androidx.room.Upsert
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseWithWeeks
import kotlinx.coroutines.flow.Flow

/** 课程表访问：标准节次与自定义时间混排，按星期 → 节次 → 自定义时间排序 */
@Dao
interface CourseDao {

    @Transaction
    @Query(
        """
        SELECT * FROM courses
        WHERE courseTableId = :courseTableId
        ORDER BY
            day ASC,
            CASE WHEN isCustomTime = 0 THEN startSection ELSE 99 END ASC,
            CASE WHEN isCustomTime = 1 THEN customStartTime ELSE '99:99' END ASC
        """
    )
    @Suppress(RoomWarnings.QUERY_MISMATCH)
    fun getCoursesWithWeeksByTableId(courseTableId: String): Flow<List<CourseWithWeeks>>

    @Upsert
    suspend fun upsert(course: Course)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(courses: List<Course>)

    @Query("DELETE FROM courses WHERE id IN (:courseIds)")
    suspend fun deleteCoursesByIds(courseIds: List<String>)

    @Query("DELETE FROM courses WHERE courseTableId = :courseTableId")
    suspend fun deleteCoursesByTableId(courseTableId: String)

    @Transaction
    @Query(
        """
        SELECT * FROM courses AS c
        INNER JOIN course_weeks AS cw ON c.id = cw.courseId
        WHERE c.courseTableId = :courseTableId
          AND c.day = :day
          AND cw.weekNumber = :weekNumber
        ORDER BY
            CASE WHEN c.isCustomTime = 0 THEN c.startSection ELSE 99 END ASC,
            CASE WHEN c.isCustomTime = 1 THEN c.customStartTime ELSE '99:99' END ASC
        """
    )
    @Suppress(RoomWarnings.QUERY_MISMATCH)
    fun getCoursesWithWeeksByDayAndWeek(
        courseTableId: String,
        day: Int,
        weekNumber: Int,
    ): Flow<List<CourseWithWeeks>>
}
