package top.jursin.hyperschedule.ui.page.settings.backup

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.R
import top.jursin.hyperschedule.data.backup.BACKUP_FOLDER
import top.jursin.hyperschedule.data.backup.BackupFile
import top.jursin.hyperschedule.data.backup.S3_DEFAULT_REGION
import top.jursin.hyperschedule.data.backup.backupFolderError
import top.jursin.hyperschedule.data.backup.normalizeS3Name
import top.jursin.hyperschedule.data.backup.trimBackupFolderSpaces
import top.jursin.hyperschedule.data.local.RemoteAccountInfo
import top.jursin.hyperschedule.data.local.S3Settings
import top.jursin.hyperschedule.data.local.WebDavSettings
import top.jursin.hyperschedule.theme.SectionSpacing
import top.jursin.hyperschedule.ui.component.PasswordMask
import top.jursin.hyperschedule.ui.component.PreferenceSection
import top.jursin.hyperschedule.ui.component.pref.ArrowPreference
import top.jursin.hyperschedule.ui.component.pref.SwitchPreference
import top.jursin.hyperschedule.ui.component.rememberSyncedInput
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Contacts
import top.yukonga.miuix.kmp.icon.glass.FolderOutline
import top.yukonga.miuix.kmp.icon.glass.Hide
import top.yukonga.miuix.kmp.icon.glass.LockOutline
import top.yukonga.miuix.kmp.icon.glass.MapAlbum
import top.yukonga.miuix.kmp.icon.glass.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 远端备份页的输入项，WebDAV 与 S3 只有字段不同 */
private class RemoteField(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
    val iconSize: Dp = 24.dp,
    val secret: Boolean = false,
    val error: String? = null,
    /** 失焦时为空就回填这个值，路径类字段用它 */
    val blankFallback: String? = null,
    /** 失焦时去掉空格与斜杠 */
    val onBlur: ((String) -> String)? = null,
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
        summary = settings,
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
                RemoteField(label = "用户名", value = draft.username, icon = MiuixIcons.Glass.Contacts, iconSize = 26.dp) { draft.username = it },
                RemoteField(label = "密码", value = draft.password, icon = MiuixIcons.Glass.LockOutline, iconSize = 26.dp, secret = true) {
                    draft.password = it
                },
                RemoteField(
                    label = "备份路径",
                    value = draft.folder,
                    icon = MiuixIcons.Glass.FolderOutline,
                    iconSize = 26.dp,
                    error = backupFolderError(draft.folder),
                    blankFallback = BACKUP_FOLDER,
                    onBlur = ::trimBackupFolderSpaces,
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
        summary = settings,
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
                RemoteField(label = "访问密钥 ID", value = draft.accessKeyId, icon = MiuixIcons.Glass.Contacts, iconSize = 26.dp) {
                    draft.accessKeyId = it
                },
                RemoteField(
                    label = "机密访问密钥",
                    value = draft.secretAccessKey,
                    icon = MiuixIcons.Glass.LockOutline,
                    iconSize = 26.dp,
                    secret = true,
                ) { draft.secretAccessKey = it },
                RemoteField(
                    label = "存储桶",
                    value = draft.bucket,
                    icon = ImageVector.vectorResource(R.drawable.ic_bucket),
                    onBlur = ::normalizeS3Name,
                ) { draft.bucket = it },
                RemoteField(
                    label = "区域",
                    value = draft.region,
                    icon = MiuixIcons.Glass.MapAlbum,
                    iconSize = 26.dp,
                    blankFallback = S3_DEFAULT_REGION,
                    onBlur = ::normalizeS3Name,
                ) { draft.region = it },
                RemoteField(
                    label = "备份路径",
                    value = draft.folder,
                    icon = MiuixIcons.Glass.FolderOutline,
                    iconSize = 26.dp,
                    error = backupFolderError(draft.folder),
                    blankFallback = BACKUP_FOLDER,
                    onBlur = ::trimBackupFolderSpaces,
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
    summary: RemoteAccountInfo,
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
    val stamp = summary.stamp
    PreferenceSection {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            fields.forEach { field -> key(field.label) { RemoteFieldRow(field) } }
        }
        extraRows?.invoke()
        ArrowPreference(
            title = "测试连接",
            summary = if (busy) "正在连接" else null,
            enabled = !busy,
            onClick = onTest,
        )
        BackupActionsSection(
            stamp = stamp,
            busy = busy,
            onBackupNow = onBackupNow,
            onToggleAuto = onToggleAuto,
            onKeepChange = onKeepChange,
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

/** 失焦后才校验，非法时描边变红、下方给提示 */
@Composable
private fun RemoteFieldRow(field: RemoteField) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }
    val error = if (focused) null else field.error
    val input = rememberSyncedInput(field.value, field.onChange)
    // 清空后离开输入框时补回默认值，不留空路径
    LaunchedEffect(focused) {
        if (focused) return@LaunchedEffect
        val cleaned = field.onBlur?.invoke(input.text.toString()) ?: input.text.toString()
        val fallback = field.blankFallback
        val next = if (cleaned.isBlank() && fallback != null) fallback else cleaned
        if (next != input.text.toString()) input.setTextAndPlaceCursorAtEnd(next)
    }
    val icon = field.icon
    val leadingIcon: (@Composable () -> Unit)? =
        icon?.let { vector -> { FieldLeadingIcon(vector, field.label, field.iconSize) } }
    val trailingIcon: (@Composable () -> Unit)? =
        if (field.secret) {
            {
                IconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Icon(
                        imageVector = if (revealed) MiuixIcons.Glass.Show else MiuixIcons.Glass.Hide,
                        contentDescription = if (revealed) "隐藏" else "显示",
                    )
                }
            }
        } else {
            null
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            state = input,
            label = field.label,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interaction,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            outputTransformation = if (field.secret && !revealed) PasswordMask else null,
            keyboardOptions =
                if (field.secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
            colors =
                TextFieldDefaults.textFieldColors(
                    borderColor = if (error != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                ),
        )
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
private fun FieldLeadingIcon(icon: ImageVector, description: String, iconSize: Dp) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = Modifier.padding(horizontal = 12.dp).size(iconSize),
    )
}
