package com.hyperschedule.app.ui.page

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hyperschedule.app.data.backup.BackupDir
import com.hyperschedule.app.data.backup.BackupDirs
import com.hyperschedule.app.data.backup.BackupFile
import com.hyperschedule.app.data.backup.BackupRepository
import com.hyperschedule.app.data.backup.RemoteBackupClient
import com.hyperschedule.app.data.backup.S3Client
import com.hyperschedule.app.data.backup.WebDavClient
import com.hyperschedule.app.data.backup.backupTimeFormatter
import com.hyperschedule.app.data.db.AppDatabase
import com.hyperschedule.app.theme.BackupSettings
import com.hyperschedule.app.theme.S3Settings
import com.hyperschedule.app.theme.SettingsStore
import com.hyperschedule.app.theme.WebDavSettings
import com.hyperschedule.app.ui.component.PreferenceScreen
import com.hyperschedule.app.ui.component.toast
import com.hyperschedule.app.ui.page.backup.BackupSource
import com.hyperschedule.app.ui.page.backup.LocalBackupTab
import com.hyperschedule.app.ui.page.backup.RemoteAccount
import com.hyperschedule.app.ui.page.backup.RemoteList
import com.hyperschedule.app.ui.page.backup.RestoreConfirmDialog
import com.hyperschedule.app.ui.page.backup.RestoreTarget
import com.hyperschedule.app.ui.page.backup.S3BackupTab
import com.hyperschedule.app.ui.page.backup.S3Draft
import com.hyperschedule.app.ui.page.backup.WebDavBackupTab
import com.hyperschedule.app.ui.page.backup.WebDavDraft
import com.hyperschedule.app.ui.page.backup.record
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TabRowWithContour

/** 页面在前台时检查目录变化的间隔 */
private const val DIR_REFRESH_INTERVAL = 3_000L

private const val TAB_LOCAL = 0
private const val TAB_WEBDAV = 1
private const val TAB_S3 = 2

/** 备份与恢复页 */
@Composable
fun BackupPage(
    store: SettingsStore,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state = store.state
    val backup = state.backup
    val scope = rememberCoroutineScope()
    val repository = remember { BackupRepository(context, AppDatabase.get(context)) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val webDav = remember { WebDavDraft(store) }
    val s3 = remember { S3Draft(store) }
    var files by remember { mutableStateOf<List<BackupFile>?>(null) }
    var dirUsable by remember { mutableStateOf<Boolean?>(null) }
    val davList = remember { RemoteList() }
    val s3List = remember { RemoteList() }
    var busy by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<RestoreTarget?>(null) }

    val dir = remember(backup.location, backup.locationLabel) {
        BackupDirs.from(context, backup.location, backup.locationLabel)
    }

    /** 当前位置一律从存储里现取 */
    fun currentDir(): BackupDir? =
        store.state.backup.let { BackupDirs.from(context, it.location, it.locationLabel) }

    suspend fun reload(target: BackupDir?) {
        if (target == null) {
            files = emptyList()
            dirUsable = null
            return
        }
        val usable = withContext(Dispatchers.IO) { target.isAccessible() }
        dirUsable = usable
        // 目录失效时不去读列表，免得把「读不到」显示成「没有备份」
        files = if (usable) runCatching { repository.list(target) }.getOrDefault(emptyList()) else emptyList()
    }

    fun applyDir(target: BackupDir, keepHash: Boolean = true) {
        store.update {
            it.copy(
                backup =
                    it.backup.copy(
                        location = target.location,
                        locationLabel = target.label,
                        // 新目录里没有备份时才清指纹，否则下次启动会平白多写一份同样的文件
                        lastHash = if (keepHash) it.backup.lastHash else "",
                        lastError = "",
                    ),
            )
        }
    }

    /** 换目录时把旧备份一并搬过去，UI 仍显示旧目录直到搬完 */
    fun switchDir(target: BackupDir) {
        val old = currentDir()
        if (old == null || old.location == target.location) {
            applyDir(target)
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            val result = runCatching { repository.migrate(old, target, store.state.backup.keepCount) }
            busy = false
            // 备份真的搬过去了，内容指纹依然有效，新目录是空的才清掉，让下次备份补一份
            applyDir(target, keepHash = result.getOrDefault(0) > 0)
            result.fold(
                onSuccess = { if (it > 0) context.toast("已迁移 $it 份备份") },
                onFailure = { context.toast("原路径读取失败，备份未迁移") },
            )
        }
    }

    /** 写一份备份并记下指纹与时间，内容没变且目录里那份还在时返回 null */
    suspend fun saveBackup(target: BackupDir): String? {
        val data = repository.build()
        val hash = repository.fingerprint(data)
        if (hash == store.state.backup.lastHash && repository.hasBackup(target)) return null
        // 保留数量从存储里取，避免用到旧组合里的值
        val name = repository.save(target, data, store.state.backup.keepCount)
        store.update {
            it.copy(
                backup =
                    it.backup.copy(
                        lastHash = hash,
                        lastTime = LocalDateTime.now().format(backupTimeFormatter),
                        lastError = "",
                    ),
            )
        }
        return name
    }

    fun runBackup(target: BackupDir) {
        if (busy) return
        busy = true
        scope.launch {
            val saved = runCatching { saveBackup(target) }
            busy = false
            reload(target)
            saved.fold(
                onSuccess = { name ->
                    if (name == null) context.toast("内容没有变化，未新增备份") else context.toast("已备份 $name")
                },
                onFailure = { context.toast("备份失败：${it.message ?: "无法写入文件"}") },
            )
        }
    }

    fun persistWebDav(transform: (WebDavSettings) -> WebDavSettings) {
        store.update { it.copy(webDav = transform(it.webDav)) }
    }

    fun persistS3(transform: (S3Settings) -> S3Settings) {
        store.update { it.copy(s3 = transform(it.s3)) }
    }

    fun webDavClient(): RemoteBackupClient =
        store.state.webDav.let { WebDavClient(it.serverUrl, it.username, it.password, it.folder) }

    fun s3Client(): RemoteBackupClient =
        store.state.s3.let {
            S3Client(it.endpoint, it.bucket, it.region, it.accessKeyId, it.secretAccessKey, it.pathStyle, it.folder)
        }

    /** 两个远端的动作只差配置来源，抽成一份 */
    val dav =
        RemoteAccount(
            name = "云端",
            hint = "请先填写服务器地址、用户名和密码",
            list = davList,
            draft = webDav,
            configured = { store.state.webDav.configured },
            client = ::webDavClient,
            lastHash = { store.state.webDav.lastHash },
            keepCount = { store.state.webDav.keepCount },
            mark = { hash, error -> persistWebDav { it.record(hash, error) } },
            setKeep = { count -> persistWebDav { it.copy(keepCount = count) } },
            repository = repository,
        )

    val s3Account =
        RemoteAccount(
            name = "对象存储",
            hint = "请先填写端点、访问密钥 ID、机密访问密钥和存储桶",
            list = s3List,
            draft = s3,
            configured = { store.state.s3.configured },
            client = ::s3Client,
            lastHash = { store.state.s3.lastHash },
            keepCount = { store.state.s3.keepCount },
            mark = { hash, error -> persistS3 { it.record(hash, error) } },
            setKeep = { count -> persistS3 { it.copy(keepCount = count) } },
            repository = repository,
        )

    suspend fun reloadRemote(account: RemoteAccount) {
        val list = account.list
        if (!account.configured()) {
            list.clear()
            return
        }
        list.loading = true
        val result = runCatching { account.client().list() }
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

    /** 输入校验与落盘，通过后占住 busy，免得测试和备份撞在一起 */
    fun beginRemote(account: RemoteAccount): Boolean {
        account.draft.error?.let {
            context.toast(it)
            return false
        }
        account.draft.save()
        if (!account.configured()) {
            context.toast(account.hint)
            return false
        }
        if (busy) return false
        busy = true
        return true
    }

    fun testRemote(account: RemoteAccount) {
        if (!beginRemote(account)) return
        scope.launch {
            val result = runCatching { account.client().testConnection() }
            busy = false
            result.fold(
                onSuccess = {
                    context.toast(it)
                    reloadRemote(account)
                },
                onFailure = { context.toast(it.message ?: "连接失败") },
            )
        }
    }

    fun backupRemote(account: RemoteAccount) {
        if (!beginRemote(account)) return
        scope.launch {
            val saved = runCatching { account.saveNow() }
            busy = false
            saved.fold(
                onSuccess = { name ->
                    if (name == null) {
                        context.toast("内容没有变化，未新增备份")
                    } else {
                        context.toast("已备份到${account.name} $name")
                        reloadRemote(account)
                    }
                },
                onFailure = {
                    val error = it.message ?: "无法上传"
                    account.mark(null, error)
                    context.toast("备份失败：$error")
                },
            )
        }
    }

    fun openRemote(account: RemoteAccount, file: BackupFile, source: BackupSource) {
        if (!account.configured() || busy) return
        busy = true
        scope.launch {
            val result = runCatching { RestoreTarget(file, repository.readFrom(account.client(), file.name), source) }
            busy = false
            result.fold(
                onSuccess = { restoreTarget = it },
                onFailure = { context.toast(it.message ?: "读取云端备份失败") },
            )
        }
    }

    fun deleteRemote(account: RemoteAccount, file: BackupFile) {
        if (!account.configured()) return
        scope.launch {
            runCatching { account.client().delete(file.name) }.onFailure { context.toast(it.message ?: "删除失败") }
            reloadRemote(account)
        }
    }

    // 选择器返回后立刻落授权并刷新，首次备份在下面的 effect 里接上
    var resumeAfterPick by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                resumeAfterPick = false
                return@rememberLauncherForActivityResult
            }
            val granted =
                runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                        true
                    }
                    .getOrDefault(false)
            if (!granted) {
                context.toast("无法长期使用该文件夹，请换一个位置")
                resumeAfterPick = false
                return@rememberLauncherForActivityResult
            }
            val target = BackupDirs.from(context, uri.toString(), BackupDirs.labelOf(context, uri).orEmpty())
            if (target == null) {
                resumeAfterPick = false
                return@rememberLauncherForActivityResult
            }
            switchDir(target)
        }

    // 备份前必须有目录，没配置过就先去选
    fun startBackup() {
        val current = currentDir()
        if (current != null) {
            runBackup(current)
            return
        }
        resumeAfterPick = true
        picker.launch(null)
    }

    LaunchedEffect(backup.location) {
        val target = BackupDirs.from(context, backup.location, backup.locationLabel)
        reload(target)
        if (resumeAfterPick && target != null) {
            resumeAfterPick = false
            runBackup(target)
        }
    }

    // 账号输入停手后再落盘，不用每敲一个字写一次 prefs
    LaunchedEffect(webDav.serverUrl, webDav.username, webDav.password, webDav.folder) {
        delay(600.milliseconds)
        webDav.save()
    }
    LaunchedEffect(s3.endpoint, s3.accessKeyId, s3.secretAccessKey, s3.bucket, s3.region, s3.folder) {
        delay(600.milliseconds)
        s3.save()
    }
    // 防抖还没到就离开页面时补一次落盘
    DisposableEffect(Unit) {
        onDispose {
            webDav.save()
            s3.save()
        }
    }

    // 切走某个 Tab 时先存下合法输入，再让输入框回到已保存的值，非法输入不留在界面上
    LaunchedEffect(selectedTab) {
        if (selectedTab != TAB_WEBDAV) {
            webDav.save()
            webDav.reset()
        }
        if (selectedTab != TAB_S3) {
            s3.save()
            s3.reset()
        }
    }

    // 切到 WebDAV 页、账号填全或换路径时读一次云端列表，远端没有目录通知
    LaunchedEffect(selectedTab, store.state.webDav.configured, store.state.webDav.folder) {
        if (selectedTab == TAB_WEBDAV) reloadRemote(dav)
    }
    // S3 同理，换地址、桶或路径都要重读
    LaunchedEffect(
        selectedTab,
        store.state.s3.configured,
        store.state.s3.endpoint,
        store.state.s3.bucket,
        store.state.s3.pathStyle,
        store.state.s3.folder,
    ) {
        if (selectedTab == TAB_S3) reloadRemote(s3Account)
    }

    // 应用外往目录里增删文件后，靠前台轮询刷新列表
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(dir?.location, lifecycleOwner) {
        while (true) {
            delay(DIR_REFRESH_INTERVAL.milliseconds)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) reload(currentDir())
        }
    }

    fun openRestore(file: BackupFile) {
        val target = currentDir() ?: return
        if (busy) return
        busy = true
        scope.launch {
            val result = runCatching { RestoreTarget(file, repository.read(target, file.name)) }
            busy = false
            result.fold(
                onSuccess = { restoreTarget = it },
                onFailure = { context.toast(it.message ?: "读取备份失败") },
            )
        }
    }

    fun confirmRestore(target: RestoreTarget) {
        val dirNow = currentDir()
        // 本地备份必须知道往哪个目录留现场，云端备份不依赖本地目录
        if (target.source == BackupSource.Local && dirNow == null) return
        restoreTarget = null
        busy = true
        scope.launch {
            val result =
                runCatching {
                    // 静默留一份当前数据，内容与已有备份一致时不会白写一份
                    when (target.source) {
                        BackupSource.WebDav -> dav.saveNow()
                        BackupSource.S3 -> s3Account.saveNow()
                        BackupSource.Local -> dirNow?.let { saveBackup(it) }
                    }
                    repository.restore(target.payload)
                }
            busy = false
            when (target.source) {
                BackupSource.WebDav -> reloadRemote(dav)
                BackupSource.S3 -> reloadRemote(s3Account)
                BackupSource.Local -> if (dirNow != null) reload(dirNow)
            }
            result.fold(
                onSuccess = { result ->
                    context.toast("已恢复 ${result.tableCount} 张课表、${result.courseCount} 门课程")
                },
                onFailure = { context.toast("恢复失败：${it.message ?: "数据未改动"}") },
            )
        }
    }

    fun deleteFile(target: RestoreTarget) {
        restoreTarget = null
        when (target.source) {
            BackupSource.WebDav -> deleteRemote(dav, target.file)
            BackupSource.S3 -> deleteRemote(s3Account, target.file)
            BackupSource.Local -> {
                val dir = currentDir() ?: return
                repository.delete(dir, target.file.name)
                scope.launch { reload(dir) }
            }
        }
    }

    PreferenceScreen(
        title = "备份与恢复",
        appBlur = appBlur,
        onBack = {
            // 返回前收尾一次
            webDav.save()
            webDav.reset()
            s3.save()
            s3.reset()
            onBack()
        },
    ) {
        TabRowWithContour(
            tabs = listOf("本地", "WebDAV", "S3"),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        when (selectedTab) {
            TAB_LOCAL -> {
                LocalBackupTab(
                    backup = backup,
                    // 位置说明以当前解析结果为准，存下来的旧值只作兜底
                    dirLabel = dir?.label ?: backup.locationLabel,
                    dirUsable = dirUsable,
                    busy = busy,
                    files = files,
                    onPickDir = {
                        resumeAfterPick = false
                        picker.launch(null)
                    },
                    onBackupNow = ::startBackup,
                    onToggleAuto = { enabled ->
                        store.update { it.copy(backup = it.backup.copy(autoBackup = enabled)) }
                        // 打开自动备份时先把目录定下来，否则启动时无从判断
                        if (enabled && !store.state.backup.configured) {
                            resumeAfterPick = false
                            picker.launch(null)
                        }
                    },
                    onKeepChange = { index ->
                        val count = BackupSettings.KEEP_OPTIONS[index]
                        store.update { it.copy(backup = it.backup.copy(keepCount = count)) }
                        dir?.let { target -> scope.launch { reload(target) } }
                    },
                    onOpenFile = ::openRestore,
                )
            }
            TAB_WEBDAV -> {
                WebDavBackupTab(
                    settings = store.state.webDav,
                    draft = webDav,
                    list = davList,
                    busy = busy,
                    onTest = { testRemote(dav) },
                    onToggleAuto = { enabled -> persistWebDav { it.copy(autoBackup = enabled) } },
                    onBackupNow = { backupRemote(dav) },
                    onKeepChange = { index -> dav.setKeep(BackupSettings.KEEP_OPTIONS[index]) },
                    onOpenFile = { openRemote(dav, it, BackupSource.WebDav) },
                )
            }
            else -> {
                S3BackupTab(
                    settings = store.state.s3,
                    draft = s3,
                    list = s3List,
                    busy = busy,
                    onTest = { testRemote(s3Account) },
                    onToggleAuto = { enabled -> persistS3 { it.copy(autoBackup = enabled) } },
                    onTogglePathStyle = { enabled ->
                        persistS3 { it.copy(pathStyle = enabled) }
                        scope.launch { reloadRemote(s3Account) }
                    },
                    onBackupNow = { backupRemote(s3Account) },
                    onKeepChange = { index -> s3Account.setKeep(BackupSettings.KEEP_OPTIONS[index]) },
                    onOpenFile = { openRemote(s3Account, it, BackupSource.S3) },
                )
            }
        }
    }

    restoreTarget?.let { target ->
        RestoreConfirmDialog(
            target = target,
            appBlur = appBlur,
            onDismiss = { restoreTarget = null },
            onRestore = { confirmRestore(target) },
            onDelete = { deleteFile(target) },
        )
    }
}
