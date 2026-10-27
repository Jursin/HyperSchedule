package com.miuix.hyperschedule

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.miuix.hyperschedule.data.db.AppDatabase
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.data.repository.ScheduleRepository
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
import com.miuix.hyperschedule.ui.page.HolidayPage
import com.miuix.hyperschedule.ui.page.ScheduleExportPage
import com.miuix.hyperschedule.ui.page.ScheduleImportPage
import com.miuix.hyperschedule.ui.page.SchedulePage
import com.miuix.hyperschedule.ui.page.SettingsPage
import com.miuix.hyperschedule.ui.page.ThemePage
import com.miuix.hyperschedule.ui.page.TimeTableEditPage
import com.miuix.hyperschedule.ui.page.TimeTableManagePage
import com.miuix.hyperschedule.ui.page.TodayPage
import com.miuix.hyperschedule.ui.page.UpdatePage
import com.miuix.hyperschedule.ui.rememberCourseTableData
import com.miuix.hyperschedule.ui.rememberCoursesWithWeeks
import com.miuix.hyperschedule.ui.rememberScheduleOverview
import com.miuix.hyperschedule.ui.rememberTableSummaries
import com.miuix.hyperschedule.update.checkLatestRelease
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val settingsStore by lazy { SettingsStore(applicationContext) }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = settingsStore.state
            val coroutineScope = rememberCoroutineScope()
            val repository = remember { ScheduleRepository(AppDatabase.get(applicationContext)) }
            val holidayRepository = remember { HolidayRepository(applicationContext) }
            // 课表级数据在这里常驻订阅：进入课程详情页时无需再等一次数据库
            val table = rememberCourseTableData(repository, settingsStore)
            val allCourses = rememberCoursesWithWeeks(repository, table?.tableId)
            val checkUpdate: () -> Unit = {
                coroutineScope.launch {
                    val message = runCatching {
                        withContext(Dispatchers.IO) {
                            checkLatestRelease(settings.githubProxyUrl, getAppVersion(this@MainActivity))
                        }
                    }.getOrElse { "检查更新失败：${it.message ?: "网络错误"}" }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
            LaunchedEffect(Unit) {
                if (settings.checkUpdateOnStart) {
                    delay(1500.milliseconds)
                    checkUpdate()
                }
            }
            AppTheme(settings = settings) {
                MainScaffold(
                    appBlur = settings.blur,
                    predictiveBackEnabled = settings.predictiveBack,
                    initialPage = settings.launchPage,
                    themePage = { onBack -> ThemePage(settingsStore, onBack) },
                    updatePage = { onBack -> UpdatePage(settingsStore, onBack, checkUpdate) },
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
                    scheduleImportPage = { onBack ->
                        ScheduleImportPage(
                            tables = rememberTableSummaries(repository),
                            table = table,
                            repository = repository,
                            appBlur = settings.blur,
                            onSwitchTable = { tableId ->
                                settingsStore.update { it.copy(currentTableId = tableId) }
                            },
                            onBack = onBack,
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
                    backupPage = { onBack -> BackupPage(appBlur = settings.blur, onBack = onBack) },
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
            }
        }
    }
}
