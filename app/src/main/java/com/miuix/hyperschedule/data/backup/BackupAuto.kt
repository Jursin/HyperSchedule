package com.miuix.hyperschedule.data.backup

import android.content.Context
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.theme.WebDavSettings
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 启动时的自动备份，本地与云端各跑一遍 */
object BackupAuto {
    private val mutex = Mutex()

    suspend fun runIfNeeded(
        context: Context,
        store: SettingsStore,
        repository: BackupRepository,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val backup = store.state.backup
            val webDav = store.state.webDav
            val dir =
                if (backup.autoBackup && backup.configured) {
                    BackupDirs.from(context, backup.location, backup.locationLabel)
                } else {
                    null
                }
            val cloud = webDav.autoBackup && webDav.configured
            if (dir == null && !cloud) return@withContext

            val payload =
                runCatching { repository.build() }.getOrElse { error ->
                    val message = error.message ?: "读取数据失败"
                    if (dir != null) record(store, toCloud = false, error = message)
                    if (cloud) record(store, toCloud = true, error = message)
                    return@withContext
                }
            val hash = repository.fingerprint(payload)
            if (dir != null) saveLocal(dir, store, repository, payload, hash, backup.keepCount)
            if (cloud) saveCloud(store, repository, payload, hash, webDav)
        }
    }

    private suspend fun saveLocal(
        dir: BackupDir,
        store: SettingsStore,
        repository: BackupRepository,
        payload: BackupPayload,
        hash: String,
        keep: Int,
    ) {
        if (!dir.isAccessible()) {
            record(store, toCloud = false, error = "备份目录已失效，请重新选择")
            return
        }
        // 内容没变、目录里那份还在，就不必再写一份
        if (hash == store.state.backup.lastHash && repository.hasBackup(dir)) return
        runCatching { repository.save(dir, payload, keep) }
            .onSuccess { record(store, toCloud = false, hash = hash) }
            .onFailure { record(store, toCloud = false, error = it.message ?: "备份失败") }
    }

    private suspend fun saveCloud(
        store: SettingsStore,
        repository: BackupRepository,
        payload: BackupPayload,
        hash: String,
        webDav: WebDavSettings,
    ) {
        val client = WebDavClient(webDav.serverUrl, webDav.username, webDav.password)
        // 上次那份还在不在云端只有列一次才知道，列不出来就当失败，别在启动时反复重试
        val files =
            runCatching { client.list() }.getOrElse {
                record(store, toCloud = true, error = it.message ?: "连接云端失败")
                return
            }
        if (hash == webDav.lastHash && files.isNotEmpty()) return
        runCatching { repository.saveTo(client, payload, webDav.keepCount, files) }
            .onSuccess { record(store, toCloud = true, hash = hash) }
            .onFailure { record(store, toCloud = true, error = it.message ?: "云端备份失败") }
    }

    /** 记下这次结果，[hash] 非空表示真的写成功了，同时刷新时间 */
    private fun record(
        store: SettingsStore,
        toCloud: Boolean,
        hash: String? = null,
        error: String? = null,
    ) {
        val time = hash?.let { LocalDateTime.now().format(backupTimeFormatter) }
        store.update { state ->
            if (toCloud) {
                state.copy(
                    webDav =
                        state.webDav.copy(
                            lastHash = hash ?: state.webDav.lastHash,
                            lastTime = time ?: state.webDav.lastTime,
                            lastError = error.orEmpty(),
                        ),
                )
            } else {
                state.copy(
                    backup =
                        state.backup.copy(
                            lastHash = hash ?: state.backup.lastHash,
                            lastTime = time ?: state.backup.lastTime,
                            lastError = error.orEmpty(),
                        ),
                )
            }
        }
    }
}
