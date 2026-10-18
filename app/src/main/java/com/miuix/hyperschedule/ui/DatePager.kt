package com.miuix.hyperschedule.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import java.time.LocalDate

/** 今日页的日期翻页容器，每个日期一页；滑动交给 [HorizontalPager] 内置手势（自带轴向锁定） */
@Composable
fun DatePager(
    dateState: DatePagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    content: @Composable (date: LocalDate, isToday: Boolean) -> Unit,
) {
    // "今"按钮 / 日期选择器请求回今天
    LaunchedEffect(dateState.backToTodayTrigger) {
        if (dateState.backToTodayTrigger > 0 && !dateState.isToday) {
            dateState.pagerState.animateScrollToPage(DatePagerState.MAX_DATE_OFFSET)
        }
    }

    HorizontalPager(
        state = dateState.pagerState,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = 1,
    ) { page ->
        val date = dateState.today.plusDays((page - DatePagerState.MAX_DATE_OFFSET).toLong())
        content(date, date == dateState.today)
    }
}
