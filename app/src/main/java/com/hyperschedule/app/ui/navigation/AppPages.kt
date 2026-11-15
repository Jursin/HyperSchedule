package com.hyperschedule.app.ui.navigation

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.hyperschedule.app.LocalAppGraph
import com.hyperschedule.app.data.db.entity.CourseWithWeeks
import com.hyperschedule.app.data.local.WallpaperStore
import com.hyperschedule.app.data.repository.ScheduleRepository
import com.hyperschedule.app.data.repository.deleteCombo
import com.hyperschedule.app.data.repository.deletePublicSchedule
import com.hyperschedule.app.ui.page.course.CourseEditPage
import com.hyperschedule.app.ui.page.course.CourseManagePage
import com.hyperschedule.app.ui.page.course.CourseReminderPage
import com.hyperschedule.app.ui.page.course.CourseWeeksPage
import com.hyperschedule.app.ui.page.schedule.SchedulePage
import com.hyperschedule.app.ui.page.settings.AboutPage
import com.hyperschedule.app.ui.page.settings.BackupPage
import com.hyperschedule.app.ui.page.settings.HolidayPage
import com.hyperschedule.app.ui.page.settings.LicensePage
import com.hyperschedule.app.ui.page.settings.SettingsPage
import com.hyperschedule.app.ui.page.settings.ThemePage
import com.hyperschedule.app.ui.page.settings.UpdateLogPage
import com.hyperschedule.app.ui.page.settings.UpdatePage
import com.hyperschedule.app.ui.page.settings.WallpaperCropPage
import com.hyperschedule.app.ui.page.settings.WearPushPage
import com.hyperschedule.app.ui.page.table.ComboEditPage
import com.hyperschedule.app.ui.page.table.CourseTableManagePage
import com.hyperschedule.app.ui.page.table.TimeTableEditPage
import com.hyperschedule.app.ui.page.table.TimeTableManagePage
import com.hyperschedule.app.ui.page.today.TodayPage
import com.hyperschedule.app.ui.page.transfer.EduImportPage
import com.hyperschedule.app.ui.page.transfer.EduImportWebPage
import com.hyperschedule.app.ui.page.transfer.ScheduleTransferPage
import com.hyperschedule.app.ui.state.CourseTableData
import com.hyperschedule.app.ui.state.rememberScheduleOverview
import com.hyperschedule.app.ui.state.rememberTableSummaries
import kotlinx.coroutines.launch

/** 两个 Tab 的页面内容，共用壁纸与外观设置 */
@Composable
internal fun HomeTabContent(
    page: Int,
    navigate: (Page) -> Unit,
    table: CourseTableData?,
    allCourses: List<CourseWithWeeks>,
) {
    val graph = LocalAppGraph.current
    val store = graph.settings
    val settings = store.state
    val scope = rememberCoroutineScope()

    // 选图统一进裁剪页
    val pickWallpaper: (Uri) -> Unit = { uri -> navigate(Page.WallpaperCrop(uri.toString())) }
    val clearWallpaper: () -> Unit = {
        store.update { it.copy(wallpaper = "") }
        WallpaperStore.clear(graph.context)
    }
    val previewWallpaperBlur: (Int) -> Unit = { value -> store.preview { it.copy(wallpaperBlur = value) } }
    // 拖动只改内存，抬手才落盘
    val commitSettings: () -> Unit = { store.save() }

    when (page) {
        0 ->
            TodayPage(
                repository = graph.schedule,
                table = table,
                style = settings.todayStyle,
                onStyleChange = { newStyle -> store.update { it.copy(todayStyle = newStyle) } },
                reminder = settings.reminder,
                holidayRepository = graph.holiday,
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
                onStyleChange = { newStyle -> store.preview { it.copy(style = newStyle) } },
                onStyleCommit = commitSettings,
                holidayRepository = graph.holiday,
                appBlur = settings.blur,
                wallpaper = settings.wallpaper,
                wallpaperBlur = settings.wallpaperBlur,
                onWallpaperPick = pickWallpaper,
                onWallpaperClear = clearWallpaper,
                onWallpaperBlurChange = previewWallpaperBlur,
                onWallpaperBlurCommit = commitSettings,
                onEditCourse = { course -> navigate(Page.CourseEdit(course.name, focusId = course.id)) },
                onAddCourse = { day, startSection, endSection ->
                    navigate(Page.CourseEdit("", day, startSection, endSection))
                },
                onManageCourses = { navigate(Page.CourseManage) },
                onManageTables = { navigate(Page.CourseTableManage) },
                onSaveCourses = { deletedIds, rows -> scope.launch { graph.schedule.saveCourseEdits(deletedIds, rows) } },
            )
    }
}

/** 除首页外的二级页；关于页要 RuntimeShader，整块要求 API 33 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun SecondaryPage(
    key: Page,
    navigate: (Page) -> Unit,
    onBack: () -> Unit,
    backToScheduleTransfer: () -> Unit,
    onWallpaperSaved: (String) -> Unit,
    table: CourseTableData?,
    allCourses: List<CourseWithWeeks>,
) {
    val graph = LocalAppGraph.current
    val store = graph.settings
    val settings = store.state
    val scope = rememberCoroutineScope()
    val repository: ScheduleRepository = graph.schedule

    fun switchTable(tableId: String) = store.update { it.copy(currentTableId = tableId) }

    when (key) {
        is Page.Settings ->
            SettingsPage(
                store = store,
                repository = repository,
                table = table,
                onAppearanceClick = { navigate(Page.Appearance) },
                onUpdateClick = { navigate(Page.Update) },
                onAboutClick = { navigate(Page.About) },
                onManageCourses = { navigate(Page.CourseManage) },
                onManageTables = { navigate(Page.CourseTableManage) },
                onManageTimeTables = { navigate(Page.TimeTableManage) },
                onOpenTransfer = { navigate(Page.ScheduleTransfer) },
                onOpenBackup = { navigate(Page.Backup) },
                onOpenHoliday = { navigate(Page.Holiday) },
                onOpenReminder = { navigate(Page.Reminder) },
                onOpenWearPush = { navigate(Page.WearPush) },
                onBack = onBack,
            )
        is Page.Appearance -> ThemePage(store, onBack)
        is Page.Update -> UpdatePage(store, graph.update, { navigate(Page.UpdateLog) }, onBack)
        is Page.UpdateLog -> UpdateLogPage(appBlur = settings.blur, onBack = onBack)
        is Page.About -> AboutPage(onBack, { navigate(Page.License) }, appBlur = settings.blur)
        is Page.License -> LicensePage(appBlur = settings.blur, onBack = onBack)
        is Page.CourseDetail ->
            CourseWeeksPage(
                courseName = key.name,
                week = key.week,
                table = table,
                courses = allCourses,
                holidayRepository = graph.holiday,
                appBlur = settings.blur,
                onBack = onBack,
            )
        is Page.CourseManage ->
            CourseManagePage(
                courses = allCourses,
                appBlur = settings.blur,
                onEditCourse = { name -> navigate(Page.CourseEdit(name)) },
                onAddCourse = { navigate(Page.CourseEdit("")) },
                onDeleteCourses = { ids -> scope.launch { repository.deleteCourses(ids) } },
                onBack = onBack,
            )
        is Page.CourseEdit ->
            CourseEditPage(
                initialName = key.name,
                prefillDay = key.day,
                prefillStart = key.startSection,
                prefillEnd = key.endSection,
                focusId = key.focusId,
                table = table,
                allCourses = allCourses,
                repository = repository,
                appBlur = settings.blur,
                onBack = onBack,
            )
        is Page.TimeTableManage -> {
            val overview = rememberScheduleOverview(repository, table?.tableId)
            TimeTableManagePage(
                overview = overview,
                appBlur = settings.blur,
                onBind = { targetType, targetId ->
                    table?.tableId?.let { tableId -> scope.launch { repository.bind(tableId, targetType, targetId) } }
                },
                onOpenPlan = { planId -> navigate(Page.TimeTableEdit(planId)) },
                onOpenCombo = { comboId -> navigate(Page.ComboEdit(comboId)) },
                onCreatePublic = { navigate(Page.TimeTableEdit()) },
                onCreateCombo = { navigate(Page.ComboEdit()) },
                onDeletePlan = { planId -> scope.launch { repository.deletePublicSchedule(planId) } },
                onDeleteCombo = { comboId -> scope.launch { repository.deleteCombo(comboId) } },
                onBack = onBack,
            )
        }
        is Page.TimeTableEdit ->
            TimeTableEditPage(
                planId = key.planId,
                repository = repository,
                tableId = table?.tableId.orEmpty(),
                appBlur = settings.blur,
                onBack = onBack,
            )
        is Page.ComboEdit ->
            ComboEditPage(comboId = key.comboId, repository = repository, appBlur = settings.blur, onBack = onBack)
        is Page.CourseTableManage ->
            CourseTableManagePage(
                tables = rememberTableSummaries(repository),
                currentTableId = store.state.currentTableId,
                appBlur = settings.blur,
                onSwitch = ::switchTable,
                onCreate = { name -> scope.launch { repository.createTable(name) } },
                onRename = { tableId, name -> scope.launch { repository.renameTable(tableId, name) } },
                onDelete = { tableId ->
                    scope.launch {
                        val fallback = repository.deleteTable(tableId)
                        if (store.state.currentTableId == tableId) {
                            store.update { it.copy(currentTableId = fallback) }
                        }
                    }
                },
                onBack = onBack,
            )
        is Page.ScheduleTransfer ->
            ScheduleTransferPage(
                tables = rememberTableSummaries(repository),
                table = table,
                repository = repository,
                holidayRepository = graph.holiday,
                reminder = settings.reminder,
                currentTableId = store.state.currentTableId,
                appBlur = settings.blur,
                onSwitchTable = ::switchTable,
                onOpenEduImport = { navigate(Page.EduImport) },
                onBack = onBack,
            )
        is Page.EduImport ->
            EduImportPage(
                repository = graph.school,
                repoStore = graph.eduRepo,
                appBlur = settings.blur,
                onOpenAdapter = { school, adapter, url -> navigate(Page.EduImportWeb(school.id, adapter.id, url)) },
                onBack = onBack,
            )
        is Page.EduImportWeb ->
            EduImportWebPage(
                schoolRepository = graph.school,
                scriptRepository = graph.script,
                repository = repository,
                schoolId = key.schoolId,
                adapterId = key.adapterId,
                startUrl = key.url,
                tables = rememberTableSummaries(repository),
                currentTableId = table?.tableId,
                appBlur = settings.blur,
                onBack = onBack,
                onImported = backToScheduleTransfer,
                onSwitchTable = ::switchTable,
            )
        is Page.Backup -> BackupPage(store, graph.backup, appBlur = settings.blur, onBack = onBack)
        is Page.Holiday -> HolidayPage(repository = graph.holiday, appBlur = settings.blur, onBack = onBack)
        is Page.Reminder -> CourseReminderPage(store = store, appBlur = settings.blur, onBack = onBack)
        is Page.WearPush ->
            WearPushPage(
                tables = rememberTableSummaries(repository),
                currentTableId = store.state.currentTableId,
                setting = settings.wearPush,
                onSettingChange = { next -> store.update { it.copy(wearPush = next) } },
                reminder = settings.reminder,
                repository = repository,
                holidayRepository = graph.holiday,
                appBlur = settings.blur,
                onBack = onBack,
            )
        is Page.WallpaperCrop ->
            WallpaperCropPage(
                uri = key.uri,
                appBlur = settings.blur,
                onSaved = { path ->
                    onWallpaperSaved(path)
                    onBack()
                },
                onBack = onBack,
            )
        is Page.Home -> Unit
    }
}
