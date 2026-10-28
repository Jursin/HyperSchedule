package com.miuix.hyperschedule.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 页面内容的左右边距 */
val PageHorizontalMargin = 12.dp

/** 卡片内容的左右内边距 */
val CardInsidePadding = 16.dp

/** 卡片圆角 */
val CardCornerRadius = 20.dp

/** 卡片内条目的上下内边距 */
val CardItemVerticalPadding = 14.dp

/** 卡片内条目的统一内边距 */
val CardItemPadding = PaddingValues(horizontal = CardInsidePadding, vertical = CardItemVerticalPadding)

/** 页面内容距顶栏的间距 */
val PageTopPadding = 10.dp

/** 页面底部留白 */
val PageBottomInset = 80.dp

/** 卡片与分组之间的纵向间距 */
val SectionSpacing = 12.dp

/** 分组标题的上下内边距 */
val SectionTitleVerticalPadding = 8.dp

/** 弹层内容的最大高度 */
val SheetMaxHeight = 420.dp
val ShortSheetMaxHeight = 320.dp

/** 抽屉底部留白 */
val SheetBottomInset = 70.dp

/** 选择格的高度、圆角、间距与字号，星期与周次网格共用 */
val SelectCellHeight = 36.dp
val SelectCellCorner = 10.dp
val SelectCellSpacing = 6.dp
val SelectCellFontSize = 13.sp

/** 徽标与时间小字的字号 */
val BadgeFontSize = 9.sp
val TimeLabelFontSize = 10.sp
