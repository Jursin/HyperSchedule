package com.miuix.hyperschedule.ui.page.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.WeekdayRow
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课表顶栏：周次标题（可点开跳转）、星期行与三个入口按钮 */
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
    onManageTables: () -> Unit,
    onManageCourses: () -> Unit,
    onAppearanceClick: () -> Unit,
    onJumpClick: (week: Int) -> Unit,
) {
    val title =
        when {
            startDate == null -> "未设置开学日期"
            pageWeek < 1 -> "未开学"
            pageWeek > totalWeeks -> "学期已结束"
            else -> "第${pageWeek}周"
        }
    // 跳转按钮的图标与文字取同一尺寸
    val titleFontSize = MiuixTheme.textStyles.title3.fontSize
    val titleIconSize = with(LocalDensity.current) { titleFontSize.toDp() }

    Box {
        BlurredTopAppBar(
            title = "",
            blurBackdrop = blurBackdrop,
            scrollBehavior = scrollBehavior,
            leading = {
                IconButton(onClick = onManageTables) {
                    Icon(MiuixIcons.ConvertFile, contentDescription = "课表切换")
                }
            },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onManageCourses) {
                        Icon(MiuixIcons.Backup, contentDescription = "课程管理")
                    }
                    IconButton(onClick = onAppearanceClick) {
                        Icon(MiuixIcons.Background, contentDescription = "课表外观")
                    }
                }
            },
            // 星期行与课表列共用同一套网格线
            bottomContent = {
                WeekdayRow(
                    startDate = startDate,
                    week = pageWeek,
                    currentWeek = currentWeek,
                    today = today,
                    gridLines = gridLines,
                )
            },
            pinnedTitle = true,
        )
        // 周次标题固定在正中，跳转按钮在文字右侧留一点空隙
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .height(TopAppBarDefaults.CollapsedHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            // 没设开学日期就没有可跳转的周，不显示按钮
            if (startDate != null) {
                IconButton(
                    onClick = { onJumpClick(pageWeek) },
                    modifier = Modifier.padding(start = 4.dp),
                    minWidth = 32.dp,
                    minHeight = 32.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowUpDown,
                        contentDescription = "跳转周数",
                        modifier = Modifier.size(titleIconSize),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
