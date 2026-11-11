package com.hyperschedule.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hyperschedule.app.data.WallpaperStore
import com.hyperschedule.app.data.backup.BackupAuto
import com.hyperschedule.app.data.backup.BackupRepository
import com.hyperschedule.app.data.db.AppDatabase
import com.hyperschedule.app.data.importer.edu.EduRepoStore
import com.hyperschedule.app.data.importer.edu.EduScriptRepository
import com.hyperschedule.app.data.importer.edu.SchoolRepository
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.data.repository.ScheduleRepository
import com.hyperschedule.app.reminder.ReminderScheduler
import com.hyperschedule.app.reminder.ShizukuAccess
import com.hyperschedule.app.theme.AppTheme
import com.hyperschedule.app.theme.SettingsStore
import com.hyperschedule.app.ui.navigation.MainScaffold
import com.hyperschedule.app.ui.navigation.Page
import com.hyperschedule.app.ui.page.AboutPage
import com.hyperschedule.app.ui.page.BackupPage
import com.hyperschedule.app.ui.page.ComboEditPage
import com.hyperschedule.app.ui.page.CourseEditPage
import com.hyperschedule.app.ui.page.CourseManagePage
import com.hyperschedule.app.ui.page.CourseReminderPage
import com.hyperschedule.app.ui.page.CourseTableManagePage
import com.hyperschedule.app.ui.page.CourseWeeksPage
import com.hyperschedule.app.ui.page.EduImportPage
import com.hyperschedule.app.ui.page.EduImportWebPage
import com.hyperschedule.app.ui.page.HolidayPage
import com.hyperschedule.app.ui.page.ScheduleExportPage
import com.hyperschedule.app.ui.page.ScheduleImportPage
import com.hyperschedule.app.ui.page.SchedulePage
import com.hyperschedule.app.ui.page.SettingsPage
import com.hyperschedule.app.ui.page.ThemePage
import com.hyperschedule.app.ui.page.TimeTableEditPage
import com.hyperschedule.app.ui.page.TimeTableManagePage
import com.hyperschedule.app.ui.page.TodayPage
import com.hyperschedule.app.ui.page.UpdateDialogs
import com.hyperschedule.app.ui.page.UpdateLogPage
import com.hyperschedule.app.ui.page.UpdatePage
import com.hyperschedule.app.ui.page.WallpaperCropPage
import com.hyperschedule.app.ui.page.WearPushPage
import com.hyperschedule.app.ui.rememberCourseTableData
import com.hyperschedule.app.ui.rememberCoursesWithWeeks
import com.hyperschedule.app.ui.rememberScheduleOverview
import com.hyperschedule.app.ui.rememberTableSummaries
import com.hyperschedule.app.update.AppForeground
import com.hyperschedule.app.update.UpdateController
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val settingsStore by lazy { SettingsStore.get(applicationContext) }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuAccess.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val settings = settingsStore.state
            val coroutineScope = rememberCoroutineScope()
            val repository = remember { ScheduleRepository(AppDatabase.get(applicationContext)) }
            val holidayRepository = remember { HolidayRepository.get(applicationContext) }
            val eduRepoStore = remember { EduRepoStore.get(applicationContext) }
            val backupRepository = remember { BackupRepository(applicationContext, AppDatabase.get(applicationContext)) }
            val schoolRepository = remember { SchoolRepository(applicationContext, eduRepoStore) }
            val scriptRepository = remember { EduScriptRepository(applicationContext, eduRepoStore) }
            // 课表级数据在这里常驻订阅，进入课程详情页时无需再等一次数据库
            val table = rememberCourseTableData(repository, settingsStore)
            val allCourses = rememberCoursesWithWeeks(repository, table?.tableId)
            // 提醒重排的全部触发点，启动、改提醒设置、换课表、课程增删改（allCourses 换代）
            LaunchedEffect(settings.reminder, settings.currentTableId, allCourses) {
                ReminderScheduler.resync(applicationContext)
            }
            val updateController = UpdateController.of(applicationContext)
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer =
                    LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                AppForeground.resumed = true
                                updateController.onAppResumed()
                            }

                            Lifecycle.Event.ON_STOP -> AppForeground.resumed = false
                            else -> Unit
                        }
                    }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(Unit) {
                val today = LocalDate.now().toString()
                if (!settings.checkUpdateOnStart || settings.lastUpdateCheck == today) return@LaunchedEffect
                delay(1500.milliseconds)
                // 静默检查每天最多一次，手动检查不受限制
                settingsStore.update { it.copy(lastUpdateCheck = today) }
                updateController.checkForUpdate(
                    settings.updateSource,
                    settings.acceptPrerelease,
                    settings.githubProxyUrl,
                    silent = true,
                )
            }
            // 启动时自动备份，内容没变就不写文件
            LaunchedEffect(Unit) {
                BackupAuto.runIfNeeded(applicationContext, settingsStore, backupRepository)
            }
            AppTheme(settings = settings) {
                MainScaffold(
                    appBlur = settings.blur,
                    predictiveBackEnabled = settings.predictiveBack,
                    initialPage = settings.launchPage,
                    wallpaper = settings.wallpaper,
                    wallpaperBlur = settings.wallpaperBlur,
                    // 裁剪页存好图后把设置指向新文件，这时才能删旧图
                    onWallpaperSaved = { path ->
                        settingsStore.update { it.copy(wallpaper = path) }
                        WallpaperStore.prune(applicationContext, path)
                    },
                    wallpaperCropPage = { uri, onSaved, onBack ->
                        WallpaperCropPage(
                            uri = uri,
                            appBlur = settings.blur,
                            onSaved = onSaved,
                            onBack = onBack,
                        )
                    },
                    settingsPage = { navigate, onBack ->
                        SettingsPage(
                            store = settingsStore,
                            repository = repository,
                            table = table,
                            onAppearanceClick = { navigate(Page.Appearance) },
                            onUpdateClick = { navigate(Page.Update) },
                            onAboutClick = { navigate(Page.About) },
                            onManageCourses = { navigate(Page.CourseManage) },
                            onManageTables = { navigate(Page.CourseTableManage) },
                            onManageTimeTables = { navigate(Page.TimeTableManage) },
                            onOpenImport = { navigate(Page.ScheduleImport) },
                            onOpenExport = { navigate(Page.ScheduleExport) },
                            onOpenBackup = { navigate(Page.Backup) },
                            onOpenHoliday = { navigate(Page.Holiday) },
                            onOpenReminder = { navigate(Page.Reminder) },
                            onOpenWearPush = { navigate(Page.WearPush) },
                            onBack = onBack,
                        )
                    },
                    themePage = { onBack -> ThemePage(settingsStore, onBack) },
                    updatePage = { onOpenLog, onBack ->
                        UpdatePage(settingsStore, updateController, onOpenLog, onBack)
                    },
                    updateLogPage = { onBack ->
                        UpdateLogPage(appBlur = settings.blur, onBack = onBack)
                    },
                    aboutPage = { onBack, onLicenseClick -> AboutPage(onBack, onLicenseClick, appBlur = settings.blur) },
                    courseDetailPage = { name, week, onBack ->
                        CourseWeeksPage(
                            courseName = name,
                            week = week,
                            table = table,
                            courses = allCourses,
                            holidayRepository = holidayRepository,
                            appBlur = settings.blur,
                            onBack = onBack,
                        )
                    },
                    courseManagePage = { navigate, onBack ->
                        CourseManagePage(
                            courses = allCourses,
                            appBlur = settings.blur,
                            onEditCourse = { name -> navigate(Page.CourseEdit(name)) },
                            onAddCourse = { navigate(Page.CourseEdit("")) },
                            onDeleteCourses = { ids ->
                                coroutineScope.launch { repository.deleteCourses(ids) }
                            },
                            onBack = onBack,
                        )
                    },
                    courseEditPage = { name, day, startSection, endSection, focusId, onBack ->
                        CourseEditPage(
                            initialName = name,
                            prefillDay = day,
                            prefillStart = startSection,
                            prefillEnd = endSection,
                            focusId = focusId,
                            table = table,
                            allCourses = allCourses,
                            repository = repository,
                            appBlur = settings.blur,
                            onBack = onBack,
                        )
                    },
                    timeTableManagePage = { navigate, onBack ->
                        val overview = rememberScheduleOverview(repository, table?.tableId)
                        TimeTableManagePage(
                            overview = overview,
                            appBlur = settings.blur,
                            onBind = { targetType, targetId ->
                                val tableId = table?.tableId
                                if (tableId != null) {
                                    coroutineScope.launch { repository.bind(tableId, targetType, targetId) }
                                }
                            },
                            onOpenPlan = { planId -> navigate(Page.TimeTableEdit(planId)) },
                            onOpenCombo = { comboId -> navigate(Page.ComboEdit(comboId)) },
                            onCreatePublic = { navigate(Page.TimeTableEdit()) },
                            onCreateCombo = { navigate(Page.ComboEdit()) },
                            onDeletePlan = { planId ->
                                coroutineScope.launch { repository.deletePublicSchedule(planId) }
                            },
                            onDeleteCombo = { comboId ->
                                coroutineScope.launch { repository.deleteCombo(comboId) }
                            },
                            onBack = onBack,
                        )
                    },
                    timeTableEditPage = { planId, onBack ->
                        TimeTableEditPage(
                            planId = planId,
                            repository = repository,
                            tableId = table?.tableId.orEmpty(),
                            appBlur = settings.blur,
                            onBack = onBack,
                        )
                    },
                    comboEditPage = { comboId, onBack ->
                        ComboEditPage(
                            comboId = comboId,
                            repository = repository,
                            appBlur = settings.blur,
                            onBack = onBack,
                        )
                    },
                    courseTableManagePage = { onBack ->
                        CourseTableManagePage(
                            tables = rememberTableSummaries(repository),
                            currentTableId = settingsStore.state.currentTableId,
                            appBlur = settings.blur,
                            onSwitch = { tableId ->
                                settingsStore.update { it.copy(currentTableId = tableId) }
                            },
                            onCreate = { name ->
                                coroutineScope.launch { repository.createTable(name) }
                            },
                            onRename = { tableId, name ->
                                coroutineScope.launch { repository.renameTable(tableId, name) }
                            },
                            onDelete = { tableId ->
                                coroutineScope.launch {
                                    val fallback = repository.deleteTable(tableId)
                                    if (settingsStore.state.currentTableId == tableId) {
                                        settingsStore.update { it.copy(currentTableId = fallback) }
                                    }
                                }
                            },
                            onBack = onBack,
                        )
                    },
                    scheduleImportPage = { navigate, onBack ->
                        ScheduleImportPage(
                            tables = rememberTableSummaries(repository),
                            table = table,
                            repository = repository,
                            appBlur = settings.blur,
                            onSwitchTable = { tableId ->
                                settingsStore.update { it.copy(currentTableId = tableId) }
                            },
                            onOpenEduImport = { navigate(Page.EduImport) },
                            onBack = onBack,
                        )
                    },
                    eduImportPage = { navigate, onBack ->
                        EduImportPage(
                            repository = schoolRepository,
                            repoStore = eduRepoStore,
                            appBlur = settings.blur,
                            onOpenAdapter = { school, adapter, url ->
                                navigate(Page.EduImportWeb(school.id, adapter.id, url))
                            },
                            onBack = onBack,
                        )
                    },
                    eduImportWebPage = { schoolId, adapterId, url, onBack, onImported ->
                        EduImportWebPage(
                            schoolRepository = schoolRepository,
                            scriptRepository = scriptRepository,
                            repository = repository,
                            schoolId = schoolId,
                            adapterId = adapterId,
                            startUrl = url,
                            tables = rememberTableSummaries(repository),
                            currentTableId = table?.tableId,
                            appBlur = settings.blur,
                            onBack = onBack,
                            onImported = onImported,
                            onSwitchTable = { tableId ->
                                settingsStore.update { it.copy(currentTableId = tableId) }
                            },
                        )
                    },
                    scheduleExportPage = { onBack ->
                        ScheduleExportPage(
                            tables = rememberTableSummaries(repository),
                            repository = repository,
                            holidayRepository = holidayRepository,
                            currentTableId = settingsStore.state.currentTableId,
                            reminder = settings.reminder,
                            appBlur = settings.blur,
                            onBack = onBack,
                        )
                    },
                    backupPage = { onBack -> BackupPage(settingsStore, appBlur = settings.blur, onBack = onBack) },
                    holidayPage = { onBack ->
                        HolidayPage(
                            repository = holidayRepository,
                            table = table,
                            appBlur = settings.blur,
                            onBack = onBack,
                        )
                    },
                    reminderPage = { onBack ->
                        CourseReminderPage(
                            store = settingsStore,
                            appBlur = settings.blur,
                            onBack = onBack,
                        )
                    },
                    wearPushPage = { onBack ->
                        WearPushPage(
                            tables = rememberTableSummaries(repository),
                            currentTableId = settingsStore.state.currentTableId,
                            setting = settings.wearPush,
                            onSettingChange = { next -> settingsStore.update { it.copy(wearPush = next) } },
                            repository = repository,
                            holidayRepository = holidayRepository,
                            appBlur = settings.blur,
                            onBack = onBack,
                        )
                    },
                ) { page, navigate ->
                    // 两个 tab 共用同一张壁纸，选图统一进裁剪页，拖动模糊强度只改内存
                    val pickWallpaper: (Uri) -> Unit = { uri -> navigate(Page.WallpaperCrop(uri.toString())) }
                    val clearWallpaper: () -> Unit = {
                        settingsStore.update { it.copy(wallpaper = "") }
                        WallpaperStore.clear(applicationContext)
                    }
                    val previewWallpaperBlur: (Int) -> Unit = { value ->
                        settingsStore.preview { it.copy(wallpaperBlur = value) }
                    }
                    // 拖动只改内存，抬手才落盘
                    val commitSettings: () -> Unit = { settingsStore.save() }
                    when (page) {
                        0 ->
                            TodayPage(
                                repository = repository,
                                table = table,
                                style = settings.todayStyle,
                                onStyleChange = { newStyle -> settingsStore.update { it.copy(todayStyle = newStyle) } },
                                reminder = settings.reminder,
                                holidayRepository = holidayRepository,
                                appBlur = settings.blur,
                                wallpaper = settings.wallpaper,
                                wallpaperBlur = settings.wallpaperBlur,
                                onWallpaperPick = pickWallpaper,
                                onWallpaperClear = clearWallpaper,
                                onWallpaperBlurChange = previewWallpaperBlur,
                                onWallpaperBlurCommit = commitSettings,
                                onCourseClick = { name, week -> navigate(Page.CourseDetail(name, week)) },
                            )
                        1 ->
                            SchedulePage(
                                table = table,
                                allCourses = allCourses,
                                showNonCurrentWeek = settings.showNonCurrentWeek,
                                style = settings.style,
                                onStyleChange = { newStyle -> settingsStore.preview { it.copy(style = newStyle) } },
                                onStyleCommit = commitSettings,
                                holidayRepository = holidayRepository,
                                appBlur = settings.blur,
                                wallpaper = settings.wallpaper,
                                wallpaperBlur = settings.wallpaperBlur,
                                onWallpaperPick = pickWallpaper,
                                onWallpaperClear = clearWallpaper,
                                onWallpaperBlurChange = previewWallpaperBlur,
                                onWallpaperBlurCommit = commitSettings,
                                onEditCourse = { course ->
                                    navigate(Page.CourseEdit(course.name, focusId = course.id))
                                },
                                onAddCourse = { day, startSection, endSection ->
                                    navigate(Page.CourseEdit("", day, startSection, endSection))
                                },
                                onManageCourses = { navigate(Page.CourseManage) },
                                onManageTables = { navigate(Page.CourseTableManage) },
                                onSaveCourses = { deletedIds, rows ->
                                    coroutineScope.launch { repository.saveCourseEdits(deletedIds, rows) }
                                },
                            )
                    }
                }
                UpdateDialogs(updateController, settings.blur)
            }
        }
    }
}
