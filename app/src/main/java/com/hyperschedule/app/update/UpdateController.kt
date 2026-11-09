package com.hyperschedule.app.update

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hyperschedule.app.getAppVersion
import com.hyperschedule.app.reminder.ShizukuAccess
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateDialogState {
    data object None : UpdateDialogState

    data class Available(
        val version: String,
        val notes: List<ChangelogEntry>,
    ) : UpdateDialogState

    data class Downloading(val progress: Float) : UpdateDialogState

    /** 后台下载完成，等回到前台再拉起系统安装器 */
    data class ReadyToInstall(val file: File) : UpdateDialogState
}

/** App 是否在前台，后台拉不起安装器 */
internal object AppForeground {
    @Volatile var resumed: Boolean = false
}

/** 检查、下载与安装，进程级单例，Activity 重建不会中断下载 */
class UpdateController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var dialog by mutableStateOf<UpdateDialogState>(UpdateDialogState.None)
        private set

    var checking by mutableStateOf(false)
        private set

    private var downloading = false
    private var progressDismissed = false
    private var lastProgress = 0f
    private var pending: ReleaseAsset? = null
    private var pendingVersion = ""
    private var job: Job? = null

    init {
        // 通知栏的「取消下载」按钮回到这里
        UpdateForegroundService.onCancel = ::stopUpdate
        // 上一次更新装完后残留的安装包在这里回收
        scope.launch(Dispatchers.IO) { UpdateApk.prune(context) }
    }

    fun checkForUpdate(
        source: UpdateSource,
        acceptPrerelease: Boolean,
        proxy: String,
        silent: Boolean = false,
    ) {
        // 下载期间再点检查就回到进度弹窗
        if (downloading) {
            reopenProgress()
            return
        }
        if (checking) return
        checking = true
        if (!silent) toast("正在检查更新")
        scope.launch {
            try {
                val currentVersion = getAppVersion(context)
                val release =
                    withContext(Dispatchers.IO) {
                        AppUpdate.fetchRelease(source, acceptPrerelease, proxy, currentVersion)
                    }
                if (AppUpdate.compareVersions(release.version, currentVersion) <= 0) {
                    if (!silent) toast("已是最新版")
                    return@launch
                }
                val asset = AppUpdate.selectAsset(release.assets, Build.SUPPORTED_ABIS.toList())
                if (asset == null) {
                    if (!silent) toast("暂无本设备安装包")
                    return@launch
                }
                pending = asset
                pendingVersion = release.version
                dialog = UpdateDialogState.Available(release.version, release.notes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!silent) toast("检查更新失败：${e.message ?: "网络错误"}")
            } finally {
                checking = false
            }
        }
    }

    fun startUpdate() {
        val asset = pending ?: return
        if (downloading) return
        UpdateApk.reuse(context, asset)?.let { file ->
            install(file)
            return
        }
        downloading = true
        progressDismissed = false
        lastProgress = 0f
        dialog = UpdateDialogState.Downloading(0f)
        UpdateNotification.show(context, 0f)
        job =
            scope.launch {
                try {
                    val file =
                        UpdateApk.download(context, asset) { progress ->
                            withContext(Dispatchers.Main) {
                                lastProgress = progress
                                UpdateNotification.show(context, progress)
                                if (!progressDismissed) dialog = UpdateDialogState.Downloading(progress)
                            }
                        }
                    install(file)
                } catch (e: CancellationException) {
                    UpdateNotification.cancel(context)
                    throw e
                } catch (e: Exception) {
                    UpdateNotification.cancel(context)
                    toast("下载失败：${e.message ?: "网络错误"}")
                    if (dialog is UpdateDialogState.Downloading) dialog = UpdateDialogState.None
                } finally {
                    downloading = false
                    job = null
                }
            }
    }

    /** 收起进度弹窗，下载继续并在通知里显示 */
    fun hideProgress() {
        if (!downloading) return
        progressDismissed = true
        dialog = UpdateDialogState.None
    }

    fun stopUpdate() {
        job?.cancel()
        job = null
        downloading = false
        progressDismissed = false
        lastProgress = 0f
        UpdateNotification.cancel(context)
        // .part 由下载协程的 finally 收尾，这里只清已落盘的包
        UpdateApk.target(context).delete()
        dialog = UpdateDialogState.None
    }

    fun dismissDialog() {
        dialog = UpdateDialogState.None
    }

    /** 回到前台，接续被拦下的安装 */
    fun onAppResumed() {
        val state = dialog as? UpdateDialogState.ReadyToInstall ?: return
        dialog = UpdateDialogState.None
        UpdateNotification.cancel(context)
        install(state.file)
    }

    private fun reopenProgress() {
        if (!downloading) return
        progressDismissed = false
        dialog = UpdateDialogState.Downloading(lastProgress)
    }

    private fun install(file: File) {
        if (ShizukuAccess.hasPermission) {
            UpdateNotification.cancel(context)
            dialog = UpdateDialogState.None
            scope.launch {
                val error = UpdateApk.installSilently(context, file)
                if (error != null) {
                    toast("Shizuku 安装失败：$error")
                    installBySystem(file)
                }
            }
            return
        }
        installBySystem(file)
    }

    private fun installBySystem(file: File) {
        if (!AppForeground.resumed) {
            UpdateNotification.showDone(context, pendingVersion)
            dialog = UpdateDialogState.ReadyToInstall(file)
            return
        }
        UpdateNotification.cancel(context)
        if (!UpdateApk.canInstall(context)) {
            dialog = UpdateDialogState.None
            if (!UpdateApk.openPermissionSettings(context)) toast("请在系统设置中允许安装应用")
            return
        }
        if (!UpdateApk.install(context, file)) toast("无法启动安装器")
        dialog = UpdateDialogState.None
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: UpdateController? = null

        fun of(context: Context): UpdateController =
            instance ?: synchronized(this) {
                instance ?: UpdateController(context.applicationContext).also { instance = it }
            }
    }
}
