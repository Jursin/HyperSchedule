package com.miuix.hyperschedule

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.miuix.hyperschedule.theme.AppTheme
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.ui.SchedulePage
import com.miuix.hyperschedule.ui.SettingsPage
import com.miuix.hyperschedule.ui.TodayPage
import com.miuix.hyperschedule.ui.page.AboutPage
import com.miuix.hyperschedule.ui.page.AppearancePage
import com.miuix.hyperschedule.ui.page.UpdatePage
import com.miuix.hyperschedule.ui.navigation.MainScaffold
import com.miuix.hyperschedule.ui.navigation.Page
import com.miuix.hyperschedule.update.checkLatestRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    private val settingsStore by lazy { SettingsStore(applicationContext) }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = settingsStore.state
            val coroutineScope = rememberCoroutineScope()
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
                ) { page, bottomPadding, navigate ->
                    when (page) {
                        0 -> TodayPage(appBlur = settings.blur)
                        1 -> SchedulePage(store = settingsStore, appBlur = settings.blur)
                        2 -> SettingsPage(
                            store = settingsStore,
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
