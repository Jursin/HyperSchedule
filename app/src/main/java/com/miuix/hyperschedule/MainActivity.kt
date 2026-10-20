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
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.theme.AppTheme
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.ui.SchedulePage
import com.miuix.hyperschedule.ui.SettingsPage
import com.miuix.hyperschedule.ui.TodayPage
import com.miuix.hyperschedule.ui.navigation.MainScaffold
import com.miuix.hyperschedule.ui.navigation.Page
import com.miuix.hyperschedule.ui.page.AboutPage
import com.miuix.hyperschedule.ui.page.AppearancePage
import com.miuix.hyperschedule.ui.page.CourseEditPage
import com.miuix.hyperschedule.ui.page.CourseWeeksPage
import com.miuix.hyperschedule.ui.page.UpdatePage
import com.miuix.hyperschedule.ui.rememberCourseTableData
import com.miuix.hyperschedule.ui.rememberCoursesWithWeeks
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
            // 课表级数据在这里常驻订阅：进入课程详情页时无需再等一次数据库
            val table = rememberCourseTableData(repository)
            val allCourses = rememberCoursesWithWeeks(repository, table?.tableId)
            val checkUpdate: () -> Unit = {
                coroutineScope.launch {
                    val message = runCatching {
                        withContext(Dispatchers.IO) {
                            checkLatestRelease(settings.githubProxyUrl)
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
                    appearancePage = { onBack -> AppearancePage(settingsStore, onBack) },
                    updatePage = { onBack -> UpdatePage(settingsStore, onBack, checkUpdate) },
                    aboutPage = { onBack, onLicenseClick -> AboutPage(onBack, onLicenseClick, appBlur = settings.blur) },
                    courseDetailPage = { name, week, onBack ->
                        CourseWeeksPage(name, week, table, allCourses, appBlur = settings.blur, onBack = onBack)
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
                ) { page, bottomPadding, navigate ->
                    when (page) {
                        0 ->
                            TodayPage(
                                repository = repository,
                                table = table,
                                appBlur = settings.blur,
                                onCourseClick = { name, week -> navigate(Page.CourseDetail(name, week)) },
                            )
                        1 ->
                            SchedulePage(
                                table = table,
                                allCourses = allCourses,
                                showNonCurrentWeek = settings.showNonCurrentWeek,
                                appBlur = settings.blur,
                                onEditCourse = { course ->
                                    navigate(Page.CourseEdit(course.name, focusId = course.id))
                                },
                                onAddCourse = { day, startSection, endSection ->
                                    navigate(Page.CourseEdit("", day, startSection, endSection))
                                },
                            )
                        2 -> SettingsPage(
                            store = settingsStore,
                            repository = repository,
                            table = table,
                            onAppearanceClick = { navigate(Page.Appearance) },
                            onUpdateClick = { navigate(Page.Update) },
                            onAboutClick = { navigate(Page.About) },
                            bottomPadding = bottomPadding,
                        )
                    }
                }
            }
        }
    }
}
