package com.hyperschedule.app.ui.page.settings.backup

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.R
import com.hyperschedule.app.data.backup.BACKUP_FOLDER
import com.hyperschedule.app.data.backup.BackupFile
import com.hyperschedule.app.data.backup.S3_DEFAULT_REGION
import com.hyperschedule.app.data.backup.backupFolderError
import com.hyperschedule.app.data.backup.s3BucketError
import com.hyperschedule.app.data.backup.s3RegionError
import com.hyperschedule.app.data.local.BackupSettings
import com.hyperschedule.app.data.local.S3Settings
import com.hyperschedule.app.data.local.WebDavSettings
import com.hyperschedule.app.theme.SectionSpacing
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.pref.ArrowPreference
import com.hyperschedule.app.ui.component.pref.OverlayDropdownPreference
import com.hyperschedule.app.ui.component.pref.SwitchPreference
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 远端备份页的输入项，WebDAV 与 S3 只有字段不同 */
private class RemoteField(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
    val secret: Boolean = false,
    val error: String? = null,
    /** 失焦时为空就回填这个值，路径类字段用它 */
    val blankFallback: String? = null,
    val onChange: (String) -> Unit,
)

@Composable
internal fun WebDavBackupTab(
    settings: WebDavSettings,
    draft: WebDavDraft,
    list: RemoteList,
    busy: Boolean,
    onTest: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onKeepChange: (Int) -> Unit,
    onOpenFile: (BackupFile) -> Unit,
) {
    RemoteBackupTab(
        summary = settings.summary(),
        list = list,
        busy = busy,
        fields =
            listOf(
                RemoteField(
                    label = "服务器地址",
                    value = draft.serverUrl,
                    icon = ImageVector.vectorResource(R.drawable.ic_cloud),
                    error = serverUrlError(draft.serverUrl),
                ) { draft.serverUrl = it },
                RemoteField(label = "用户名", value = draft.username, icon = ImageVector.vectorResource(R.drawable.ic_person)) { draft.username = it },
                RemoteField(label = "密码", value = draft.password, icon = MiuixIcons.Lock, secret = true) {
                    draft.password = it
                },
                RemoteField(
                    label = "备份路径",
                    value = draft.folder,
                    icon = MiuixIcons.Folder,
                    error = backupFolderError(draft.folder),
                    blankFallback = BACKUP_FOLDER,
                ) { draft.folder = it },
            ),
        onTest = onTest,
        onToggleAuto = onToggleAuto,
        onBackupNow = onBackupNow,
        onKeepChange = onKeepChange,
        onOpenFile = onOpenFile,
    )
}

@Composable
internal fun S3BackupTab(
    settings: S3Settings,
    draft: S3Draft,
    list: RemoteList,
    busy: Boolean,
    onTest: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onTogglePathStyle: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onKeepChange: (Int) -> Unit,
    onOpenFile: (BackupFile) -> Unit,
) {
    RemoteBackupTab(
        summary = settings.summary(),
        list = list,
        busy = busy,
        fields =
            listOf(
                RemoteField(
                    label = "端点",
                    value = draft.endpoint,
                    icon = ImageVector.vectorResource(R.drawable.ic_cloud),
                    error = serverUrlError(draft.endpoint),
                ) { draft.endpoint = it },
                RemoteField(label = "访问密钥 ID", value = draft.accessKeyId, icon = ImageVector.vectorResource(R.drawable.ic_person)) {
                    draft.accessKeyId = it
                },
                RemoteField(
                    label = "机密访问密钥",
                    value = draft.secretAccessKey,
                    icon = MiuixIcons.Lock,
                    secret = true,
                ) { draft.secretAccessKey = it },
                RemoteField(
                    label = "存储桶",
                    value = draft.bucket,
                    icon = ImageVector.vectorResource(R.drawable.ic_bucket),
                    error = s3BucketError(draft.bucket),
                ) { draft.bucket = it },
                RemoteField(
                    label = "区域",
                    value = draft.region,
                    icon = MiuixIcons.MapAlbum,
                    error = s3RegionError(draft.region),
                    blankFallback = S3_DEFAULT_REGION,
                ) { draft.region = it },
                RemoteField(
                    label = "备份路径",
                    value = draft.folder,
                    icon = MiuixIcons.Folder,
                    error = backupFolderError(draft.folder),
                    blankFallback = BACKUP_FOLDER,
                ) { draft.folder = it },
            ),
        extraRows = {
            SwitchPreference(
                checked = settings.pathStyle,
                onCheckedChange = onTogglePathStyle,
                title = "路径风格地址",
                summary = "桶名写在地址里，多数第三方服务需要，关闭则放进域名",
            )
        },
        onTest = onTest,
        onToggleAuto = onToggleAuto,
        onBackupNow = onBackupNow,
        onKeepChange = onKeepChange,
        onOpenFile = onOpenFile,
    )
}

/** 远端备份页的公共排版，WebDAV 与 S3 共用 */
@Composable
private fun RemoteBackupTab(
    summary: RemoteSummary,
    list: RemoteList,
    busy: Boolean,
    fields: List<RemoteField>,
    onTest: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onKeepChange: (Int) -> Unit,
    onOpenFile: (BackupFile) -> Unit,
    extraRows: (@Composable () -> Unit)? = null,
) {
    PreferenceSection {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            fields.forEach { field -> RemoteFieldRow(field) }
        }
        extraRows?.invoke()
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
                    summary.lastError.isNotBlank() -> summary.lastError
                    summary.lastTime.isNotBlank() -> "上次备份 ${summary.lastTime}"
                    else -> null
                },
            enabled = !busy,
            onClick = onBackupNow,
        )
        SwitchPreference(
            checked = summary.autoBackup,
            onCheckedChange = onToggleAuto,
            title = "启动时自动备份",
            summary = "启动时检查一次，内容有变化时新增备份",
        )
        OverlayDropdownPreference(
            title = "备份保留数量",
            summary = "超出后删除最旧的一份",
            items = BackupSettings.KEEP_OPTIONS.map { "$it 份" },
            selectedIndex = BackupSettings.keepIndexOf(summary.keepCount),
            onSelectedIndexChange = onKeepChange,
        )
    }

    BackupFileSection(
        hint =
            when {
                !summary.configured -> "还未配置账号信息"
                list.error.isNotBlank() -> list.error
                list.loading && list.files == null -> "正在读取…"
                else -> null
            },
        files = list.files,
        onOpenFile = onOpenFile,
    )
    RestoreHint()
}

/** 实时校验，非法时描边变红、下方给提示 */
@Composable
private fun RemoteFieldRow(field: RemoteField) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }
    // 清空后离开输入框时补回默认值，不留空路径
    LaunchedEffect(focused) {
        val fallback = field.blankFallback
        if (!focused && field.value.isBlank() && fallback != null) field.onChange(fallback)
    }
    val icon = field.icon
    val leadingIcon: (@Composable () -> Unit)? =
        icon?.let { vector -> { FieldLeadingIcon(vector, field.label) } }
    val trailingIcon: (@Composable () -> Unit)? =
        if (field.secret) {
            {
                IconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Icon(
                        imageVector = if (revealed) MiuixIcons.Show else MiuixIcons.Hide,
                        contentDescription = if (revealed) "隐藏" else "显示",
                    )
                }
            }
        } else {
            null
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = field.value,
            onValueChange = field.onChange,
            label = field.label,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interaction,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation =
                if (field.secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions =
                if (field.secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
            colors =
                TextFieldDefaults.textFieldColors(
                    borderColor =
                        if (field.error != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                ),
        )
        if (field.error != null) {
            Text(
                text = field.error,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
private fun FieldLeadingIcon(icon: ImageVector, description: String) {
    Icon(imageVector = icon, contentDescription = description, modifier = Modifier.padding(horizontal = 12.dp))
}
