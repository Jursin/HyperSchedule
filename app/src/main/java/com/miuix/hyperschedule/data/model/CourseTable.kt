package com.miuix.hyperschedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.miuix.hyperschedule.schedule.DEFAULT_TOTAL_WEEKS

/** 课表元数据 */
@Entity(tableName = "course_tables")
data class CourseTable(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long, // 用于排序
)

/** 课表配置，与 CourseTable 一对一，主键即课表 id */
@Entity(
    tableName = "course_table_config",
    foreignKeys = [
        ForeignKey(
            entity = CourseTable::class,
            parentColumns = ["id"],
            childColumns = ["courseTableId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class CourseTableConfig(
    @PrimaryKey val courseTableId: String,
    val showWeekends: Boolean = false,
    val semesterStartDate: String? = null, // "yyyy-MM-dd"，空表示未设置
    val semesterTotalWeeks: Int = DEFAULT_TOTAL_WEEKS,
    val firstDayOfWeek: Int = 1, // 1 = 周一 … 7 = 周日
)

/** 每张课表的课程门数（按课程名去重） */
data class CourseTableCourseCount(
    val courseTableId: String,
    val count: Int,
)
