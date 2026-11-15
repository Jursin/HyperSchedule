package com.hyperschedule.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.hyperschedule.app.data.backup.BackupRepository
import com.hyperschedule.app.data.db.AppDatabase
import com.hyperschedule.app.data.importer.edu.EduRepoStore
import com.hyperschedule.app.data.importer.edu.EduScriptRepository
import com.hyperschedule.app.data.importer.edu.SchoolRepository
import com.hyperschedule.app.data.local.SettingsStore
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.data.repository.ScheduleRepository
import com.hyperschedule.app.update.UpdateController

/** 进程内共享的仓库实例，只在启动时构造一次 */
class AppGraph(base: Context) {
    /** 一律用 applicationContext，避免长期持有 Activity */
    val context: Context = base.applicationContext

    val settings: SettingsStore = SettingsStore.get(context)
    private val database: AppDatabase = AppDatabase.get(context)
    val schedule: ScheduleRepository = ScheduleRepository(database)
    val holiday: HolidayRepository = HolidayRepository.get(context)
    val eduRepo: EduRepoStore = EduRepoStore.get(context)
    val backup = BackupRepository(context, database)
    val school = SchoolRepository(context, eduRepo)
    val script = EduScriptRepository(context, eduRepo)
    val update: UpdateController = UpdateController.of(context)
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph 未注入") }
