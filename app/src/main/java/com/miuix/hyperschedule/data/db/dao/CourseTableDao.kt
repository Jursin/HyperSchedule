package com.miuix.hyperschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.miuix.hyperschedule.data.model.CourseTable
import com.miuix.hyperschedule.data.model.CourseTableConfig
import kotlinx.coroutines.flow.Flow

private const val SQL_CONFIG = "SELECT * FROM course_table_config WHERE courseTableId = :courseTableId"

/** 课表元数据与课表配置访问 */
@Dao
interface CourseTableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(courseTable: CourseTable)

    /** 创建最早的课表，作为当前课表失效时的兜底 */
    @Query("SELECT * FROM course_tables ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstTableOnce(): CourseTable?

    @Query("SELECT * FROM course_tables WHERE id = :id")
    suspend fun getTableOnce(id: String): CourseTable?

    @Query("SELECT * FROM course_tables ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CourseTable>>

    @Query("SELECT COUNT(*) FROM course_tables")
    suspend fun countTables(): Int

    @Query("UPDATE course_tables SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM course_tables WHERE id = :id")
    suspend fun deleteById(id: String)

    @Upsert
    suspend fun upsertConfig(config: CourseTableConfig)

    @Query(SQL_CONFIG)
    fun observeConfig(courseTableId: String): Flow<CourseTableConfig?>

    @Query(SQL_CONFIG)
    suspend fun getConfigOnce(courseTableId: String): CourseTableConfig?
}
