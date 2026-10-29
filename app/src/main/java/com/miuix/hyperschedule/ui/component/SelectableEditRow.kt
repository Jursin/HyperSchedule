package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.CardCornerRadius
import com.miuix.hyperschedule.theme.CardItemPadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 单选行，[radioAtEnd] 为真时单选放到右侧，编辑删除按钮始终在右侧 */
@Composable
fun SelectableEditRow(
    title: String,
    summary: String? = null,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    radioAtEnd: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
    ) {
        BasicComponent(
            title = title,
            summary = summary,
            insideMargin = CardItemPadding,
            startAction =
                if (radioAtEnd) {
                    null
                } else {
                    { RadioButton(selected = selected, onClick = onSelect) }
                },
            endActions = {
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = MiuixIcons.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    }
                }
                if (radioAtEnd) {
                    RadioButton(selected = selected, onClick = onSelect)
                }
            },
            onClick = onSelect,
            role = Role.RadioButton,
        )
    }
}
