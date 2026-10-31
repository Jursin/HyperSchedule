package com.miuix.hyperschedule.ui.page

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miuix.hyperschedule.data.backup.BackupDir
import com.miuix.hyperschedule.data.backup.BackupDirs
import com.miuix.hyperschedule.data.backup.BackupFile
import com.miuix.hyperschedule.data.backup.BackupPayload
import com.miuix.hyperschedule.data.backup.BackupRepository
import com.miuix.hyperschedule.data.backup.backupTimeFormatter
import com.miuix.hyperschedule.data.backup.sizeLabel
import com.miuix.hyperschedule.data.backup.title
import com.miuix.hyperschedule.data.backup.WebDavClient
import com.miuix.hyperschedule.data.db.AppDatabase
import com.miuix.hyperschedule.theme.BackupSettings
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.SettingsStore
import com.miuix.hyperschedule.theme.WebDavSettings
import com.miuix.hyperschedule.ui.component.Cloud
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.InfoCard
import com.miuix.hyperschedule.ui.component.InfoCardType
import com.miuix.hyperschedule.ui.component.Person
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.pref.ArrowPreference
import com.miuix.hyperschedule.ui.component.pref.MultilineArrowPreference
import com.miuix.hyperschedule.ui.component.pref.OverlayDropdownPreference
import com.miuix.hyperschedule.ui.component.pref.SwitchPreference
import com.miuix.hyperschedule.ui.component.toast
import java.net.URI
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

/** 页面在前台时检查目录变化的间隔 */
private const val DIR_REFRESH_INTERVAL = 3_000L

/** WebDAV 输入框草稿 */
private class WebDavDraft(settings: WebDavSettings) {
    var serverUrl by mutableStateOf(settings.serverUrl)
    var username by mutableStateOf(settings.username)
    var password by mutableStateOf(settings.password)
    var passwordVisible by mutableStateOf(false)
}

/** 待恢复的备份，[remote] 表示来自云端 */
private class RestoreTarget(
    val file: BackupFile,
    val payload: BackupPayload,
    val remote: Boolean = false,
)

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
    var webDav by remember { mutableStateOf(WebDavDraft(store.state.webDav)) }
    var files by remember { mutableStateOf<List<BackupFile>?>(null) }
    var dirUsable by remember { mutableStateOf<Boolean?>(null) }
    var davFiles by remember { mutableStateOf<List<BackupFile>?>(null) }
    var davError by remember { mutableStateOf("") }
    var davLoading by remember { mutableStateOf(false) }
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
            // 备份真的搬过去了，内容指纹依然有效；新目录是空的才清掉，让下次备份补一份
            applyDir(target, keepHash = result.getOrDefault(0) > 0)
            result.fold(
                onSuccess = { if (it > 0) context.toast("已迁移 $it 份备份") },
                onFailure = { context.toast("原目录读取失败，备份未迁移") },
            )
        }
    }

    /** 写一份备份并记下指纹与时间；内容没变且目录里那份还在时返回 null */
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

    // WebDAV

    fun currentWebDav(): WebDavSettings = store.state.webDav

    fun persistWebDav(transform: (WebDavSettings) -> WebDavSettings) {
        store.update { it.copy(webDav = transform(it.webDav)) }
    }

    /** 输入框改动即时落盘 */
    fun saveWebDavFields() {
        val url = webDav.serverUrl.trim().trimEnd('/')
        val user = webDav.username.trim()
        val settings = currentWebDav()
        if (url == settings.serverUrl && user == settings.username && webDav.password == settings.password) return
        persistWebDav { it.copy(serverUrl = url, username = user, password = webDav.password, lastError = "") }
    }

    fun webDavClient(settings: WebDavSettings): WebDavClient =
        WebDavClient(settings.serverUrl, settings.username, settings.password)

    suspend fun reloadWebDav() {
        val settings = currentWebDav()
        if (!settings.configured) {
            davFiles = emptyList()
            if (davError.isNotBlank()) davError = ""
            return
        }
        davLoading = true
        val result = runCatching { webDavClient(settings).list() }
        davLoading = false
        result.fold(
            onSuccess = { list ->
                davFiles = list
                davError = ""
            },
            onFailure = {
                davFiles = emptyList()
                davError = it.message ?: "读取云端备份失败"
            },
        )
    }

    /** 上传快照到云端；内容没变且云端那份还在时返回 null */
    suspend fun saveWebDavBackup(settings: WebDavSettings): String? {
        val client = webDavClient(settings)
        val data = repository.build()
        val hash = repository.fingerprint(data)
        // 列一次，既判断云端那份还在不在，也拿到要清理的旧文件
        val files = client.list()
        if (hash == settings.lastHash && files.isNotEmpty()) return null
        val name = repository.saveTo(client, data, settings.keepCount, files)
        persistWebDav {
            it.copy(lastHash = hash, lastTime = LocalDateTime.now().format(backupTimeFormatter), lastError = "")
        }
        return name
    }

    fun testWebDav() {
        val settings = currentWebDav()
        if (!settings.configured) {
            context.toast("请先填写服务器地址、用户名和密码")
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            val result = runCatching { webDavClient(settings).testConnection() }
            busy = false
            result.fold(
                onSuccess = {
                    context.toast(it)
                    reloadWebDav()
                },
                onFailure = { context.toast(it.message ?: "连接失败") },
            )
        }
    }

    fun runWebDavBackup() {
        val settings = currentWebDav()
        if (!settings.configured) {
            context.toast("请先填写服务器地址、用户名和密码")
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            val saved = runCatching { saveWebDavBackup(settings) }
            busy = false
            saved.fold(
                onSuccess = { name ->
                    if (name == null) {
                        context.toast("内容没有变化，未新增备份")
                    } else {
                        context.toast("已备份到云端 $name")
                        reloadWebDav()
                    }
                },
                onFailure = {
                    val error = it.message ?: "无法上传"
                    persistWebDav { state -> state.copy(lastError = error) }
                    context.toast("备份失败：$error")
                },
            )
        }
    }

    fun openWebDavFile(file: BackupFile) {
        val settings = currentWebDav()
        if (!settings.configured || busy) return
        busy = true
        scope.launch {
            val result =
                runCatching { RestoreTarget(file, repository.readFrom(webDavClient(settings), file.name), remote = true) }
            busy = false
            result.fold(
                onSuccess = { restoreTarget = it },
                onFailure = { context.toast(it.message ?: "读取云端备份失败") },
            )
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
    LaunchedEffect(webDav.serverUrl, webDav.username, webDav.password) {
        delay(600.milliseconds)
        saveWebDavFields()
    }
    // 防抖还没到就离开页面时补一次落盘
    DisposableEffect(Unit) { onDispose { saveWebDavFields() } }

    // 切到 WebDAV 页或账号刚填全时读一次云端列表，远端没有目录通知
    LaunchedEffect(selectedTab, store.state.webDav.configured) {
        if (selectedTab == 1) reloadWebDav()
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
        // 本地备份必须知道往哪个目录留现场；云端备份不依赖本地目录
        if (!target.remote && dirNow == null) return
        restoreTarget = null
        busy = true
        scope.launch {
            val result =
                runCatching {
                    // 静默留一份当前数据，内容与已有备份一致时不会白写一份
                    if (target.remote) {
                        saveWebDavBackup(currentWebDav())
                    } else if (dirNow != null) {
                        saveBackup(dirNow)
                    }
                    repository.restore(target.payload)
                }
            busy = false
            if (target.remote) {
                reloadWebDav()
            } else if (dirNow != null) {
                reload(dirNow)
            }
            result.fold(
                onSuccess = { result ->
                    val skipped = if (result.droppedRows > 0) "，忽略 ${result.droppedRows} 条无法关联的记录" else ""
                    context.toast("已恢复 ${result.tableCount} 张课表、${result.courseCount} 门课程$skipped")
                },
                onFailure = { context.toast("恢复失败：${it.message ?: "数据未改动"}") },
            )
        }
    }

    fun deleteFile(target: RestoreTarget) {
        restoreTarget = null
        if (target.remote) {
            val settings = currentWebDav()
            if (!settings.configured) return
            scope.launch {
                runCatching { webDavClient(settings).delete(target.file.name) }
                    .onFailure { context.toast(it.message ?: "删除失败") }
                reloadWebDav()
            }
            return
        }
        val dir = currentDir() ?: return
        repository.delete(dir, target.file.name)
        scope.launch { reload(dir) }
    }

    PreferenceScreen(title = "备份与恢复", appBlur = appBlur, onBack = onBack) {
        TabRowWithContour(
            tabs = listOf("本地", "WebDAV"),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        if (selectedTab == 0) {
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
        } else {
            WebDavBackupTab(
                settings = store.state.webDav,
                draft = webDav,
                busy = busy,
                loading = davLoading,
                error = davError,
                files = davFiles,
                onTest = ::testWebDav,
                onToggleAuto = { enabled -> persistWebDav { it.copy(autoBackup = enabled) } },
                onBackupNow = ::runWebDavBackup,
                onKeepChange = { index ->
                    persistWebDav { it.copy(keepCount = BackupSettings.KEEP_OPTIONS[index]) }
                },
                onOpenFile = ::openWebDavFile,
            )
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

@Composable
private fun LocalBackupTab(
    backup: BackupSettings,
    dirLabel: String,
    dirUsable: Boolean?,
    busy: Boolean,
    files: List<BackupFile>?,
    onPickDir: () -> Unit,
    onBackupNow: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onKeepChange: (Int) -> Unit,
    onOpenFile: (BackupFile) -> Unit,
) {
    PreferenceSection {
        MultilineArrowPreference(
            title = "备份目录",
            summary = dirLabel.ifBlank { "点击选择备份文件夹" },
            onClick = onPickDir,
        )
        ArrowPreference(
            title = "立即备份",
            summary =
                when {
                    busy -> "正在处理"
                    dirUsable == false -> "备份目录已失效，请重新选择"
                    backup.lastError.isNotBlank() -> backup.lastError
                    backup.lastTime.isNotBlank() -> "上次备份 ${backup.lastTime}"
                    else -> null
                },
            enabled = !busy,
            onClick = onBackupNow,
        )
        SwitchPreference(
            checked = backup.autoBackup,
            onCheckedChange = onToggleAuto,
            title = "启动时自动备份",
            summary = "启动时检查一次，内容有变化时新增备份",
        )
        OverlayDropdownPreference(
            title = "备份保留数量",
            items = BackupSettings.KEEP_OPTIONS.map { "$it 份" },
            selectedIndex = BackupSettings.keepIndexOf(backup.keepCount),
            summary = "超出后删除最旧的一份",
            onSelectedIndexChange = onKeepChange,
        )
    }
    BackupFileSection(
        hint =
            when {
                dirLabel.isBlank() -> "还未选择备份目录"
                dirUsable == false -> "备份目录已失效（文件夹被删除或授权被系统回收），请重新选择"
                else -> null
            },
        files = files,
        onOpenFile = onOpenFile,
    )
    RestoreHint()
}

/** 本地与云端共用的文件列表，hint 优先 */
@Composable
private fun BackupFileSection(
    hint: String?,
    files: List<BackupFile>?,
    onOpenFile: (BackupFile) -> Unit,
) {
    PreferenceSection("备份文件") {
        when {
            hint != null -> SectionHint(hint)
            files == null -> SectionHint("正在读取…")
            files.isEmpty() -> SectionHint("暂无备份文件")
            else ->
                files.forEach { file ->
                    ArrowPreference(
                        title = file.title(),
                        summary = file.sizeLabel(),
                        onClick = { onOpenFile(file) },
                    )
                }
        }
    }
}

/** 恢复前的风险提示 */
@Composable
private fun RestoreHint() {
    InfoCard(text = "恢复会覆盖当前全部课表与设置，覆盖前会自动保存一份当前数据。", type = InfoCardType.Important)
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(16.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun RestoreConfirmDialog(
    target: RestoreTarget,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val detail =
        buildString {
            append("${target.file.title()} · ${target.file.sizeLabel()}")
            if (target.payload.appVersion.isNotBlank()) append(" · v${target.payload.appVersion}")
            append("\n")
            append("${target.payload.tableCount} 张课表 · ${target.payload.courseCount} 门课程")
        }
    ConfirmDialog(
        title = "恢复备份",
        message = detail,
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = onRestore,
        actions = {
            TextButton(
                modifier = Modifier.weight(1f),
                onClick = onDelete,
                text = "删除",
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
            )
            TextButton(
                modifier = Modifier.weight(1f),
                onClick = onRestore,
                text = "恢复",
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
    )
}

@Composable
private fun WebDavBackupTab(
    settings: WebDavSettings,
    draft: WebDavDraft,
    busy: Boolean,
    loading: Boolean,
    error: String,
    files: List<BackupFile>?,
    onTest: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onKeepChange: (Int) -> Unit,
    onOpenFile: (BackupFile) -> Unit,
) {
    // 失焦后才提示地址非法，避免边输入边报错
    val urlInteraction = remember { MutableInteractionSource() }
    val urlFocused by urlInteraction.collectIsFocusedAsState()
    val urlError = !urlFocused && draft.serverUrl.isNotBlank() && !isValidServerUrl(draft.serverUrl)

    PreferenceSection {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = draft.serverUrl,
                    onValueChange = { draft.serverUrl = it },
                    label = "服务器地址",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = urlInteraction,
                    leadingIcon = { FieldLeadingIcon(Cloud, "云端") },
                )
                if (urlError) {
                    Text(
                        text = "请输入合法的地址，例如 https://example.com/dav",
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
            TextField(
                value = draft.username,
                onValueChange = { draft.username = it },
                label = "用户名",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { FieldLeadingIcon(Person, "用户") },
            )
            TextField(
                value = draft.password,
                onValueChange = { draft.password = it },
                label = "密码",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation =
                    if (draft.passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { FieldLeadingIcon(MiuixIcons.Lock, "密码") },
                trailingIcon = {
                    IconButton(
                        onClick = { draft.passwordVisible = !draft.passwordVisible },
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(
                            imageVector = if (draft.passwordVisible) MiuixIcons.Show else MiuixIcons.Hide,
                            contentDescription = if (draft.passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
        }
        ArrowPreference(
            title = "测试连接",
            summary = if (busy) "正在连接" else null,
            enabled = !busy,
            onClick = onTest,
        )
        ArrowPreference(
            title = "立即备份",
            summary =
                when {
                    busy -> "正在处理"
                    settings.lastError.isNotBlank() -> settings.lastError
                    settings.lastTime.isNotBlank() -> "上次备份 ${settings.lastTime}"
                    else -> null
                },
            enabled = !busy,
            onClick = onBackupNow,
        )
        SwitchPreference(
            checked = settings.autoBackup,
            onCheckedChange = onToggleAuto,
            title = "启动时自动备份",
            summary = "启动时检查一次，内容有变化时新增备份",
        )
        OverlayDropdownPreference(
            title = "备份保留数量",
            summary = "超出后删除最旧的一份",
            items = BackupSettings.KEEP_OPTIONS.map { "$it 份" },
            selectedIndex = WebDavSettings.keepIndexOf(settings.keepCount),
            onSelectedIndexChange = onKeepChange,
        )
    }

    BackupFileSection(
        hint =
            when {
                !settings.configured -> "还未配置账号信息"
                error.isNotBlank() -> error
                loading && files == null -> "正在读取…"
                else -> null
            },
        files = files,
        onOpenFile = onOpenFile,
    )
    RestoreHint()
}

/** 输入框前置图标 */
@Composable
private fun FieldLeadingIcon(icon: ImageVector, description: String) {
    Icon(imageVector = icon, contentDescription = description, modifier = Modifier.padding(horizontal = 12.dp))
}

/** 合法服务器地址，必须是 http(s) 加主机名 */
private fun isValidServerUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}
