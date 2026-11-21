package top.jursin.hyperschedule

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import top.jursin.hyperschedule.data.backup.BackupRepository
import top.jursin.hyperschedule.data.db.AppDatabase
import top.jursin.hyperschedule.data.importer.edu.EduRepoStore
import top.jursin.hyperschedule.data.importer.edu.EduScriptRepository
import top.jursin.hyperschedule.data.importer.edu.SchoolRepository
import top.jursin.hyperschedule.data.local.SettingsStore
import top.jursin.hyperschedule.data.repository.HolidayRepository
import top.jursin.hyperschedule.data.repository.ScheduleRepository
import top.jursin.hyperschedule.update.UpdateController

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
