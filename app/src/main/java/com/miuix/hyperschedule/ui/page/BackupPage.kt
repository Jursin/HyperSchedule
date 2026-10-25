package com.miuix.hyperschedule.ui.page

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import java.net.URI
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val KeepCountOptions = listOf("3", "5", "10")

/** 两个 Tab 的表单状态，提到页面层以便切换 Tab 不丢失输入 */
private class BackupDraft {
    var selectedTab by mutableIntStateOf(0)
    var backupPath by mutableStateOf("/Documents/HyperSchedule")
    var localAutoBackup by mutableStateOf(false)
    var localKeepIndex by mutableIntStateOf(1)
    var serverUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordVisible by mutableStateOf(false)
    var webDavAutoBackup by mutableStateOf(false)
    var webDavKeepIndex by mutableIntStateOf(1)
}

/** 备份与恢复：本地目录备份与 WebDAV 远程备份；备份业务待实现 */
@Composable
fun BackupPage(appBlur: Boolean = false, onBack: () -> Unit) {
    val draft = remember { BackupDraft() }
    PreferenceScreen(title = "备份与恢复", appBlur = appBlur, onBack = onBack) {
        TabRowWithContour(
            tabs = listOf("本地", "WebDAV"),
            selectedTabIndex = draft.selectedTab,
            onTabSelected = { draft.selectedTab = it },
        )
        when (draft.selectedTab) {
            0 -> LocalBackupTab(draft)
            else -> WebDavBackupTab(draft)
        }
    }
}

@Composable
private fun LocalBackupTab(draft: BackupDraft) {
    PreferenceSection("备份") {
        ArrowPreference(
            title = "备份路径",
            summary = draft.backupPath,
            onClick = {},
        )
        SwitchPreference(checked = draft.localAutoBackup, onCheckedChange = { draft.localAutoBackup = it }, title = "启动时自动备份")
        BackupActions(draft.localKeepIndex) { draft.localKeepIndex = it }
    }

    BackupFileSection()
}

@Composable
private fun WebDavBackupTab(draft: BackupDraft) {
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
                    leadingIcon = { FieldLeadingIcon(MiuixIcons.CloudFill, "云端") },
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
                leadingIcon = { FieldLeadingIcon(MiuixIcons.Contacts, "用户") },
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
            onClick = {},
        )
        SwitchPreference(checked = draft.webDavAutoBackup, onCheckedChange = { draft.webDavAutoBackup = it }, title = "启动时自动备份")
        BackupActions(draft.webDavKeepIndex) { draft.webDavKeepIndex = it }
    }

    BackupFileSection()
}

/** 两个 Tab 共用的「立即备份 + 保留数量」 */
@Composable
private fun BackupActions(keepIndex: Int, onKeepIndexChange: (Int) -> Unit) {
    ArrowPreference(title = "立即备份", onClick = {})
    OverlayDropdownPreference(
        title = "备份保留数量",
        items = KeepCountOptions,
        selectedIndex = keepIndex,
        onSelectedIndexChange = onKeepIndexChange,
    )
}

/** 备份文件列表：备份功能实现前占位 */
@Composable
private fun BackupFileSection() {
    PreferenceSection("备份文件") {
        Text(
            text = "暂无备份文件",
            modifier = Modifier.padding(16.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 输入框前置图标 */
@Composable
private fun FieldLeadingIcon(icon: ImageVector, description: String) {
    Icon(imageVector = icon, contentDescription = description, modifier = Modifier.padding(horizontal = 12.dp))
}

/** 合法服务器地址：http(s) 协议 + 主机名，可带端口与路径 */
private fun isValidServerUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}
