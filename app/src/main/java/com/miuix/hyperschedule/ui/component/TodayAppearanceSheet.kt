package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.miuix.hyperschedule.theme.SheetBottomInset
import com.miuix.hyperschedule.theme.TodayStyle
import com.miuix.hyperschedule.theme.sheetCardColor
import com.miuix.hyperschedule.ui.component.pref.SwitchPreference

/** 今日课表外观抽屉：课程卡信息行显示哪些字段，改动即时生效 */
@Composable
fun TodayAppearanceSheet(
    show: Boolean,
    style: TodayStyle,
    onStyleChange: (TodayStyle) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    SheetScaffold(
        title = "今日课表外观",
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        SheetScrollColumn {
            PreferenceSection(cardColor = sheetCardColor(), title = "列表") {
                SwitchPreference(
                    checked = style.showPeriods,
                    onCheckedChange = { onStyleChange(style.copy(showPeriods = it)) },
                    title = "显示时段分隔",
                )
            }

            PreferenceSection(cardColor = sheetCardColor(), title = "课程卡片") {
                SwitchPreference(
                    checked = style.showSection,
                    onCheckedChange = { onStyleChange(style.copy(showSection = it)) },
                    title = "显示节次",
                )
                SwitchPreference(
                    checked = style.showPosition,
                    onCheckedChange = { onStyleChange(style.copy(showPosition = it)) },
                    title = "显示地点",
                )
                SwitchPreference(
                    checked = style.showTeacher,
                    onCheckedChange = { onStyleChange(style.copy(showTeacher = it)) },
                    title = "显示教师",
                )
                SwitchPreference(
                    checked = style.showCredit,
                    onCheckedChange = { onStyleChange(style.copy(showCredit = it)) },
                    title = "显示学分",
                )
                SwitchPreference(
                    checked = style.showRemark,
                    onCheckedChange = { onStyleChange(style.copy(showRemark = it)) },
                    title = "显示备注",
                )
            }

            // 抽屉底下压着悬浮导航栏，留出位置让最后一项能完整露出来
            Spacer(modifier = Modifier.height(SheetBottomInset))
        }
    }
}
