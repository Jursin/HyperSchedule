package com.hyperschedule.app.ui.component

import android.net.Uri
import androidx.compose.runtime.Composable
import com.hyperschedule.app.data.local.TodayStyle
import com.hyperschedule.app.theme.sheetCardColor
import com.hyperschedule.app.ui.component.pref.SwitchPreference

/** 今日课表外观抽屉，课程卡信息行显示哪些字段，改动即时生效 */
@Composable
fun TodayAppearanceSheet(
    show: Boolean,
    style: TodayStyle,
    onStyleChange: (TodayStyle) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    wallpaper: String = "",
    wallpaperBlur: Int = 0,
    onWallpaperPick: (Uri) -> Unit = {},
    onWallpaperClear: () -> Unit = {},
    onWallpaperBlurChange: (Int) -> Unit = {},
    onWallpaperBlurCommit: () -> Unit = {},
) {
    SheetScaffold(
        title = "今日课表外观",
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        SheetScrollColumn {
            PreferenceSection(cardColor = sheetCardColor(), title = "视图") {
                SwitchPreference(
                    checked = style.showPeriods,
                    onCheckedChange = { onStyleChange(style.copy(showPeriods = it)) },
                    title = "显示时段分隔",
                )
                WallpaperPreferences(
                    wallpaper = wallpaper,
                    blur = wallpaperBlur,
                    onPick = onWallpaperPick,
                    onClear = onWallpaperClear,
                    onBlurChange = onWallpaperBlurChange,
                    onBlurCommit = onWallpaperBlurCommit,
                )
            }

            PreferenceSection(cardColor = sheetCardColor(), title = "课程卡片") {
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

            SheetBottomSpacer()
        }
    }
}
