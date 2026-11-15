package com.hyperschedule.app.data.backup

import android.content.Context
import com.hyperschedule.app.data.local.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 自动备份的落点，本地与两个云端各记一份状态 */
private enum class Target { Local, WebDav, S3 }

/** 启动时的自动备份，本地与各个云端各跑一遍 */
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
            val s3 = store.state.s3
            val dir =
                if (backup.stamp.autoBackup && backup.configured) {
                    BackupDirs.from(context, backup.location, backup.locationLabel)
                } else {
                    null
                }
            val webDavOn = webDav.stamp.autoBackup && webDav.configured
            val s3On = s3.stamp.autoBackup && s3.configured
            if (dir == null && !webDavOn && !s3On) return@withContext

            val payload =
                runCatching { repository.build() }.getOrElse { error ->
                    val message = error.message ?: "读取数据失败"
                    if (dir != null) record(store, Target.Local, error = message)
                    if (webDavOn) record(store, Target.WebDav, error = message)
                    if (s3On) record(store, Target.S3, error = message)
                    return@withContext
                }
            val hash = repository.fingerprint(payload)
            if (dir != null) saveLocal(dir, store, repository, payload, hash, backup.stamp.keepCount)
            if (webDavOn) {
                val client =
                    WebDavClient(webDav.serverUrl, webDav.username, webDav.password, webDav.folder)
                saveRemote(store, repository, payload, hash, Target.WebDav, webDav.stamp.keepCount, client, webDav.stamp.lastHash)
            }
            if (s3On) {
                val client =
                    S3Client(
                        endpoint = s3.endpoint,
                        bucket = s3.bucket,
                        region = s3.region,
                        accessKeyId = s3.accessKeyId,
                        secretAccessKey = s3.secretAccessKey,
                        pathStyle = s3.pathStyle,
                        folder = s3.folder,
                    )
                saveRemote(store, repository, payload, hash, Target.S3, s3.stamp.keepCount, client, s3.stamp.lastHash)
            }
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
            record(store, Target.Local, error = "备份路径已失效，请重新选择")
            return
        }
        // 内容没变、目录里那份还在，就不必再写一份
        if (hash == store.state.backup.stamp.lastHash && repository.hasBackup(dir)) return
        runCatching { repository.save(dir, payload, keep) }
            .onSuccess { record(store, Target.Local, hash = hash) }
            .onFailure { record(store, Target.Local, error = it.message ?: "备份失败") }
    }

    private suspend fun saveRemote(
        store: SettingsStore,
        repository: BackupRepository,
        payload: BackupPayload,
        hash: String,
        target: Target,
        keep: Int,
        client: RemoteBackupClient,
        lastHash: String,
    ) {
        // 云端那份还在不在只能列一次，列不出来就当失败，别在启动时反复重试
        val files =
            runCatching { client.list() }.getOrElse {
                record(store, target, error = it.message ?: "连接云端失败")
                return
            }
        if (hash == lastHash && files.isNotEmpty()) return
        runCatching { repository.saveTo(client, payload, keep, files) }
            .onSuccess { record(store, target, hash = hash) }
            .onFailure { record(store, target, error = it.message ?: "云端备份失败") }
    }

    /** [hash] 非空表示真的写成功了，同时刷新时间 */
    private fun record(
        store: SettingsStore,
        target: Target,
        hash: String? = null,
        error: String? = null,
    ) {
        val now = stampNow()
        store.update { state ->
            when (target) {
                Target.Local -> state.copy(backup = state.backup.copy(stamp = state.backup.stamp.record(hash, error, now)))
                Target.WebDav -> state.copy(webDav = state.webDav.copy(stamp = state.webDav.stamp.record(hash, error, now)))
                Target.S3 -> state.copy(s3 = state.s3.copy(stamp = state.s3.stamp.record(hash, error, now)))
            }
        }
    }
}
