package com.miuix.hyperschedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 作息方案：专属方案 id == courseTableId 且 name 为 null；公共方案 id 为独立 UUID、name 非空 */
@Serializable
@Entity(tableName = "time_tables")
data class TimeTable(
    @PrimaryKey val id: String,
    val name: String? = null,
    val createdAt: Long,
    val defaultClassDuration: Int = 45, // 单位分钟
    val defaultBreakDuration: Int = 10,
)

/** 节次时间段，主键 (timeTableId, number) */
@Serializable
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
    indices = [Index("timeTableId")],
    primaryKeys = ["timeTableId", "number"],
)
data class TimeSlot(
    val timeTableId: String,
    val number: Int, // 第几节，从 1 开始
    val startTime: String, // "08:00"
    val endTime: String, // "08:45"
    val alias: String? = null, // 如 "早自习"
)

/** 组合作息方案 */
@Serializable
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
    val baseTimeTableId: String? = null, // null = 动态专属作息；非空 = 公共作息 ID
    val createdAt: Long,
)

/** 组合方案内的日期段调度规则 */
@Serializable
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
    val startDate: String, // "2026-05-01"
    val endDate: String,
)
