package com.miuix.hyperschedule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.miuix.hyperschedule.data.db.dao.CourseDao
import com.miuix.hyperschedule.data.db.dao.CourseTableDao
import com.miuix.hyperschedule.data.db.dao.TimeTableComboDao
import com.miuix.hyperschedule.data.db.dao.TimeTableDao
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.data.model.CourseTable
import com.miuix.hyperschedule.data.model.CourseTableConfig
import com.miuix.hyperschedule.data.model.CourseTimeBinding
import com.miuix.hyperschedule.data.model.CourseWeek
import com.miuix.hyperschedule.data.model.SpecialTimeBlock
import com.miuix.hyperschedule.data.model.TimeSlot
import com.miuix.hyperschedule.data.model.TimeTable
import com.miuix.hyperschedule.data.model.TimeTableCombo
import com.miuix.hyperschedule.data.model.TimeTableComboRule

@Database(
    entities = [
        CourseTable::class,
        CourseTableConfig::class,
        Course::class,
        CourseWeek::class,
        TimeTable::class,
        TimeSlot::class,
        SpecialTimeBlock::class,
        CourseTimeBinding::class,
        TimeTableCombo::class,
        TimeTableComboRule::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseTableDao(): CourseTableDao

    abstract fun courseDao(): CourseDao

    abstract fun timeTableDao(): TimeTableDao

    abstract fun timeTableComboDao(): TimeTableComboDao

    companion object {
        private const val NAME = "hyperschedule.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance
                    ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                        // 结构仍在早期演进：版本变化时按 schema 自动重建，不做数据迁移
                        .fallbackToDestructiveMigration(true)
                        .build()
                        .also { instance = it }
            }
    }
}
