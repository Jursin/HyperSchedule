package com.hyperschedule.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.theme.isDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class Tab(val label: String, val icon: ImageVector)

/** 分页 tab */
internal val tabs =
    listOf(
        Tab("今日课表", MiuixIcons.Months),
        Tab("课程表", MiuixIcons.Years),
    )

/** 设置底栏单独成圆、侧栏单独一条，点开子页面 */
internal val settingsTab = Tab("设置", MiuixIcons.Settings)

/** 单个 tab，等分宽度、满高，纯视觉，手势统一由外层处理 */
@Composable
internal fun RowScope.NavBarTab(
    index: Int,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .semantics {
                    role = Role.Tab
                    onClick(label = tabs[index].label) {
                        onClick()
                        true
                    }
                }
                .fillMaxHeight()
                .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** 底栏 */
@Composable
internal fun BottomNavBar(
    initialPage: Int,
    targetPage: State<Int>,
    blurBackdrop: Backdrop?,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isDark = isDarkTheme()
    // 自定义配色时容器和蒙层跟着主题走
    val colors =
        (if (isDark) NavBarColors.Dark else NavBarColors.Light).run {
            if (MiuixTheme.isDynamicColor) {
                val container = MiuixTheme.colorScheme.surfaceContainer
                copy(
                    container = container,
                    blurTint = container.copy(blurTint.alpha),
                    contentTint = container.copy(contentTint.alpha),
                )
            } else {
                this
            }
        }

    val labelTextStyle =
        remember(density) {
            TextStyle(fontSize = with(density) { NavBarMetrics.labelFontSize.toSp() })
        }

    Row(
        modifier = modifier.height(NavBarMetrics.containerHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabsPill(
            initialPage = initialPage,
            targetPage = targetPage,
            blurBackdrop = blurBackdrop,
            colors = colors,
            labelTextStyle = labelTextStyle,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(NavBarMetrics.splitGap))
        SettingsButton(colors = colors, blurBackdrop = blurBackdrop, onClick = onOpenSettings)
    }
}

/** 底栏尺寸 */
internal object NavBarMetrics {
    val containerHeight: Dp = 54.dp // 实测 152px
    /** 栏体与页面底边之间悬空的距离 */
    val floatingBottomMargin: Dp = 24.dp
    val containerRadius: Dp = containerHeight / 2
    val containerPadding: Dp = 8.dp // 实测 22px
    val splitGap: Dp = 8.dp // 胶囊与设置圆之间的间距
    val overlayHeight: Dp = containerHeight - 6.dp // 指示器上下各内缩 3dp
    val overlayRadius: Dp = containerRadius - 3.dp
    val indicatorOverhang: Dp = 5.dp
    val pressInset: Dp = 6.dp
    val iconSize: Dp = 26.dp // 按可见墨迹 57×53px 校准
    val labelFontSize: Dp = 11.dp // footnote3，注意是 dp
    val barShadowRadius: Dp = 10.dp
    val liftShadowDelta: Dp = 8.dp // 拖动中额外抬升
    const val LIFT_SHADOW_ALPHA = 0.8f // 抬升时投影浓度放大系数
}

internal object NavBarColors {
    val Light =
        NavBarPalette(
            container = Color(0xFFFFFFFF),
            blurTint = Color.White.copy(0.45f),
            contentTint = Color.White.copy(0.68f),
            selector = Color(0x0F000000),
            selectorPressed = Color(0x29000000), // 按下 16%
            icon = Color(0xFF000000), // 选中不变色
            iconPressed = Color(0x99000000),
            barShadowAlpha = 0.1f,
        )

    val Dark =
        NavBarPalette(
            container = Color(0xFF181818),
            blurTint = Color.White.copy(0.08f),
            contentTint = Color(0xFF181818).copy(0.72f),
            selector = Color(0x1FFFFFFF),
            selectorPressed = Color(0x42FFFFFF),
            icon = Color(0xF2FFFFFF),
            iconPressed = Color(0x91FFFFFF),
            barShadowAlpha = 0.14f,
        )
}

internal data class NavBarPalette(
    val container: Color,
    val blurTint: Color,
    val contentTint: Color,
    val selector: Color,
    val selectorPressed: Color,
    val icon: Color,
    val iconPressed: Color,
    val barShadowAlpha: Float,
)
