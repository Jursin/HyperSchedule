package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.useLargeScreenWindow
import com.miuix.hyperschedule.ui.effect.WindowBlurEffect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 确认对话框：底部一排等宽按钮，[actions] 可整排替换成自定义按钮 */
@Composable
fun ConfirmDialog(
    title: String,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确定",
    confirmEnabled: Boolean = true,
    cancelText: String? = "取消",
    destructive: Boolean = false,
    message: String? = null,
    summary: String? = null,
    summaryColor: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
    endAction: (@Composable () -> Unit)? = null,
    show: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    WindowDialog(
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        title = if (endAction == null) title else null,
        summary = if (endAction == null) summary else null,
        summaryColor = summaryColor,
        // 平板居中、手机贴底，和抽屉、整页浮窗同一套判定
        largeScreen = useLargeScreenWindow(),
        content = {
            WindowBlurEffect(useBlur = appBlur)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                if (endAction != null) {
                    DialogTitleRow(title = title, summary = summary, summaryColor = summaryColor, action = endAction)
                }
                if (message != null) Text(text = message)
                // 正文区用权重占掉剩余高度，超出时自己滚动，按钮行始终拿得到高度
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) {
                    content?.invoke(this)
                }
                DialogActionRow {
                    if (actions != null) {
                        actions()
                    } else {
                        if (cancelText != null) {
                            val dismiss = LocalDismissState.current
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = { dismiss?.invoke() },
                                text = cancelText,
                            )
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = onConfirm,
                            text = confirmText,
                            enabled = confirmEnabled,
                            colors =
                                if (destructive) {
                                    ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                                } else {
                                    ButtonDefaults.textButtonColorsPrimary()
                                },
                        )
                    }
                }
            }
        },
    )
}

/** 纯选择对话框：若干等宽按钮，用于「多选一」的确认场景 */
@Composable
fun ChoiceDialog(
    title: String,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    choices: List<DialogChoice>,
    message: String? = null,
) {
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        onDismiss = onDismiss,
        onConfirm = {},
        message = message,
        actions = {
            choices.forEach { choice ->
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        choice.onClick()
                    },
                    text = choice.text,
                    enabled = choice.enabled,
                    colors =
                        if (choice.destructive) {
                            ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                )
            }
        },
    )
}

/** [ChoiceDialog] 的一个选项；[destructive] 用错误色，[enabled] 为 false 时置灰 */
data class DialogChoice(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/** 有未保存改动时返回：确认后丢弃并退出编辑 */
@Composable
fun DiscardChangesDialog(
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    ConfirmDialog(
        title = "放弃修改",
        message = "本次编辑尚未保存，返回后修改将丢失。",
        appBlur = appBlur,
        destructive = true,
        confirmText = "放弃",
        onDismiss = onDismiss,
        onConfirm = onDiscard,
    )
}

/** 拖动落点冲突：覆盖或交换（只有一门冲突课程时才能交换）；取消靠返回键，不占按钮位 */
@Composable
fun ConflictDialog(
    appBlur: Boolean,
    canSwap: Boolean,
    onDismiss: () -> Unit,
    onSwap: () -> Unit,
    onOverwrite: () -> Unit,
) {
    ChoiceDialog(
        title = "位置冲突",
        appBlur = appBlur,
        onDismiss = onDismiss,
        message = "待移动课程与目标课程冲突，请选择操作",
        choices =
            listOf(
                DialogChoice(text = "交换", onClick = onSwap, enabled = canSwap),
                DialogChoice(text = "覆盖", onClick = onOverwrite, destructive = true),
            ),
    )
}

/** 添加 / 重命名课表：名称为空时确定按钮不可点，最多 20 字 */
@Composable
fun TableNameDialog(
    title: String,
    initialName: String = "",
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        confirmEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(name.trim()) },
    ) {
        TextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = "课表名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 新建课表 */
@Composable
fun CreateTableDialog(
    repository: ScheduleRepository,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onCreated: (tableId: String) -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    TableNameDialog(
        title = "新建课表",
        appBlur = appBlur,
        onDismiss = { if (!creating) onDismiss() },
        onConfirm = { name ->
            if (creating) return@TableNameDialog
            creating = true
            scope.launch {
                runCatching { repository.createTable(name) }
                    .onSuccess(onCreated)
                    .onFailure {
                        creating = false
                        context.toast("新建课表失败：${it.message ?: "未知错误"}")
                        onFailed()
                    }
            }
        },
    )
}

/** 分享码 / 口令输入：取回期间禁用确定，失败原因就地显示 */
@Composable
fun ShareCodeDialog(
    title: String,
    hint: String,
    appBlur: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    ConfirmDialog(
        title = title,
        appBlur = appBlur,
        confirmEnabled = input.isNotBlank() && !busy,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(input.trim()) },
    ) {
        TextField(
            value = input,
            onValueChange = { input = it.take(2000) },
            label = hint,
            useLabelAsPlaceholder = true,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        if (busy) {
            Text(text = "正在获取课表…", style = MiuixTheme.textStyles.body2)
        }
        error?.let {
            Text(
                text = it,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

/** 对话框标题右侧的删除按钮 */
@Composable
fun DialogDeleteAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Delete,
            contentDescription = "删除",
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.error,
        )
    }
}

/** 底部按钮行：等宽排布，与上方内容留出额外间距 */
@Composable
private fun DialogActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 带右侧操作的标题行 */
@Composable
private fun DialogTitleRow(
    title: String,
    summary: String?,
    summaryColor: Color,
    action: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    textAlign = TextAlign.Center,
                    color = summaryColor,
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) { action() }
    }
}
