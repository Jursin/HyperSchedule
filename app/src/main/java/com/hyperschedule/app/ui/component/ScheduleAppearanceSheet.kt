package com.hyperschedule.app.ui.component

import android.net.Uri
import androidx.compose.runtime.Composable
import com.hyperschedule.app.data.local.ScheduleStyle
import com.hyperschedule.app.theme.sheetCardColor
import com.hyperschedule.app.ui.component.pref.SwitchPreference
import com.hyperschedule.app.ui.component.pref.WindowDropdownPreference
import java.util.Locale
import kotlin.math.roundToInt

/** 课程表外观抽屉，改动即时生效，不需要「应用」按钮 */
@Composable
fun ScheduleAppearanceSheet(
    show: Boolean,
    style: ScheduleStyle,
    onStyleChange: (ScheduleStyle) -> Unit,
    onStyleCommit: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    wallpaper: String = "",
    wallpaperBlur: Int = 0,
    onWallpaperPick: (Uri) -> Unit = {},
    onWallpaperClear: () -> Unit = {},
    onWallpaperBlurChange: (Int) -> Unit = {},
    onWallpaperBlurCommit: () -> Unit = {},
) {
    // 拖动滑块只改内存让课表实时跟着变，手势结束再落盘
    fun applyStyle(updated: ScheduleStyle) {
        onStyleChange(updated)
        onStyleCommit()
    }
    SheetScaffold(
        title = "课程表外观",
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        SheetScrollColumn {
            PreferenceSection(cardColor = sheetCardColor(), title = "视图") {
                SwitchPreference(
                    checked = style.timeline,
                    onCheckedChange = { applyStyle(style.copy(timeline = it)) },
                    title = "时间轴视图",
                )
                SwitchPreference(
                    checked = style.gridLines,
                    onCheckedChange = { applyStyle(style.copy(gridLines = it)) },
                    title = "显示网格线",
                )
                // 时间轴按整点铺开，没有节次之间的分界缝
                if (!style.timeline) {
                    SwitchPreference(
                        checked = style.breakDividers,
                        onCheckedChange = { applyStyle(style.copy(breakDividers = it)) },
                        title = "显示午休、晚休分界线",
                    )
                }
                WallpaperPreferences(
                    wallpaper = wallpaper,
                    blur = wallpaperBlur,
                    onPick = onWallpaperPick,
                    onClear = onWallpaperClear,
                    onBlurChange = onWallpaperBlurChange,
                    onBlurCommit = onWallpaperBlurCommit,
                )
            }

            PreferenceSection(cardColor = sheetCardColor(), title = "卡片") {
                // 卡片高度按每节课的真实时长换算，滑块统一按一小时占多高来调
                HourHeightSlider(
                    style = style,
                    onStyleChange = onStyleChange,
                    onValueChangeFinished = onStyleCommit,
                )
                StyleSlider(
                    title = "课程卡片圆角",
                    value = style.cardCorner.toFloat(),
                    valueText = "${style.cardCorner}dp",
                    valueRange = 0f..ScheduleStyle.MAX_CARD_CORNER.toFloat(),
                    steps = ScheduleStyle.MAX_CARD_CORNER - 1,
                    keyPoint = ScheduleStyle.Default.cardCorner.toFloat(),
                    onValueChange = { value ->
                        onStyleChange(style.copy(cardCorner = value.roundToInt()))
                    },
                    onValueChangeFinished = onStyleCommit,
                )
                StyleSlider(
                    title = "课程卡片内边距",
                    value = style.cardPadding.toFloat(),
                    valueText = "${style.cardPadding}dp",
                    valueRange =
                        ScheduleStyle.MIN_CARD_PADDING.toFloat()..ScheduleStyle.MAX_CARD_PADDING.toFloat(),
                    steps = ScheduleStyle.MAX_CARD_PADDING - ScheduleStyle.MIN_CARD_PADDING - 1,
                    keyPoint = ScheduleStyle.Default.cardPadding.toFloat(),
                    onValueChange = { value ->
                        onStyleChange(style.copy(cardPadding = value.roundToInt()))
                    },
                    onValueChangeFinished = onStyleCommit,
                )
                StyleSlider(
                    title = "课程卡片外边距",
                    value = style.cardMargin.toFloat(),
                    valueText = "${style.cardMargin}dp",
                    valueRange = ScheduleStyle.MIN_CARD_MARGIN.toFloat()..ScheduleStyle.MAX_CARD_MARGIN.toFloat(),
                    steps = ScheduleStyle.MAX_CARD_MARGIN - ScheduleStyle.MIN_CARD_MARGIN - 1,
                    keyPoint = ScheduleStyle.Default.cardMargin.toFloat(),
                    onValueChange = { value ->
                        onStyleChange(style.copy(cardMargin = value.roundToInt()))
                    },
                    onValueChangeFinished = onStyleCommit,
                )
                SwitchPreference(
                    checked = style.cardBorder,
                    onCheckedChange = { applyStyle(style.copy(cardBorder = it)) },
                    title = "显示卡片边框",
                )
            }

            PreferenceSection(cardColor = sheetCardColor(), title = "内容") {
                // 抽屉挂在浮层里，Overlay 版下拉弹层会被抽屉盖住，用窗口版才会浮在最上层
                WindowDropdownPreference(
                    title = "卡片内容对齐方式",
                    items = listOf("居中", "左对齐"),
                    selectedIndex = if (style.contentCenter) 0 else 1,
                    onSelectedIndexChange = { index ->
                        applyStyle(style.copy(contentCenter = index == 0))
                    },
                )
                StyleSlider(
                    title = "文字缩放比例",
                    value = style.textScale,
                    valueText = String.format(Locale.ROOT, "%.2f x", style.textScale),
                    valueRange = ScheduleStyle.MIN_TEXT_SCALE..ScheduleStyle.MAX_TEXT_SCALE,
                    steps =
                        ((ScheduleStyle.MAX_TEXT_SCALE - ScheduleStyle.MIN_TEXT_SCALE) /
                            ScheduleStyle.TEXT_SCALE_STEP).roundToInt() - 1,
                    keyPoint = ScheduleStyle.Default.textScale,
                    onValueChange = { value ->
                        onStyleChange(style.copy(textScale = ScheduleStyle.snapTextScale(value)))
                    },
                    onValueChangeFinished = onStyleCommit,
                )
                SwitchPreference(
                    checked = style.showPosition,
                    onCheckedChange = { applyStyle(style.copy(showPosition = it)) },
                    title = "显示地点",
                )
                SwitchPreference(
                    checked = style.showTeacher,
                    onCheckedChange = { applyStyle(style.copy(showTeacher = it)) },
                    title = "显示教师",
                )
                SwitchPreference(
                    checked = style.positionPrefix,
                    onCheckedChange = { applyStyle(style.copy(positionPrefix = it)) },
                    title = "地点前显示 @ 符号",
                )
            }

            SheetBottomSpacer()
        }
    }
}

/** 课程卡片高度滑块 */
@Composable
private fun HourHeightSlider(
    style: ScheduleStyle,
    onStyleChange: (ScheduleStyle) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val range = if (style.timeline) ScheduleStyle.TimelineHourHeightRange else ScheduleStyle.HourHeightRange
    val value = if (style.timeline) style.timelineHourHeight else style.hourHeight
    val default = if (style.timeline) ScheduleStyle.Default.timelineHourHeight else ScheduleStyle.Default.hourHeight
    StyleSlider(
        title = "课程卡片高度",
        value = value.toFloat(),
        valueText = "${value}dp/时",
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / ScheduleStyle.HOUR_HEIGHT_STEP - 1,
        keyPoint = default.toFloat(),
        onValueChange = { raw ->
            val snapped = ScheduleStyle.snapHourHeight(raw.roundToInt(), range)
            onStyleChange(
                if (style.timeline) style.copy(timelineHourHeight = snapped) else style.copy(hourHeight = snapped),
            )
        },
        onValueChangeFinished = onValueChangeFinished,
    )
}
