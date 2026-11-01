package com.hyperschedule.app.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PageHorizontalMargin = 12.dp

val CardInsidePadding = 16.dp

val CardCornerRadius = 20.dp

val CardItemVerticalPadding = 14.dp

val CardItemPadding = PaddingValues(horizontal = CardInsidePadding, vertical = CardItemVerticalPadding)

val PageTopPadding = 10.dp

val PageBottomInset = 80.dp

/** 卡片与分组之间的纵向间距 */
val SectionSpacing = 12.dp

val SectionTitleVerticalPadding = 8.dp

val SheetMaxHeight = 420.dp
val ShortSheetMaxHeight = 320.dp

val SheetLargeMaxWidth = 420.dp

/** 宽屏阈值，侧栏按宽度、浮窗按短边，都取这个值 */
val WideLayoutMinWidth = 600.dp

/** 宽屏浮窗与居中弹层的圆角 */
val PanelCorner = 32.dp

val WidePageMaxWidth = 512.dp
const val WidePageHeightFraction = 0.8f

val WidePageContentInset = 20.dp

/** 浮窗的遮罩强度 */
val WindowScrimColor = Color.Black.copy(alpha = 0.6f)

fun widePageHeight(
    width: Dp,
    height: Dp,
): Dp = minOf(width, height) * WidePageHeightFraction

/** 窗口是否该用浮窗页面，看短边，横竖屏结论一致 */
fun useWindowedPages(
    width: Dp,
    height: Dp,
): Boolean = minOf(width, height) >= WideLayoutMinWidth

/** 当前窗口是不是平板，对话框、抽屉、整页浮窗统一按短边判定 */
@Composable
fun useLargeScreenWindow(): Boolean {
    val size = LocalWindowInfo.current.containerDpSize
    return useWindowedPages(size.width, size.height)
}

val SheetBottomInset = 70.dp

/** 选择格的高度、圆角、间距与字号，星期与周次网格共用 */
val SelectCellHeight = 36.dp
val SelectCellCorner = 10.dp
val SelectCellSpacing = 6.dp
val SelectCellFontSize = 13.sp

/** 徽标与时间小字的字号 */
val BadgeFontSize = 9.sp
val TimeLabelFontSize = 10.sp
