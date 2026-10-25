package com.miuix.hyperschedule.ui.page.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.miuix.hyperschedule.theme.SectionSpacing
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 撤销 / 重做按钮：栈为空时对应按钮不出现 */
@Composable
internal fun HistoryFabColumn(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        if (canUndo) {
            FloatingActionButton(
                onClick = onUndo,
                containerColor = MiuixTheme.colorScheme.secondaryVariant,
            ) {
                Icon(
                    imageVector = MiuixIcons.Undo,
                    contentDescription = "撤销",
                    tint = MiuixTheme.colorScheme.onSecondaryVariant,
                )
            }
        }
        if (canRedo) {
            FloatingActionButton(
                onClick = onRedo,
                containerColor = MiuixTheme.colorScheme.secondaryVariant,
            ) {
                Icon(
                    imageVector = MiuixIcons.Redo,
                    contentDescription = "重做",
                    tint = MiuixTheme.colorScheme.onSecondaryVariant,
                )
            }
        }
    }
}
