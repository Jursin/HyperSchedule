package com.hyperschedule.app.ui.page.backup

import androidx.compose.runtime.Composable
import com.hyperschedule.app.data.backup.BackupFile
import com.hyperschedule.app.theme.BackupSettings
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.pref.ArrowPreference
import com.hyperschedule.app.ui.component.pref.MultilineArrowPreference
import com.hyperschedule.app.ui.component.pref.OverlayDropdownPreference
import com.hyperschedule.app.ui.component.pref.SwitchPreference

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
        ArrowPreference(
            title = "立即备份",
            summary =
                when {
                    busy -> "正在处理"
                    dirUsable == false -> "备份路径已失效，请重新选择"
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
                dirLabel.isBlank() -> "还未选择备份路径"
                dirUsable == false -> "备份路径已失效（文件夹被删除或授权被系统回收），请重新选择"
                else -> null
            },
        files = files,
        onOpenFile = onOpenFile,
    )
    RestoreHint()
}
