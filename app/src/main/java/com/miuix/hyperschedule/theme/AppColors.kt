package com.miuix.hyperschedule.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课程色叠加到底色上的不透明度，课程卡、管理列表与占位格共用 */
const val CourseTintAlpha = 0.15f

/** 浮层面板的实测底色：浅色略深于白、深色略浅于底 */
private val PanelLight = Color(0xFFF2F2F2)
private val PanelDark = Color(0xFF1E1E1E)

/** 动态取色时取 [dynamic]，否则用实测底色 */
@Composable
private fun panelColor(dynamic: Color): Color =
    when {
        MiuixTheme.isDynamicColor -> dynamic
        isDarkTheme() -> PanelDark
        else -> PanelLight
    }

/** 选择格未选中时的底色：叠在对话框底色上，深浅色都留一档差 */
@Composable
fun selectCellIdleColor(): Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)

/** 浮窗与弹层顶边的高光描边，深浅色都压得很淡 */
@Composable
fun panelOutlineColor(): Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)

/** 抽屉面板 */
@Composable
fun sheetPanelColor(): Color = panelColor(MiuixTheme.colorScheme.surfaceContainer)

/** 抽屉里的卡片：浅色纯白、深色 #303030，动态取色跟随主题 */
@Composable
fun sheetCardColor(): Color =
    when {
        MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.surfaceContainerHigh
        isDarkTheme() -> Color(0xFF303030)
        else -> Color.White
    }

/** 整页浮窗的面板 */
@Composable
fun pageWindowColor(): Color = panelColor(MiuixTheme.colorScheme.surface)
