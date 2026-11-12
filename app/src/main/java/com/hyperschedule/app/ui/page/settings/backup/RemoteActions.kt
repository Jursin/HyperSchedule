package com.hyperschedule.app.ui.page.settings.backup

import android.content.Context
import androidx.compose.runtime.MutableState
import com.hyperschedule.app.data.backup.BackupFile
import com.hyperschedule.app.data.backup.BackupRepository
import com.hyperschedule.app.ui.component.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 重读远端列表，配置不全时直接清空 */
internal suspend fun RemoteAccount.reload() {
    if (!configured()) {
        list.clear()
        return
    }
    list.loading = true
    val result = runCatching { client().list() }
    list.loading = false
    result.fold(
        onSuccess = {
            list.files = it
            list.error = ""
        },
        onFailure = {
            list.files = emptyList()
            list.error = it.message ?: "读取云端备份失败"
        },
    )
}

/** 输入校验与落盘，通过后占住 busy */
internal fun RemoteAccount.begin(
    context: Context,
    busy: MutableState<Boolean>,
): Boolean {
    draft.error?.let {
        context.toast(it)
        return false
    }
    draft.save()
    if (!configured()) {
        context.toast(hint)
        return false
    }
    if (busy.value) return false
    busy.value = true
    return true
}

internal fun RemoteAccount.test(
    context: Context,
    busy: MutableState<Boolean>,
    scope: CoroutineScope,
) {
    if (!begin(context, busy)) return
    scope.launch {
        val result = runCatching { client().testConnection() }
        busy.value = false
        result.fold(
            onSuccess = {
                context.toast(it)
                reload()
            },
            onFailure = { context.toast(it.message ?: "连接失败") },
        )
    }
}

internal fun RemoteAccount.upload(
    context: Context,
    busy: MutableState<Boolean>,
    scope: CoroutineScope,
) {
    if (!begin(context, busy)) return
    scope.launch {
        val saved = runCatching { saveNow() }
        busy.value = false
        saved.fold(
            onSuccess = { fileName ->
                if (fileName == null) {
                    context.toast("内容没有变化，未新增备份")
                } else {
                    context.toast("已备份到${name} $fileName")
                    reload()
                }
            },
            onFailure = {
                val error = it.message ?: "无法上传"
                mark(null, error)
                context.toast("备份失败：$error")
            },
        )
    }
}

internal fun RemoteAccount.openBackup(
    file: BackupFile,
    source: BackupSource,
    context: Context,
    repository: BackupRepository,
    busy: MutableState<Boolean>,
    scope: CoroutineScope,
    onRestore: (RestoreTarget) -> Unit,
) {
    if (!configured() || busy.value) return
    busy.value = true
    scope.launch {
        val result = runCatching { RestoreTarget(file, repository.readFrom(client(), file.name), source) }
        busy.value = false
        result.fold(
            onSuccess = onRestore,
            onFailure = { context.toast(it.message ?: "读取云端备份失败") },
        )
    }
}

internal fun RemoteAccount.deleteBackup(
    fileName: String,
    context: Context,
    scope: CoroutineScope,
) {
    if (!configured()) return
    scope.launch {
        runCatching { client().delete(fileName) }.onFailure { context.toast(it.message ?: "删除失败") }
        reload()
    }
}
