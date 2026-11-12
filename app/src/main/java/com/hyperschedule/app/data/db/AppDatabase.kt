package com.hyperschedule.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hyperschedule.app.data.db.dao.BackupDao
import com.hyperschedule.app.data.db.dao.CourseDao
import com.hyperschedule.app.data.db.dao.CourseTableDao
import com.hyperschedule.app.data.db.dao.TimeTableComboDao
import com.hyperschedule.app.data.db.dao.TimeTableDao
import com.hyperschedule.app.data.db.entity.Course
import com.hyperschedule.app.data.db.entity.CourseTable
import com.hyperschedule.app.data.db.entity.CourseTableConfig
import com.hyperschedule.app.data.db.entity.CourseTimeBinding
import com.hyperschedule.app.data.db.entity.CourseWeek
import com.hyperschedule.app.data.db.entity.SpecialTimeBlock
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.db.entity.TimeTable
import com.hyperschedule.app.data.db.entity.TimeTableCombo
import com.hyperschedule.app.data.db.entity.TimeTableComboRule

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

    abstract fun backupDao(): BackupDao

    companion object {
        private const val NAME = "hyperschedule.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance
                    ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                        .build()
                        .also { instance = it }
            }
    }
}
