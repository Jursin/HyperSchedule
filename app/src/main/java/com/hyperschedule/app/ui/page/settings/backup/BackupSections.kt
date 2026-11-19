package com.hyperschedule.app.ui.page.settings.backup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.data.backup.BackupFile
import com.hyperschedule.app.data.backup.sizeLabel
import com.hyperschedule.app.data.backup.title
import com.hyperschedule.app.data.local.BackupSettings
import com.hyperschedule.app.data.local.BackupStamp
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.theme.CardCornerRadius
import com.hyperschedule.app.theme.ContainerTintAlpha
import com.hyperschedule.app.theme.accentBlueColor
import com.hyperschedule.app.ui.component.ConfirmDialog
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.pref.ArrowPreference
import com.hyperschedule.app.ui.component.pref.DropdownPreference
import com.hyperschedule.app.ui.component.pref.SwitchPreference
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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

/** 立即备份 / 自动备份 / 保留数量三行，本地与两个云端共用 */
@Composable
internal fun BackupActionsSection(
    stamp: BackupStamp,
    busy: Boolean,
    blockedHint: String? = null,
    onBackupNow: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onKeepChange: (Int) -> Unit,
) {
    ArrowPreference(
        title = "立即备份",
        summary =
            when {
                busy -> "正在处理"
                blockedHint != null -> blockedHint
                stamp.lastError.isNotBlank() -> stamp.lastError
                stamp.lastTime.isNotBlank() -> "上次备份${stamp.lastTime}".cjkDigitGap()
                else -> null
            },
        enabled = !busy,
        onClick = onBackupNow,
    )
    SwitchPreference(
        checked = stamp.autoBackup,
        onCheckedChange = onToggleAuto,
        title = "启动时自动备份",
        summary = "启动时检查一次，内容有变化时新增备份",
    )
    DropdownPreference(
        title = "备份保留数量",
        summary = "超出后删除最旧的一份",
        items = BackupSettings.KEEP_OPTIONS.map { "${it}份".cjkDigitGap() },
        selectedIndex = BackupSettings.keepIndexOf(stamp.keepCount),
        onSelectedIndexChange = onKeepChange,
    )
}

/** 恢复前的风险提示 */
@Composable
internal fun RestoreHint() {
    val colors =
        CardDefaults.defaultColors(
            color = accentBlueColor().copy(alpha = ContainerTintAlpha),
            contentColor = accentBlueColor(),
        )
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = colors,
    ) {
        Text(
            text = "恢复会覆盖当前全部课表与设置，覆盖前会自动备份一次。",
            modifier = Modifier.padding(16.dp),
            style = MiuixTheme.textStyles.body2,
            color = colors.contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
            if (target.parsed.hasData) {
                append("${target.payload.tableCount}张课表 · ${target.payload.courseCount}门课程".cjkDigitGap())
            } else {
                append("仅包含设置")
            }
            target.partialNotice?.let { append("\n").append(it) }
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
