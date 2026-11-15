package com.hyperschedule.app.ui.page.settings.backup

import androidx.compose.runtime.Composable
import com.hyperschedule.app.data.backup.BackupFile
import com.hyperschedule.app.data.local.BackupSettings
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.pref.MultilineArrowPreference

@Composable
internal fun LocalBackupTab(
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
            title = "备份路径",
            summary = dirLabel.ifBlank { "点击选择备份文件夹" },
            onClick = onPickDir,
        )
        BackupActionsSection(
            stamp = backup.stamp,
            busy = busy,
            blockedHint = if (dirUsable == false) "备份路径已失效，请重新选择" else null,
            onBackupNow = onBackupNow,
            onToggleAuto = onToggleAuto,
            onKeepChange = onKeepChange,
        )
    }
    BackupFileSection(
        hint =
            when {
                dirLabel.isBlank() -> "还未选择备份路径"
                dirUsable == false -> "备份路径已失效（文件夹被删除或授权被系统回收），请重新选择"
                else -> null
            },
        files = files,
        onOpenFile = onOpenFile,
    )
    RestoreHint()
}
