package com.hyperschedule.app

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hyperschedule.app.data.backup.BackupAuto
import com.hyperschedule.app.data.local.WallpaperStore
import com.hyperschedule.app.reminder.ReminderScheduler
import com.hyperschedule.app.reminder.ShizukuAccess
import com.hyperschedule.app.theme.AppTheme
import com.hyperschedule.app.ui.navigation.MainScaffold
import com.hyperschedule.app.ui.navigation.RootOverlayHost
import com.hyperschedule.app.ui.page.settings.UpdateDialogs
import com.hyperschedule.app.update.AppForeground
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuAccess.init(applicationContext)
        enableEdgeToEdge()
        val graph = (application as HyperScheduleApp).graph
        setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                val store = graph.settings
                val settings = store.state
                val updateController = graph.update
                val lifecycleOwner = LocalLifecycleOwner.current
                // 授权回来后补一次对账，已发的通知才能补全
                var hadPrivilege by remember { mutableStateOf(ShizukuAccess.hasPermission) }
                DisposableEffect(lifecycleOwner) {
                    val observer =
                        LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> {
                                    AppForeground.resumed = true
                                    updateController.onAppResumed()
                                    val granted = ShizukuAccess.hasPermission
                                    if (granted && !hadPrivilege) {
                                        ReminderScheduler.resync(applicationContext)
                                    }
                                    hadPrivilege = granted
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
                    // 这个门禁只管静默检查，手动检查不受限制
                    store.update { it.copy(lastUpdateCheck = today) }
                    updateController.checkForUpdate(
                        settings.acceptPrerelease,
                        settings.githubProxyUrl,
                        silent = true,
                    )
                }
                LaunchedEffect(Unit) {
                    AppMessages.messages.collect { text ->
                        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
                    }
                }
                // 启动后跑一次自动备份
                LaunchedEffect(Unit) {
                    BackupAuto.runIfNeeded(applicationContext, store, graph.backup)
                }
                AppTheme(settings = settings) {
                    RootOverlayHost {
                        MainScaffold(
                            appBlur = settings.blur,
                            predictiveBackEnabled = settings.predictiveBack,
                            initialPage = settings.launchPage,
                            wallpaper = settings.wallpaper,
                            wallpaperBlur = settings.wallpaperBlur,
                            // 裁剪页存好图后把设置指向新文件，这时才能删旧图
                            onWallpaperSaved = { path ->
                                store.update { it.copy(wallpaper = path) }
                                WallpaperStore.prune(applicationContext, path)
                            },
                        )
                    }
                    UpdateDialogs(updateController, settings.blur)
                }
            }
        }
    }
}
