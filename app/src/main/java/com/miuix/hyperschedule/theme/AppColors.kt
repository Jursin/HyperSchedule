package com.miuix.hyperschedule.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课程色叠加到底色上的不透明度，课程卡、管理列表与占位格共用 */
const val CourseTintAlpha = 0.15f

/** 选择格未选中时的底色：叠在对话框底色上，深浅色都留一档差 */
@Composable
fun selectCellIdleColor(): Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)

/** 抽屉面板：浅色 #F2F2F2、深色 #1E1E1E，动态取色跟随主题 */
@Composable
fun sheetPanelColor(): Color =
    when {
        MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.surfaceContainer
        isDarkTheme() -> Color(0xFF1E1E1E)
        else -> Color(0xFFF2F2F2)
    }

/** 抽屉里的卡片：浅色纯白、深色 #303030，动态取色跟随主题 */
@Composable
fun sheetCardColor(): Color =
    when {
        MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.surfaceContainerHigh
        isDarkTheme() -> Color(0xFF303030)
        else -> Color.White
    }
