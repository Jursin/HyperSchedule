package com.hyperschedule.app.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.hyperschedule.app.theme.isDarkTheme
import com.hyperschedule.app.ui.effect.cardSurface
import com.hyperschedule.app.ui.effect.wallpaperVisible
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 展开状态放在进程级，页面来回复用同一份，旋转也不会被恢复成旧值 */
private var sideNavExpanded by mutableStateOf(true)

private val NavWidth = 240.dp
private val NavCollapsedWidth = 84.dp
private val NavItemHeight = 48.dp
private val NavItemGap = 4.dp
private val NavIconSize = 24.dp
private val NavPanelPadding = 8.dp

/** 条目圆角取高的一半，做成胶囊形，分隔线让到这一档，只压住胶囊的直线段 */
private val NavCornerRadius = NavItemHeight / 2
private val NavShape = RoundedCornerShape(NavCornerRadius)

/** 图标中心钉在折叠宽度的中线上，展开时不跟着平移 */
private val NavIconInset = (NavCollapsedWidth - NavPanelPadding * 2 - NavIconSize) / 2

private val NavLabelStyle = TextStyle(fontSize = 16.sp)

private val NavExpandMotion =
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** 宽屏侧栏，面板用抽屉面板色，靠一档色差分层，选中项是胶囊 */
@Composable
internal fun SideNavBar(
    selectedPage: Int,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by
        animateFloatAsState(
            targetValue = if (sideNavExpanded) 1f else 0f,
            animationSpec = NavExpandMotion,
            label = "sideNavExpand",
        )
    val palette = sideNavPalette()
    // 有壁纸时面板换成毛玻璃，壁纸从侧栏后面透出来，没壁纸仍是实心面板
    val onWallpaper = wallpaperVisible()
    // 文字在中段之后淡入，收起时先消失再收窄
    val labelAlpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

    Column(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top))
                .padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
                .width(lerp(NavCollapsedWidth, NavWidth, progress))
                .fillMaxHeight()
                .clip(NavShape)
                .then(if (onWallpaper) Modifier.cardSurface(NavShape) else Modifier.background(palette.panel))
                .padding(NavPanelPadding),
        verticalArrangement = Arrangement.spacedBy(NavItemGap),
    ) {
        // 折叠按钮，只有图标，只换字色不铺按下底色
        SideNavRow(
            icon = MiuixIcons.Sidebar,
            palette = palette,
            role = Role.Button,
            onClickLabel = if (sideNavExpanded) "收起侧栏" else "展开侧栏",
            pressHighlight = false,
            onClick = { sideNavExpanded = !sideNavExpanded },
        )
        // 和下方条目多让出一档，折叠按钮才不会和第一个 tab 贴成一排
        Spacer(Modifier.height(NavItemGap))
        tabs.forEachIndexed { index, tab ->
            SideNavRow(
                icon = tab.icon,
                palette = palette,
                label = tab.label,
                labelAlpha = labelAlpha,
                selected = index == selectedPage,
                role = Role.Tab,
                onClickLabel = tab.label,
                onClick = { onSelect(index) },
            )
        }
        // 两端各让出一个圆角，和上方胶囊高亮矩形的直线段对齐，收起时同样按这个量内缩
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = NavCornerRadius),
            thickness = 0.5.dp,
            color = MiuixTheme.colorScheme.outline,
        )
        // 设置是子页面入口，不占选中态，只有按下的那一下亮
        SideNavRow(
            icon = settingsTab.icon,
            palette = palette,
            label = settingsTab.label,
            labelAlpha = labelAlpha,
            role = Role.Button,
            onClickLabel = settingsTab.label,
            onClick = onOpenSettings,
        )
    }
}

/** 侧栏的一行，折叠按钮和 tab 共用，整行命中，按下换字色，选中铺一层胶囊底色 */
@Composable
private fun SideNavRow(
    icon: ImageVector,
    palette: SideNavPalette,
    role: Role,
    onClickLabel: String,
    onClick: () -> Unit,
    label: String? = null,
    labelAlpha: Float = 1f,
    selected: Boolean = false,
    pressHighlight: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val content = if (pressed) palette.contentPressed else palette.content
    val background =
        when {
            pressed && pressHighlight -> palette.pressed
            selected -> palette.selected
            else -> Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(NavItemHeight)
                .clip(NavShape)
                .background(background)
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    role = role,
                    onClickLabel = onClickLabel,
                    onClick = onClick,
                )
                .padding(start = NavIconInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(NavIconSize),
            imageVector = icon,
            contentDescription = null,
            tint = content,
        )
        if (label != null) {
            Text(
                text = label,
                color = content,
                style = NavLabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = labelAlpha }
                        .padding(start = 12.dp),
            )
        }
    }
}

/** 侧栏配色，面板与文字取主题色，选中/按下是把文字色压淡 */
@Composable
private fun sideNavPalette(): SideNavPalette {
    val onSurface = MiuixTheme.colorScheme.onSurface
    val dark = isDarkTheme()
    return SideNavPalette(
        panel = MiuixTheme.colorScheme.surfaceContainer,
        selected = onSurface.copy(alpha = if (dark) 0.12f else 0.1f),
        pressed = onSurface.copy(alpha = if (dark) 0.2f else 0.06f),
        content = onSurface,
        contentPressed = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

private data class SideNavPalette(
    val panel: Color,
    val selected: Color,
    val pressed: Color,
    val content: Color,
    val contentPressed: Color,
)
