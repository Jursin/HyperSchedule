package com.miuix.hyperschedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 作息表：name 为空表示课表专属作息（id 即课表 id），非空表示公共作息。
 *
 * 只作 Room 实体使用；导入导出走 data.importer 里的 DTO，实体本身不作传输格式。
 */
@Entity(tableName = "time_tables")
data class TimeTable(
    @PrimaryKey val id: String,
    val name: String? = null,
    val createdAt: Long,
    val defaultClassDuration: Int = 90, // 单位分钟
    val morningAfternoonSplit: String = DEFAULT_MORNING_SPLIT, // 上午/下午分界，空串按默认值处理
    val afternoonEveningSplit: String = DEFAULT_EVENING_SPLIT, // 下午/晚上分界，空串按默认值处理
) {
    /** 没有名字的是课表专属作息 */
    val isExclusive: Boolean get() = name == null

    companion object {
        const val DEFAULT_MORNING_SPLIT = "12:00"
        const val DEFAULT_EVENING_SPLIT = "18:00"
    }
}

/** 节次时间段，主键 (timeTableId, number)；起止时间为空串表示「未设置」 */
@Entity(
    tableName = "time_slots",
    foreignKeys = [
        ForeignKey(
            entity = TimeTable::class,
            parentColumns = ["id"],
            childColumns = ["timeTableId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    primaryKeys = ["timeTableId", "number"],
)
data class TimeSlot(
    val timeTableId: String,
    val number: Int, // 第几节，从 1 开始
    val startTime: String, // "08:00"；空串表示未设置
    val endTime: String, // "08:45"；空串表示未设置
    val alias: String? = null, // 如 "早自习"
)

/**
 * 特殊节次：不占节次的固定时段（早读、眼保健操等），按 [days] 在对应星期上显示。
 *
 * [days] 是逗号分隔的星期序号（1 = 周一 … 7 = 周日），空串表示每天。
 */
@Entity(
    tableName = "special_time_blocks",
    foreignKeys = [
        ForeignKey(
            entity = TimeTable::class,
            parentColumns = ["id"],
            childColumns = ["timeTableId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("timeTableId")],
)
data class SpecialTimeBlock(
    @PrimaryKey val id: String,
    val timeTableId: String,
    val name: String,
    val startTime: String, // "07:30"；空串按未设置处理
    val endTime: String, // "07:50"；空串按未设置处理
    val days: String = "",
) {
    /** 生效的星期；空串 / 无法解析时按每天处理 */
    val daySet: Set<Int>
        get() =
            days
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..DAY_COUNT }
                .toSet()
                .ifEmpty { (1..DAY_COUNT).toSet() }

    companion object {
        const val DAY_COUNT = 7

        /** 把选中的星期写成列值；全选（每天）写空串 */
        fun encodeDays(days: Set<Int>): String {
            val valid = days.filter { it in 1..DAY_COUNT }
            return if (valid.size >= DAY_COUNT) "" else valid.sorted().joinToString(",")
        }
    }
}

/** 课表与作息方案的绑定；没有记录时用课表专属作息 */
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
    // 删作息 / 删组合时按 target 反查清理，走索引
    indices = [Index("targetId")],
)
data class CourseTimeBinding(
    @PrimaryKey val courseTableId: String,
    val targetType: String = TARGET_SINGLE, // SINGLE / COMBO
    val targetId: String, // SINGLE 时为 TimeTable id（等于课表 id 即专属），COMBO 时为组合 id
) {
    companion object {
        const val TARGET_SINGLE = "SINGLE"
        const val TARGET_COMBO = "COMBO"
    }
}

/** 组合作息：基准作息 + 按日期区间切换的规则 */
@Entity(
    tableName = "time_table_combos",
    foreignKeys = [
        ForeignKey(
            entity = TimeTable::class,
            parentColumns = ["id"],
            childColumns = ["baseTimeTableId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("baseTimeTableId")],
)
data class TimeTableCombo(
    @PrimaryKey val id: String,
    val name: String,
    val baseTimeTableId: String? = null, // null 表示用课表专属作息做基准
    val createdAt: Long,
)

/** 组合内某段日期使用的作息；节次结构仍由基准决定，这里只提供时间 */
@Entity(
    tableName = "time_table_combo_rules",
    foreignKeys = [
        ForeignKey(
            entity = TimeTableCombo::class,
            parentColumns = ["id"],
            childColumns = ["comboId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TimeTable::class,
            parentColumns = ["id"],
            childColumns = ["targetTimeTableId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("comboId"), Index("targetTimeTableId")],
)
data class TimeTableComboRule(
    @PrimaryKey val id: String,
    val comboId: String,
    val targetTimeTableId: String,
    val startDate: String, // yyyy-MM-dd
    val endDate: String, // yyyy-MM-dd
)

/** 每张作息表的节次条数 */
data class TimeTableSlotCount(
    val timeTableId: String,
    val count: Int,
)
