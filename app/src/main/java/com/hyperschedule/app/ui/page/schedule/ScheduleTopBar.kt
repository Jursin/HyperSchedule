package com.hyperschedule.app.ui.page.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.ui.component.BlurredTopAppBar
import com.hyperschedule.app.ui.component.ScheduleDayColumnStart
import com.hyperschedule.app.ui.component.TopBarIconButton
import com.hyperschedule.app.ui.component.TopBarLeadingLabel
import com.hyperschedule.app.ui.component.WeekdayRow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Background
import top.yukonga.miuix.kmp.icon.glass.ConvertFile
import top.yukonga.miuix.kmp.icon.glass.GridView
import top.yukonga.miuix.kmp.icon.glass.JumpPrevious
import top.yukonga.miuix.kmp.icon.glass.More
import java.time.LocalDate

/** 菜单收起时缩回按钮的那枚图标尺寸 */
private val MenuIconSize = 24.dp

/** 课表顶栏 */
@Composable
internal fun ScheduleTopBar(
    startDate: LocalDate?,
    pageWeek: Int,
    totalWeeks: Int,
    currentWeek: Int?,
    today: LocalDate,
    gridLines: Boolean,
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    menuAnchor: GlassPopupAnchor,
    onMenuOpenChange: (Boolean) -> Unit,
    onJumpClick: (week: Int) -> Unit,
) {
    val weekLabel =
        when {
            startDate == null -> "未设置开学日期"
            pageWeek < 1 -> "未开学"
            pageWeek > totalWeeks -> "学期已结束"
            else -> "第${pageWeek}周".cjkDigitGap()
        }
    BlurredTopAppBar(
        title = "本周课程",
        blurBackdrop = blurBackdrop,
        scrollBehavior = scrollBehavior,
        leading = { TopBarLeadingLabel(weekLabel) },
        // 与下方网格第一列右侧的竖线对齐
        leadingStart = ScheduleDayColumnStart,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 没设开学日期就没有可跳转的周
                TopBarIconButton(
                    onClick = { onJumpClick(pageWeek) },
                    enabled = startDate != null,
                    tooltip = "跳转周数",
                ) {
                    Icon(MiuixIcons.Glass.JumpPrevious, contentDescription = "跳转周数")
                }
                TopBarIconButton(
                    onClick = { onMenuOpenChange(true) },
                    modifier =
                        Modifier.glassPopupAnchor(
                            anchor = menuAnchor,
                            cornerRadius = IconButtonDefaults.MinWidth / 2f,
                            floating = true,
                        ),
                    tooltip = "更多",
                ) {
                    Icon(
                        imageVector = MiuixIcons.Glass.More,
                        contentDescription = "更多",
                        modifier = Modifier.size(MenuIconSize).glassPopupAnchorContent(menuAnchor),
                    )
                }
            }
        },
        bottomContent = {
            // 星期行与课表列共用同一套网格线
            WeekdayRow(
                // 没设开学日期时按今天所在周显示日期
                startDate = startDate ?: today,
                week = pageWeek,
                currentWeek = currentWeek,
                today = today,
                gridLines = gridLines,
            )
        },
        pinnedTitle = true,
    )
}

/** 顶栏「更多」菜单 */
@Composable
internal fun BoxScope.ScheduleTopBarMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchor: GlassPopupAnchor,
    blurBackdrop: Backdrop?,
    onAppearanceClick: () -> Unit,
    onManageTables: () -> Unit,
    onManageCourses: () -> Unit,
) {
    GlassTransformPopup(
        show = show,
        onDismissRequest = onDismissRequest,
        anchor = anchor,
        backdrop = blurBackdrop,
        anchorContent = {
            Icon(
                imageVector = MiuixIcons.Glass.More,
                contentDescription = null,
                modifier = Modifier.size(MenuIconSize),
            )
        },
    ) {
        GlassPopupItem(
            text = "外观设置",
            icon = MiuixIcons.Glass.Background,
            onClick = {
                onDismissRequest()
                onAppearanceClick()
            },
        )
        GlassPopupItem(
            text = "课表切换",
            icon = MiuixIcons.Glass.ConvertFile,
            onClick = {
                onDismissRequest()
                onManageTables()
            },
        )
        GlassPopupItem(
            text = "课程管理",
            icon = MiuixIcons.Glass.GridView,
            onClick = {
                onDismissRequest()
                onManageCourses()
            },
        )
    }
}
