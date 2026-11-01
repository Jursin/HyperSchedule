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
import com.hyperschedule.app.data.model.Course
import com.hyperschedule.app.data.model.CourseTable
import com.hyperschedule.app.data.model.CourseTableConfig
import com.hyperschedule.app.data.model.CourseTimeBinding
import com.hyperschedule.app.data.model.CourseWeek
import com.hyperschedule.app.data.model.SpecialTimeBlock
import com.hyperschedule.app.data.model.TimeSlot
import com.hyperschedule.app.data.model.TimeTable
import com.hyperschedule.app.data.model.TimeTableCombo
import com.hyperschedule.app.data.model.TimeTableComboRule

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
