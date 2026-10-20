package com.miuix.hyperschedule.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

/** 课程：标准节次时间与自定义时间二选一（isCustomTime 为 true 时忽略 start/endSection） */
@Serializable
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = CourseTable::class,
            parentColumns = ["id"],
            childColumns = ["courseTableId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("courseTableId")],
)
data class Course(
    @PrimaryKey val id: String,
    val courseTableId: String,
    val name: String,
    val teacher: String,
    val position: String,
    val day: Int, // 1 = 周一 … 7 = 周日
    val startSection: Int?,
    val endSection: Int?,
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null, // "HH:mm"
    val customEndTime: String? = null,
    val colorInt: Int, // 课程色板索引，见 ScheduleCourseCard 的 CoursePalette
    val colorArgb: Int? = null, // 自定义颜色；为空时回落到 colorInt 对应的色板色
    val remark: String? = null, // 限 300 字
    val credit: Float? = null,
)

/** 课程与周次的多对多关联，主键 (courseId, weekNumber) */
@Serializable
@Entity(
    tableName = "course_weeks",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("courseId"), Index("weekNumber")],
    primaryKeys = ["courseId", "weekNumber"],
)
data class CourseWeek(
    val courseId: String,
    val weekNumber: Int,
)

/** 课程 + 其生效周次 */
@Serializable
data class CourseWithWeeks(
    @Embedded val course: Course,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val weeks: List<CourseWeek>,
)
