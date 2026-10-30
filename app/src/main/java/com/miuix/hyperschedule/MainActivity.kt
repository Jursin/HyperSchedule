package com.miuix.hyperschedule

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
import com.miuix.hyperschedule.data.backup.BackupAuto
import com.miuix.hyperschedule.data.backup.BackupRepository
import com.miuix.hyperschedule.data.db.AppDatabase
import com.miuix.hyperschedule.data.importer.edu.EduRepoStore
import com.miuix.hyperschedule.data.importer.edu.EduScriptRepository
import com.miuix.hyperschedule.data.importer.edu.SchoolRepository
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.reminder.ReminderScheduler
import com.miuix.hyperschedule.theme.AppTheme
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.ui.navigation.MainScaffold
import com.miuix.hyperschedule.ui.navigation.Page
import com.miuix.hyperschedule.ui.page.AboutPage
import com.miuix.hyperschedule.ui.page.BackupPage
import com.miuix.hyperschedule.ui.page.ComboEditPage
import com.miuix.hyperschedule.ui.page.CourseEditPage
import com.miuix.hyperschedule.ui.page.CourseManagePage
import com.miuix.hyperschedule.ui.page.CourseReminderPage
import com.miuix.hyperschedule.ui.page.CourseTableManagePage
import com.miuix.hyperschedule.ui.page.CourseWeeksPage
import com.miuix.hyperschedule.ui.page.EduImportPage
import com.miuix.hyperschedule.ui.page.EduImportWebPage
import com.miuix.hyperschedule.ui.page.HolidayPage
import com.miuix.hyperschedule.ui.page.ScheduleExportPage
import com.miuix.hyperschedule.ui.page.ScheduleImportPage
import com.miuix.hyperschedule.ui.page.SchedulePage
import com.miuix.hyperschedule.ui.page.SettingsPage
import com.miuix.hyperschedule.ui.page.ThemePage
import com.miuix.hyperschedule.ui.page.TimeTableEditPage
import com.miuix.hyperschedule.ui.page.TimeTableManagePage
import com.miuix.hyperschedule.ui.page.TodayPage
import com.miuix.hyperschedule.ui.page.UpdateDialogs
import com.miuix.hyperschedule.ui.page.UpdatePage
import com.miuix.hyperschedule.ui.rememberCourseTableData
import com.miuix.hyperschedule.ui.rememberCoursesWithWeeks
import com.miuix.hyperschedule.ui.rememberScheduleOverview
import com.miuix.hyperschedule.ui.rememberTableSummaries
import com.miuix.hyperschedule.update.AppForeground
import com.miuix.hyperschedule.update.UpdateController
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val settingsStore by lazy { SettingsStore.get(applicationContext) }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            // 课表级数据在这里常驻订阅：进入课程详情页时无需再等一次数据库
            val table = rememberCourseTableData(repository, settingsStore)
            val allCourses = rememberCoursesWithWeeks(repository, table?.tableId)
            // 提醒重排的全部触发点：启动、改提醒设置、换课表、课程增删改（allCourses 换代）
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
                // 静默检查每天最多一次；手动检查不受限制
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
                    themePage = { onBack -> ThemePage(settingsStore, onBack) },
                    updatePage = { onBack -> UpdatePage(settingsStore, updateController, onBack) },
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
                            onOpenAdapter = { school, adapter ->
                                navigate(Page.EduImportWeb(school.id, adapter.id))
                            },
                            onBack = onBack,
                        )
                    },
                    eduImportWebPage = { schoolId, adapterId, onBack, onImported ->
                        EduImportWebPage(
                            schoolRepository = schoolRepository,
                            scriptRepository = scriptRepository,
                            repository = repository,
                            schoolId = schoolId,
                            adapterId = adapterId,
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
                ) { page, navigate ->
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
                                onCourseClick = { name, week -> navigate(Page.CourseDetail(name, week)) },
                            )
                        1 ->
                            SchedulePage(
                                table = table,
                                allCourses = allCourses,
                                showNonCurrentWeek = settings.showNonCurrentWeek,
                                style = settings.style,
                                onStyleChange = { newStyle -> settingsStore.preview { it.copy(style = newStyle) } },
                                onStyleCommit = { settingsStore.save() },
                                holidayRepository = holidayRepository,
                                appBlur = settings.blur,
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
                        2 -> SettingsPage(
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
                        )
                    }
                }
                UpdateDialogs(updateController, settings.blur)
            }
        }
    }
}
