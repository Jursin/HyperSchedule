package top.jursin.hyperschedule.ui.page.settings.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.jursin.hyperschedule.data.backup.BACKUP_FOLDER
import top.jursin.hyperschedule.data.backup.BackupFile
import top.jursin.hyperschedule.data.backup.BackupPayload
import top.jursin.hyperschedule.data.backup.BackupRepository
import top.jursin.hyperschedule.data.backup.ParsedBackup
import top.jursin.hyperschedule.data.backup.RemoteBackupClient
import top.jursin.hyperschedule.data.backup.S3_DEFAULT_REGION
import top.jursin.hyperschedule.data.backup.backupFolderError
import top.jursin.hyperschedule.data.backup.normalizeBackupFolder
import top.jursin.hyperschedule.data.backup.normalizeS3Name
import top.jursin.hyperschedule.data.local.SettingsStore
import java.net.URI

/** 输入框草稿，负责校验、落盘与恢复 */
internal interface RemoteDraft {
    /** 当前输入的问题，合法时返回 null */
    val error: String?

    fun save()

    fun reset()
}

/** 远端备份列表状态，WebDAV 与 S3 各一份 */
internal class RemoteList {
    var files by mutableStateOf<List<BackupFile>?>(null)
    var error by mutableStateOf("")
    var loading by mutableStateOf(false)

    fun clear() {
        files = emptyList()
        error = ""
        loading = false
    }
}

internal class WebDavDraft(private val store: SettingsStore) : RemoteDraft {
    var serverUrl by mutableStateOf(store.state.webDav.serverUrl.trimEnd('/'))
    var username by mutableStateOf(store.state.webDav.username)
    var password by mutableStateOf(store.state.webDav.password)

    /** 路径为空时直接显示默认名，输入框不出现空白态 */
    var folder by mutableStateOf(store.state.webDav.folder.ifBlank { BACKUP_FOLDER })

    override val error: String?
        get() = serverUrlError(serverUrl) ?: backupFolderError(folder)

    /** 校验不过的值不存，离开页面时会被恢复成上次保存的值 */
    override fun save() {
        if (error != null) return
        val url = serverUrl.trim().trimEnd('/')
        val user = username.trim()
        val saved = store.state.webDav
        if (url == saved.serverUrl && user == saved.username && folder == saved.folder && password == saved.password) {
            return
        }
        store.update {
            it.copy(
                webDav =
                    it.webDav.copy(
                        serverUrl = url,
                        username = user,
                        password = password,
                        folder = normalizeBackupFolder(folder),
                        stamp = it.webDav.stamp.copy(lastError = ""),
                    ),
            )
        }
    }

    override fun reset() {
        val saved = store.state.webDav
        serverUrl = saved.serverUrl.trimEnd('/')
        username = saved.username
        password = saved.password
        folder = saved.folder.ifBlank { BACKUP_FOLDER }
    }
}

internal class S3Draft(private val store: SettingsStore) : RemoteDraft {
    var endpoint by mutableStateOf(store.state.s3.endpoint.trimEnd('/'))
    var accessKeyId by mutableStateOf(store.state.s3.accessKeyId)
    var secretAccessKey by mutableStateOf(store.state.s3.secretAccessKey)
    var bucket by mutableStateOf(store.state.s3.bucket)
    var region by mutableStateOf(store.state.s3.region.ifBlank { S3_DEFAULT_REGION })

    /** 路径为空时直接显示默认名，输入框不出现空白态 */
    var folder by mutableStateOf(store.state.s3.folder.ifBlank { BACKUP_FOLDER })

    override val error: String?
        get() = serverUrlError(endpoint) ?: backupFolderError(folder)

    override fun save() {
        if (error != null) return
        val url = endpoint.trim().trimEnd('/')
        val accessKey = accessKeyId.trim()
        val bucketName = normalizeS3Name(bucket)
        val regionName = normalizeS3Name(region).ifBlank { S3_DEFAULT_REGION }
        val saved = store.state.s3
        if (
            url == saved.endpoint &&
                accessKey == saved.accessKeyId &&
                bucketName == saved.bucket &&
                regionName == saved.region &&
                folder == saved.folder &&
                secretAccessKey == saved.secretAccessKey
        ) {
            return
        }
        store.update {
            it.copy(
                s3 =
                    it.s3.copy(
                        endpoint = url,
                        accessKeyId = accessKey,
                        secretAccessKey = secretAccessKey,
                        bucket = bucketName,
                        region = regionName,
                        folder = normalizeBackupFolder(folder),
                        stamp = it.s3.stamp.copy(lastError = ""),
                    ),
            )
        }
    }

    override fun reset() {
        val saved = store.state.s3
        endpoint = saved.endpoint.trimEnd('/')
        accessKeyId = saved.accessKeyId
        secretAccessKey = saved.secretAccessKey
        bucket = saved.bucket
        region = saved.region.ifBlank { S3_DEFAULT_REGION }
        folder = saved.folder.ifBlank { BACKUP_FOLDER }
    }
}

/** 备份来自哪里，恢复与删除都要按来源找对客户端 */
internal enum class BackupSource { Local, WebDav, S3 }

/** 待恢复的备份 */
internal class RestoreTarget(
    val file: BackupFile,
    val parsed: ParsedBackup,
    val source: BackupSource = BackupSource.Local,
) {
    val payload: BackupPayload get() = parsed.payload

    /** 有一块读不了时提示只恢复哪一部分 */
    val partialNotice: String?
        get() =
            when {
                !parsed.hasPrefs -> "该备份的设置由更新版本的应用创建，只会恢复课表"
                !parsed.hasData -> "该备份的课表由更新版本的应用创建，只会恢复设置"
                else -> null
            }
}

/** 远端备份的界面动作，两个后端只差配置来源与文案 */
internal class RemoteAccount(
    /** toast 里对远端的称呼 */
    val name: String,
    /** 配置不全时的提示 */
    val hint: String,
    val list: RemoteList,
    val draft: RemoteDraft,
    private val repository: BackupRepository,
    val configured: () -> Boolean,
    val client: () -> RemoteBackupClient,
    val lastHash: () -> String,
    val keepCount: () -> Int,
    val mark: (hash: String?, error: String?) -> Unit,
    val setKeep: (Int) -> Unit,
) {
    /** 上传一份快照，内容没变且远端那份还在时返回 null */
    suspend fun saveNow(): String? {
        val client = client()
        val data = repository.build()
        val hash = repository.fingerprint(data)
        val files = client.list()
        if (hash == lastHash() && files.isNotEmpty()) return null
        val name = repository.saveTo(client, data, keepCount(), files)
        mark(hash, null)
        return name
    }
}

/** 地址输入的问题，没填或合法时返回 null */
internal fun serverUrlError(url: String): String? {
    if (url.isBlank()) return null
    val uri = runCatching { URI(url.trim()) }.getOrNull()
    if (uri != null && uri.scheme?.lowercase() !in setOf("http", "https")) return "需要以 http:// 或 https:// 开头"
    return if (uri?.host?.contains('.') == true) null else "地址格式不正确"
}
