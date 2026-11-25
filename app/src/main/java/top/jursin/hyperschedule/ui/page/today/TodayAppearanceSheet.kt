package top.jursin.hyperschedule.ui.page.today

import android.net.Uri
import androidx.compose.runtime.Composable
import top.jursin.hyperschedule.data.local.TodayStyle
import top.jursin.hyperschedule.theme.sheetCardColor
import top.jursin.hyperschedule.ui.component.pref.SwitchPreference
import top.jursin.hyperschedule.ui.component.PreferenceSection
import top.jursin.hyperschedule.ui.component.SheetScaffold
import top.jursin.hyperschedule.ui.component.SheetScrollColumn
import top.jursin.hyperschedule.ui.component.WallpaperPreferences

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
        title = "今日课程外观",
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
        }
    }
}
