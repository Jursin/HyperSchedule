package top.jursin.hyperschedule.ui.page.transfer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import top.jursin.hyperschedule.data.importer.edu.EduRepo
import top.jursin.hyperschedule.data.importer.edu.EduRepoConfig
import top.jursin.hyperschedule.theme.SheetMaxHeight
import top.jursin.hyperschedule.theme.sheetCardColor
import top.jursin.hyperschedule.ui.component.pref.DropdownPreference
import top.jursin.hyperschedule.ui.component.pref.SwitchPreference
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.jursin.hyperschedule.ui.component.PreferenceItemSection
import top.jursin.hyperschedule.ui.component.PreferenceSection
import top.jursin.hyperschedule.ui.component.SheetScaffold
import top.jursin.hyperschedule.ui.component.SheetScrollColumn
import top.jursin.hyperschedule.ui.component.rememberSyncedInput

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
                DropdownPreference(
                    title = "适配仓库",
                    items = EduRepo.entries.map { it.label },
                    selectedIndex = EduRepo.entries.indexOf(draft.repo),
                    onSelectedIndexChange = { index ->
                        draft = draft.copy(repo = EduRepo.entries[index])
                    },
                )
            }

            if (draft.repo == EduRepo.Custom) {
                val urlInput = rememberSyncedInput(draft.customUrl) { draft = draft.copy(customUrl = it) }
                val branchInput = rememberSyncedInput(draft.customBranch) { draft = draft.copy(customBranch = it) }
                PreferenceItemSection {
                    GlassTextField(
                        state = urlInput,
                        label = "仓库 URL",
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassTextField(
                        state = branchInput,
                        label = "分支",
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
