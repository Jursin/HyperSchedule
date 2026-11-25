package top.jursin.hyperschedule.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.jursin.hyperschedule.ui.effect.LocalPageGlassBackdrop
import top.jursin.hyperschedule.ui.effect.LocalWallpaper
import top.jursin.hyperschedule.ui.effect.captureForBlur
import top.jursin.hyperschedule.ui.effect.glassStyle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun TabSurfaceLayer(
    modifier: Modifier,
    content: @Composable (LayerBackdrop) -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val layer = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
    Box(modifier) {
        Box(Modifier.matchParentSize().captureForBlur(layer))
        content(layer)
    }
}

/** 分段式 Tab 行，关掉模糊时退回内置的描边 Tab */
@Composable
internal fun JoinedGlassTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val wallpaper = LocalWallpaper.current?.glassSample
    val materialStyle = glassStyle()
    // 关掉模糊就没有玻璃可采，交给内置的描边 Tab
    val blurOn = wallpaper != null || LocalPageGlassBackdrop.current != null
    when {
        wallpaper != null ->
            GlassSegmentedTabRow(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                backdrop = wallpaper,
                style = materialStyle,
                modifier = modifier.fillMaxWidth(),
            )
        blurOn ->
            TabSurfaceLayer(modifier) { layer ->
                GlassSegmentedTabRow(
                    tabs = tabs,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    backdrop = layer,
                    style = materialStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        else ->
            TabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedIndex,
                onTabSelected = onSelect,
                modifier = modifier,
            )
    }
}

/** 独立胶囊 Tab 行，选中项自带玻璃底 */
@Composable
internal fun GlassTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val wallpaper = LocalWallpaper.current?.glassSample
    val materialStyle = glassStyle()
    val blurOn = wallpaper != null || LocalPageGlassBackdrop.current != null
    when {
        wallpaper != null ->
            GlassTabRow(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                backdrop = wallpaper,
                style = materialStyle,
                modifier = modifier.fillMaxWidth(),
            )
        blurOn ->
            TabSurfaceLayer(modifier) { layer ->
                GlassTabRow(
                    tabs = tabs,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    backdrop = layer,
                    style = materialStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        // 关掉模糊时按内置的静态胶囊画
        else ->
            GlassTabRow(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                backdrop = null,
                style = materialStyle,
                modifier = modifier.fillMaxWidth(),
            )
    }
}
