package top.jursin.hyperschedule.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.jursin.hyperschedule.ui.effect.glassStyle
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailState

/** 宽屏侧栏 */
@Composable
internal fun SideNavBar(
    selectedPage: Int,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    blurBackdrop: Backdrop?,
    state: GlassNavigationRailState,
    modifier: Modifier = Modifier,
) {
    GlassNavigationRail(
        items = tabs.map { GlassNavigationItem(it.icon, it.label) },
        secondaryItems = listOf(GlassNavigationItem(settingsTab.icon, settingsTab.label)),
        selectedIndex = selectedPage,
        onSelect = { index -> if (index >= tabs.size) onOpenSettings() else onSelect(index) },
        backdrop = blurBackdrop,
        state = state,
        modifier = modifier,
        style = glassStyle(),
    )
}
