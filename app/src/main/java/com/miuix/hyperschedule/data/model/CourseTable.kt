package com.miuix.hyperschedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 课表元数据 */
@Serializable
@Entity(tableName = "course_tables")
data class CourseTable(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long, // 用于排序
)

/** 课表配置，与 CourseTable 一对一 */
@Serializable
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
    indices = [Index(value = ["courseTableId"], unique = true)],
)
data class CourseTableConfig(
    @PrimaryKey val courseTableId: String,
    val showWeekends: Boolean = false,
    val semesterStartDate: String? = null, // "yyyy-MM-dd"
    val semesterTotalWeeks: Int = 20,
    val firstDayOfWeek: Int = 1, // 1 = 周一 … 7 = 周日
)

/** 课表与作息方案的绑定：一个课表一条；targetId 等于 courseTableId 时即专属作息 */
@Serializable
@Entity(
    tableName = "course_time_bindings",
    foreignKeys = [
        ForeignKey(
            entity = CourseTable::class,
            parentColumns = ["id"],
            childColumns = ["courseTableId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class CourseTimeBinding(
    @PrimaryKey val courseTableId: String,
    val targetType: TargetType = TargetType.SINGLE,
    val targetId: String,
) {
    @Serializable
    enum class TargetType {
        SINGLE, // 单一作息
        COMBO, // 组合作息
    }
}
