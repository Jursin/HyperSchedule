package top.jursin.hyperschedule.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.ui.effect.glassStyle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Months
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.icon.glass.Years
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class Tab(val label: String, val icon: ImageVector)

/** 分页 tab */
internal val tabs =
    listOf(
        Tab("今日课程", MiuixIcons.Glass.Months),
        Tab("本周课程", MiuixIcons.Glass.Years),
    )

internal val settingsTab = Tab("设置", MiuixIcons.Glass.SettingsOutline)

/** 底栏尺寸 */
internal object NavBarMetrics {
    val floatingBottomMargin: Dp = 24.dp

    /** 胶囊与屏幕左右边缘的固定边距 */
    val sideMargin: Dp = 24.dp

    /** 每个 tab 的宽度，装不下时按可用宽度均分收窄 */
    val itemWidth: Dp = 88.dp

    /** 尾部设置圆片的直径 */
    val settingsSize: Dp = GlassNavigationBarDefaults.Height
}

/** 底栏 */
@Composable
internal fun BottomNavBar(
    targetPage: State<Int>,
    blurBackdrop: Backdrop?,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val materialStyle = glassStyle()
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // 每个 tab 一个固定宽度，整组按内容宽度居中，屏幕窄到装不下时收窄到可用宽度
        val groupWidth =
            (
                NavBarMetrics.itemWidth * tabs.size +
                    GlassNavigationBarDefaults.ActionSpacing +
                    NavBarMetrics.settingsSize
                ).coerceAtMost((maxWidth - NavBarMetrics.sideMargin * 2).coerceAtLeast(0.dp))
        GlassNavigationBar(
            items = tabs.map { GlassNavigationItem(it.icon, it.label) },
            selectedIndex = targetPage.value,
            onSelect = onSelect,
            backdrop = blurBackdrop,
            defaultWindowInsetsPadding = false,
            modifier = Modifier.width(groupWidth),
            style = materialStyle,
            trailingAction = {
                GlassIconButton(
                    onClick = onOpenSettings,
                    backdrop = blurBackdrop,
                    material = GlassNavigationBarDefaults.material(),
                    style = materialStyle,
                    stroke = GlassNavigationBarDefaults.stroke(),
                    shadow = GlassShadows.Float,
                    size = NavBarMetrics.settingsSize,
                ) {
                    Icon(
                        imageVector = settingsTab.icon,
                        contentDescription = settingsTab.label,
                        modifier = Modifier.size(26.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}
