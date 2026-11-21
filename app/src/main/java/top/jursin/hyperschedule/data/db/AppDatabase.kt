package top.jursin.hyperschedule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import top.jursin.hyperschedule.data.db.dao.BackupDao
import top.jursin.hyperschedule.data.db.dao.CourseDao
import top.jursin.hyperschedule.data.db.dao.CourseTableDao
import top.jursin.hyperschedule.data.db.dao.TimeTableComboDao
import top.jursin.hyperschedule.data.db.dao.TimeTableDao
import top.jursin.hyperschedule.data.db.entity.Course
import top.jursin.hyperschedule.data.db.entity.CourseTable
import top.jursin.hyperschedule.data.db.entity.CourseTableConfig
import top.jursin.hyperschedule.data.db.entity.CourseTimeBinding
import top.jursin.hyperschedule.data.db.entity.CourseWeek
import top.jursin.hyperschedule.data.db.entity.SpecialTimeBlock
import top.jursin.hyperschedule.data.db.entity.TimeSlot
import top.jursin.hyperschedule.data.db.entity.TimeTable
import top.jursin.hyperschedule.data.db.entity.TimeTableCombo
import top.jursin.hyperschedule.data.db.entity.TimeTableComboRule

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
                        .addMigrations(*ALL_MIGRATIONS)
                        .build()
                        .also { instance = it }
            }
    }
}

private val ALL_MIGRATIONS: Array<Migration> = arrayOf()
