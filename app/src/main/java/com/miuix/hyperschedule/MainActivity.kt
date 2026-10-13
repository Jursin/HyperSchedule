package com.miuix.hyperschedule

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.miuix.hyperschedule.theme.AppTheme
import com.miuix.hyperschedule.ui.HomePage
import com.miuix.hyperschedule.ui.SettingsPage
import com.miuix.hyperschedule.ui.navigation.MainScaffold
import com.miuix.hyperschedule.ui.navigation.Page
import com.miuix.hyperschedule.update.checkLatestRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val settingsStore by lazy { com.miuix.hyperschedule.theme.SettingsStore(applicationContext) }

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
                    delay(1500)
                    checkUpdate()
                }
            }
            AppTheme(settings = settings) {
                MainScaffold(
                    floatingNav = settings.floatingNav,
                    appBlur = settings.blur,
                    predictiveBackEnabled = settings.predictiveBack,
                ) { page, bottomPadding, navigate ->
                    when (page) {
                        0 -> HomePage()
                        else -> SettingsPage(
                            store = settingsStore,
                            onLicenseClick = { navigate(Page.License) },
                            onCheckUpdate = checkUpdate,
                            bottomPadding = bottomPadding,
                        )
                    }
                }
            }
        }
    }
}
