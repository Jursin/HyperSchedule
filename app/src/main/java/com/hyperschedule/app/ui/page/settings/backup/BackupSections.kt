package com.hyperschedule.app.ui.page.settings.backup

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.backup.BackupFile
import com.hyperschedule.app.data.backup.sizeLabel
import com.hyperschedule.app.data.backup.title
import com.hyperschedule.app.ui.component.ConfirmDialog
import com.hyperschedule.app.ui.component.Container
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.pref.ArrowPreference
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 本地与云端共用的文件列表，hint 优先 */
@Composable
internal fun BackupFileSection(
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
internal fun RestoreHint() {
    Container(text = "恢复会覆盖当前全部课表与设置，覆盖前会自动备份一次。")
}

@Composable
internal fun SectionHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(16.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
internal fun RestoreConfirmDialog(
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
