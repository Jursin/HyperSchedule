package top.jursin.hyperschedule.ui.page.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import top.jursin.hyperschedule.data.local.AppLog
import top.jursin.hyperschedule.data.local.LogKeep
import top.jursin.hyperschedule.data.local.LogLevel
import top.jursin.hyperschedule.data.local.SettingsStore
import top.jursin.hyperschedule.theme.CardCornerRadius
import top.jursin.hyperschedule.theme.CardInsidePadding
import top.jursin.hyperschedule.ui.component.ConfirmDialog
import top.jursin.hyperschedule.ui.component.GlassFab
import top.jursin.hyperschedule.ui.component.HintText
import top.jursin.hyperschedule.ui.component.PreferenceItemSection
import top.jursin.hyperschedule.ui.component.PreferenceScreen
import top.jursin.hyperschedule.ui.component.PreferenceSection
import top.jursin.hyperschedule.ui.component.pref.DropdownPreference
import top.jursin.hyperschedule.ui.component.toast
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.Locale

private fun File.logLabel(): String = name.removePrefix("log_").removeSuffix(".txt")

private fun File.sizeLabel(): String =
    when {
        length() >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", length() / 1024.0 / 1024.0)
        length() >= 1024 -> String.format(Locale.ROOT, "%.1f KB", length() / 1024.0)
        else -> "${length()} B"
    }

@Composable
fun LogPage(
    store: SettingsStore,
    appBlur: Boolean,
    onBack: () -> Unit,
) {
    val state = store.state
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val files = remember(revision) { AppLog.files() }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    fun shareLog(file: File) {
        val uri =
            runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
                .getOrNull()
        if (uri == null) {
            context.toast("分享失败")
            return
        }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "HyperSchedule 日志 ${file.logLabel()}")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { context.toast("分享失败：${it.message ?: "未知错误"}") }
    }

    PreferenceScreen(
        title = "日志",
        appBlur = appBlur,
        onBack = onBack,
        floatingActionButton = {
            GlassFab(
                onClick = { showClearDialog = true },
                color = MiuixTheme.colorScheme.error,
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.Delete,
                    contentDescription = "清空日志",
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        PreferenceSection {
            DropdownPreference(
                title = "日志级别",
                items = LogLevel.entries.map { it.title },
                selectedIndex = LogLevel.entries.indexOf(state.logLevel),
                onSelectedIndexChange = { index ->
                    store.update { it.copy(logLevel = LogLevel.entries[index]) }
                    AppLog.onSettingsChanged()
                },
            )
            DropdownPreference(
                title = "保留数量",
                items = LogKeep.LABELS,
                selectedIndex = LogKeep.indexOf(state.logKeep),
                onSelectedIndexChange = { index ->
                    store.update { it.copy(logKeep = LogKeep.OPTIONS[index]) }
                    AppLog.onSettingsChanged()
                },
            )
        }

        PreferenceItemSection(title = "日志文件") {
            if (files.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardCornerRadius,
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    HintText(
                        text = "暂无日志文件",
                        modifier = Modifier.padding(16.dp),
                        style = MiuixTheme.textStyles.main,
                    )
                }
            } else {
                files.forEach { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardCornerRadius,
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = CardInsidePadding, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = file.logLabel(), style = MiuixTheme.textStyles.headline1)
                                Text(
                                    text = file.sizeLabel(),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            IconButton(onClick = { shareLog(file) }) {
                                Icon(imageVector = MiuixIcons.Glass.Share, contentDescription = "分享日志")
                            }
                            IconButton(
                                onClick = {
                                    deleteTarget = file
                                    showDeleteDialog = true
                                },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Glass.Delete,
                                    contentDescription = "删除日志",
                                    tint = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    ConfirmDialog(
        show = showClearDialog,
        title = "清空日志",
        message = "全部日志文件将被删除，确定清空？",
        appBlur = appBlur,
        destructive = true,
        confirmText = "清空",
        onDismiss = { showClearDialog = false },
        onConfirm = {
            showClearDialog = false
            AppLog.clear()
            revision++
        },
    )

    ConfirmDialog(
        show = showDeleteDialog,
        title = "删除日志",
        message = "「${deleteTarget?.logLabel().orEmpty()}」删除后无法恢复，确定删除？",
        appBlur = appBlur,
        destructive = true,
        confirmText = "删除",
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            deleteTarget?.let { runCatching { AppLog.delete(it) } }
            showDeleteDialog = false
            revision++
        },
    )
}
