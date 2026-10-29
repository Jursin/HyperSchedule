package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.miuix.hyperschedule.data.importer.edu.EduRepo
import com.miuix.hyperschedule.data.importer.edu.EduRepoConfig
import com.miuix.hyperschedule.theme.SheetBottomInset
import com.miuix.hyperschedule.theme.SheetMaxHeight
import com.miuix.hyperschedule.theme.sheetCardColor
import com.miuix.hyperschedule.ui.component.pref.SwitchPreference
import com.miuix.hyperschedule.ui.component.pref.WindowDropdownPreference
import top.yukonga.miuix.kmp.basic.TextField

/** 学校页的更多设置 */
@Composable
fun EduRepoSheet(
    show: Boolean,
    config: EduRepoConfig,
    onApply: (EduRepoConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    // 抽屉里改的是草稿，收起后才写入设置
    var draft by remember { mutableStateOf(config) }
    LaunchedEffect(show) { if (show) draft = config }

    SheetScaffold(
        title = "适配仓库设置",
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = { onApply(draft) },
    ) {
        SheetScrollColumn(maxHeight = SheetMaxHeight) {
            PreferenceSection(cardColor = sheetCardColor()) {
                SwitchPreference(
                    checked = draft.autoSync,
                    onCheckedChange = { draft = draft.copy(autoSync = it) },
                    title = "进入时自动同步",
                )
            }

            PreferenceSection(cardColor = sheetCardColor()) {
                WindowDropdownPreference(
                    title = "适配仓库",
                    items = EduRepo.entries.map { it.label },
                    selectedIndex = EduRepo.entries.indexOf(draft.repo),
                    onSelectedIndexChange = { index ->
                        draft = draft.copy(repo = EduRepo.entries[index])
                    },
                )
            }

            if (draft.repo == EduRepo.CUSTOM) {
                PreferenceItemSection {
                    TextField(
                        value = draft.customUrl,
                        onValueChange = { draft = draft.copy(customUrl = it) },
                        label = "仓库 URL",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = draft.customBranch,
                        onValueChange = { draft = draft.copy(customBranch = it) },
                        label = "分支",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SheetBottomInset))
        }
    }
}
