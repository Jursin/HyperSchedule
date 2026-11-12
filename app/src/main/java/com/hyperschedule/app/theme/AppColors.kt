package com.hyperschedule.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 课程色叠加到底色上的不透明度，课程卡、管理列表与占位格共用 */
const val CourseTintAlpha = 0.15f

/** 提示容器底色比例 */
const val ContainerTintAlpha = 0.12f

// 中性色阶
private val SurfaceLight = Color(0xFFF2F2F2)
private val SurfaceDark = Color(0xFF1E1E1E)
private val SurfaceRaisedDark = Color(0xFF303030)

// 默认蓝
private val AccentBlueLight = Color(0xFF3482FF)
private val AccentBlueDark = Color(0xFF277AF7)

// 警告黄
private val WarningLight = Color(0xFFBF8700)
private val WarningDark = Color(0xFFFFD75E)

// 成功绿
private val Success = Color(0xFF36D167)
private val SuccessContainerLight = Color(0xFFDFFAE4)
private val SuccessContainerDark = Color(0xFF1A3825)

// 危险红
private val DangerLight = Color(0xFFE00000)
private val DangerDark = Color(0xFFFF4D4D)
private val DangerContainerLight = Color(0xFFFFEBEB)
private val DangerContainerDark = Color(0xFF3D1A1A)

/** 浮窗与弹层的遮罩强度 */
val WindowScrimColor = Color.Black.copy(alpha = 0.6f)

/** 动态取色时取 [dynamic]，否则用实测底色 */
@Composable
private fun panelColor(dynamic: Color): Color =
    when {
        MiuixTheme.isDynamicColor -> dynamic
        isDarkTheme() -> SurfaceDark
        else -> SurfaceLight
    }

@Composable
fun pageWindowColor(): Color = panelColor(MiuixTheme.colorScheme.surface)

@Composable
fun sheetPanelColor(): Color = panelColor(MiuixTheme.colorScheme.surfaceContainer)

/** 抽屉里的卡片，动态取色跟随主题 */
@Composable
fun sheetCardColor(): Color =
    when {
        MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.surfaceContainerHigh
        isDarkTheme() -> SurfaceRaisedDark
        else -> Color.White
    }

@Composable
fun panelOutlineColor(): Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)

/** 中性浅填充 */
@Composable
fun subtleFillColor(): Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)

/** 默认蓝 */
@Composable
fun accentBlueColor(): Color = if (isDarkTheme()) AccentBlueDark else AccentBlueLight

/** 警告黄 */
@Composable
fun warningColor(): Color = if (isDarkTheme()) WarningDark else WarningLight

/** 成功绿 */
fun successColor(): Color = Success

/** 成功绿底色 */
@Composable
fun successContainerColor(): Color = if (isDarkTheme()) SuccessContainerDark else SuccessContainerLight

/** 危险红 */
@Composable
fun dangerColor(): Color = if (isDarkTheme()) DangerDark else DangerLight

/** 危险红底色 */
@Composable
fun dangerContainerColor(): Color = if (isDarkTheme()) DangerContainerDark else DangerContainerLight
