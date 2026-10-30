package com.miuix.hyperschedule.update

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miuix.hyperschedule.getAppVersion
import com.miuix.hyperschedule.reminder.ShizukuAccess
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 更新弹窗状态 */
sealed interface UpdateDialogState {
    data object None : UpdateDialogState

    data class Available(val version: String) : UpdateDialogState

    data class Downloading(val progress: Float) : UpdateDialogState

    data object AwaitingPermission : UpdateDialogState

    /** 后台下载完成，等回到前台再拉起系统安装器 */
    data class ReadyToInstall(val file: File) : UpdateDialogState

    /** 交给 Shizuku 静默安装，装完系统会杀掉本进程 */
    data object Installing : UpdateDialogState

    data object NeedPermission : UpdateDialogState
}

/** App 是否在前台，后台拉不起安装器 */
internal object AppForeground {
    @Volatile
    var resumed: Boolean = false
}

/** 检查、下载与安装；进程级单例，Activity 重建不会中断下载 */
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
        if (downloading) {
            reopenProgress()
            return
        }
        if (checking) return
        checking = true
        if (!silent) toast("正在检查更新")
        scope.launch {
            try {
                val release =
                    withContext(Dispatchers.IO) {
                        AppUpdate.fetchRelease(source, acceptPrerelease, proxy)
                    }
                if (AppUpdate.compareVersions(release.version, getAppVersion(context)) <= 0) {
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
                dialog = UpdateDialogState.Available(release.version)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!silent) toast("检查更新失败")
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
        when (val state = dialog) {
            UpdateDialogState.AwaitingPermission ->
                if (UpdateApk.canInstall(context)) {
                    install(UpdateApk.target(context))
                } else {
                    dialog = UpdateDialogState.NeedPermission
                }

            is UpdateDialogState.ReadyToInstall -> {
                dialog = UpdateDialogState.None
                UpdateNotification.cancel(context)
                install(state.file)
            }

            else -> Unit
        }
    }

    fun openInstallSettings() {
        if (!UpdateApk.openPermissionSettings(context)) {
            toast("无法打开权限设置")
            dialog = UpdateDialogState.None
            return
        }
        dialog = UpdateDialogState.AwaitingPermission
    }

    private fun reopenProgress() {
        if (!downloading) return
        progressDismissed = false
        dialog = UpdateDialogState.Downloading(lastProgress)
    }

    private fun install(file: File) {
        if (ShizukuAccess.hasPermission) {
            // 静默安装不挑前后台、也不用授权页，装完系统会直接杀掉本进程
            UpdateNotification.cancel(context)
            dialog = UpdateDialogState.Installing
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
        // 后台既拉不起安装器也拉不起授权页，统一交给通知把用户叫回来
        if (!AppForeground.resumed) {
            UpdateNotification.showDone(context, pendingVersion)
            dialog = UpdateDialogState.ReadyToInstall(file)
            return
        }
        UpdateNotification.cancel(context)
        if (!UpdateApk.canInstall(context)) {
            openInstallSettings()
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
