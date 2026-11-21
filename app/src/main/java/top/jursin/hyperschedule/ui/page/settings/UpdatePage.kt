package top.jursin.hyperschedule.ui.page.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.data.local.SettingsStore
import top.jursin.hyperschedule.getAppVersion
import top.jursin.hyperschedule.getAppVersionCode
import top.jursin.hyperschedule.schedule.cjkDigitGap
import top.jursin.hyperschedule.theme.SectionSpacing
import top.jursin.hyperschedule.theme.dangerColor
import top.jursin.hyperschedule.theme.successColor
import top.jursin.hyperschedule.theme.warningColor
import top.jursin.hyperschedule.ui.component.ConfirmDialog
import top.jursin.hyperschedule.ui.component.MarkdownText
import top.jursin.hyperschedule.ui.component.PreferenceItemSection
import top.jursin.hyperschedule.ui.component.PreferenceScreen
import top.jursin.hyperschedule.ui.component.PreferenceSection
import top.jursin.hyperschedule.ui.component.SelectableEditRow
import top.jursin.hyperschedule.ui.component.WideActionButton
import top.jursin.hyperschedule.ui.component.pref.ArrowPreference
import top.jursin.hyperschedule.ui.component.pref.SwitchPreference
import top.jursin.hyperschedule.ui.component.rememberSyncedInput
import top.jursin.hyperschedule.update.ChangelogEntry
import top.jursin.hyperschedule.update.SourceSpeed
import top.jursin.hyperschedule.update.UpdateController
import top.jursin.hyperschedule.update.UpdateDialogState
import top.jursin.hyperschedule.update.UpdateSource
import top.jursin.hyperschedule.update.UpdateSpeed
import top.jursin.hyperschedule.update.mirrorHost
import top.jursin.hyperschedule.update.proxyPrefix
import top.jursin.hyperschedule.update.sourcesOf
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.SearchDevice
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.util.Locale

@Composable
fun UpdatePage(
    store: SettingsStore,
    controller: UpdateController,
    onOpenLog: () -> Unit,
    onBack: () -> Unit,
) {
    val state = store.state
    val context = LocalContext.current
    val sources = remember(state.updateMirrors) { sourcesOf(state.updateMirrors) }
    // 没有匹配项时退回直连
    val current = sources.firstOrNull { isSelected(it, state.updateSource) } ?: sources.first()
    // 正在编辑的镜像，null 表示新增
    var editing by remember { mutableStateOf<UpdateSource?>(null) }
    var showMirrorDialog by remember { mutableStateOf(false) }
    var mirrorInput by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<UpdateSource?>(null) }
    fun openEditor(source: UpdateSource) {
        editing = source
        mirrorInput = source.host
        showMirrorDialog = true
    }

    // 离开页面清掉测速结果
    DisposableEffect(Unit) { onDispose { UpdateSpeed.clear() } }

    PreferenceScreen(title = "更新", appBlur = state.blur, onBack = onBack) {
        PreferenceSection {
            ArrowPreference(
                title = "检查更新",
                summary = if (controller.checking) "正在检查" else "当前版本 v${getAppVersion(context)} (${getAppVersionCode(context)})",
                onClick = {
                    // 手动检查也记一次日期，避免重启后再静默查一遍
                    store.update { it.copy(lastUpdateCheck = LocalDate.now().toString()) }
                    controller.checkForUpdate(state.acceptPrerelease, proxyPrefix(state.updateSource))
                },
            )
            ArrowPreference(
                title = "更新日志",
                onClick = onOpenLog,
            )
            SwitchPreference(
                checked = state.acceptPrerelease,
                onCheckedChange = { store.update { it.copy(acceptPrerelease = it.acceptPrerelease.not()) } },
                title = "接收测试版",
            )
            SwitchPreference(
                checked = state.checkUpdateOnStart,
                onCheckedChange = { store.update { it.copy(checkUpdateOnStart = it.checkUpdateOnStart.not()) } },
                title = "自动检查更新",
                summary = "打开应用时自动检查新版本",
            )
        }

        PreferenceItemSection("更新源") {
            sources.forEach { source ->
                val speed = UpdateSpeed.speedOf(source)
                val custom = !source.builtIn
                SelectableEditRow(
                    title = source.title,
                    summary = speedText(speed),
                    summaryColor = speedColor(speed),
                    selected = source == current,
                    onSelect = { store.update { it.copy(updateSource = source.key) } },
                    onEdit = if (custom) ({ openEditor(source) }) else null,
                    onDelete = if (custom) ({ pendingDelete = source }) else null,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                WideActionButton(
                    text = "添加镜像源",
                    icon = MiuixIcons.Glass.Add,
                    onClick = {
                        editing = null
                        mirrorInput = ""
                        showMirrorDialog = true
                    },
                    modifier = Modifier.weight(1f),
                )
                WideActionButton(
                    text = if (UpdateSpeed.testing) "测速中" else "立即测速",
                    icon = MiuixIcons.Glass.SearchDevice,
                    onClick = { UpdateSpeed.testAll(sources) },
                    modifier = Modifier.weight(1f),
                    containerColor = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    if (showMirrorDialog) {
        // 点确认才校验，不合法时提示并禁用确定，改了输入再恢复
        var problem by remember { mutableStateOf<String?>(null) }
        ConfirmDialog(
            title = if (editing == null) "添加镜像源" else "编辑镜像源",
            appBlur = state.blur,
            confirmEnabled = problem == null,
            onDismiss = { showMirrorDialog = false },
            onConfirm = {
                val host = mirrorHost(mirrorInput)
                val duplicated = host != null && sources.any { it.host == host && it.host != editing?.host }
                if (host == null) {
                    problem = "地址不合法"
                } else if (duplicated) {
                    problem = "该镜像源已在列表中"
                } else {
                    val previous = editing
                    store.update { s ->
                        val mirrors = s.updateMirrors.map { if (it == previous?.host) host else it }
                        val keeping = previous != null && isSelected(previous, s.updateSource)
                        s.copy(
                            updateMirrors = if (host in mirrors) mirrors else mirrors + host,
                            updateSource = if (keeping) host else s.updateSource,
                        )
                    }
                    showMirrorDialog = false
                }
            },
        ) {
            val input =
                rememberSyncedInput(mirrorInput) {
                    mirrorInput = it
                    problem = null
                }
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTextField(
                    state = input,
                    label = "域名",
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        GlassTextFieldDefaults.textFieldColors(
                            borderColor = if (problem != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                        ),
                )
                problem?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "删除镜像源",
            message = "「${target.host}」删除后无法恢复，确定删除？",
            appBlur = state.blur,
            destructive = true,
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                store.update { s ->
                    s.copy(
                        updateMirrors = s.updateMirrors - target.host,
                        updateSource = if (isSelected(target, s.updateSource)) "" else s.updateSource,
                    )
                }
            },
        )
    }
}

private fun isSelected(source: UpdateSource, current: String): Boolean = source.key == current

private fun speedText(speed: SourceSpeed): String =
    when (speed) {
        SourceSpeed.Untested -> "未测速"
        SourceSpeed.Testing -> "测速中"
        is SourceSpeed.Reachable -> formatSpeed(speed.bytesPerSecond)
        SourceSpeed.Timeout -> "timeout"
    }

private fun formatSpeed(bytesPerSecond: Long): String {
    val kiloBytes = bytesPerSecond / 1024.0
    return if (kiloBytes >= 1024) {
        "${"%.1f".format(Locale.US, kiloBytes / 1024)} MB/s"
    } else {
        "${"%.0f".format(Locale.US, kiloBytes)} KB/s"
    }
}

/** 1 MB/s 以上绿色，0.2 MB/s 以上黄色，再慢或超时红色 */
@Composable
private fun speedColor(speed: SourceSpeed): Color? =
    when (speed) {
        is SourceSpeed.Reachable if speed.bytesPerSecond >= 1024 * 1024 -> successColor()
        is SourceSpeed.Reachable if speed.bytesPerSecond >= 200 * 1024 -> warningColor()
        is SourceSpeed.Reachable -> dangerColor()
        SourceSpeed.Timeout -> dangerColor()
        else -> null
    }

/** 更新流程的弹窗，挂在根组合上以便任意页面都能看到 */
@Composable
fun UpdateDialogs(controller: UpdateController, appBlur: Boolean) {
    when (val dialog = controller.dialog) {
        is UpdateDialogState.Available ->
            ConfirmDialog(
                title = "检测到新版本",
                summary = "v${dialog.version}",
                appBlur = appBlur,
                confirmText = "更新",
                onConfirm = controller::startUpdate,
                onDismiss = controller::dismissDialog,
            ) {
                ReleaseNotesView(notes = dialog.notes)
            }

        is UpdateDialogState.Downloading ->
            ConfirmDialog(
                title = "正在下载更新",
                appBlur = appBlur,
                confirmText = "停止下载",
                cancelText = null,
                destructive = true,
                onConfirm = controller::stopUpdate,
                // 点外部或返回只收起弹窗，下载继续在通知栏显示
                onDismiss = controller::hideProgress,
            ) {
                LinearProgressIndicator(progress = dialog.progress, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "已下载${(dialog.progress * 100).toInt()}%".cjkDigitGap(),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

        UpdateDialogState.None, is UpdateDialogState.ReadyToInstall -> Unit
    }
}

/** 更新日志 */
@Composable
private fun ReleaseNotesView(notes: List<ChangelogEntry>) {
    val maxHeight = (LocalWindowInfo.current.containerDpSize.height * 0.45f).coerceAtLeast(180.dp)
    if (notes.isEmpty()) {
        Text(
            text = "该版本未提供更新日志",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        return
    }
    val markdown =
        remember(notes) {
            notes.joinToString("\n\n") { note ->
                if (notes.size > 1) "## v${note.version}\n\n${note.body}" else note.body
            }
        }
    MarkdownText(
        markdown = markdown,
        modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
    )
}
