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

/** 单选行：左侧单选，右侧编辑与删除；[onEdit]、[onDelete] 为空时不显示对应按钮 */
@Composable
fun SelectableEditRow(
    title: String,
    summary: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
    ) {
        BasicComponent(
            title = title,
            summary = summary,
            insideMargin = CardItemPadding,
            startAction = {
                RadioButton(selected = selected, onClick = onSelect)
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
            },
            onClick = onSelect,
            role = Role.RadioButton,
        )
    }
}
