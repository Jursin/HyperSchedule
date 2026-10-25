package com.miuix.hyperschedule.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 今日页的日期翻页状态：页码即「相对今天的天数偏移」，第 [MAX_DATE_OFFSET] 页是今天，
 * 两侧各留 1000 天，任意一天都能双向到达
 */
class DatePagerState internal constructor(
    /** 今天；跨零点后重建状态，让"今"指向真实今天 */
    val today: LocalDate,
    initialPagerState: PagerState,
) {

    var pagerState: PagerState = initialPagerState
        private set

    /** 相对今天的天数偏移 */
    val dayOffset: Int get() = pagerState.currentPage - MAX_DATE_OFFSET

    /** 当前页对应的日期 */
    val date: LocalDate get() = today.plusDays(dayOffset.toLong())

    /** 当前是否停在今天 */
    val isToday: Boolean get() = dayOffset == 0

    suspend fun backToToday() {
        if (!isToday) {
            pagerState.animateScrollToPage(MAX_DATE_OFFSET)
        }
    }

    /** 动画切到指定日期 */
    suspend fun animateToDate(date: LocalDate) {
        val target = pageOf(date)
        if (target in PAGE_RANGE) {
            pagerState.animateScrollToPage(target)
        }
    }

    /** 立即切到指定日期，无动画 */
    suspend fun snapToDate(date: LocalDate) {
        val target = pageOf(date)
        if (target in PAGE_RANGE) {
            pagerState.scrollToPage(target)
        }
    }

    /** 日期对应的页码 */
    private fun pageOf(date: LocalDate): Int =
        MAX_DATE_OFFSET + ChronoUnit.DAYS.between(today, date).toInt()

    companion object {
        /** 今天所在页码；两侧各留 1000 天 */
        const val MAX_DATE_OFFSET = 1000

        /** 总页数 */
        const val PAGE_COUNT = MAX_DATE_OFFSET * 2 + 1

        /** 页码合法区间 */
        val PAGE_RANGE = 0 until PAGE_COUNT
    }
}

/** 创建日期翻页状态；跨零点后以新的一天重建 */
@Composable
fun rememberDatePagerState(): DatePagerState {
    val today = rememberToday()
    // 不用 rememberPagerState（内部是 rememberSaveable）：页面被回收再恢复时要落在今天，而非上次翻到的日期
    val pagerState =
        remember {
            PagerState(
                currentPage = DatePagerState.MAX_DATE_OFFSET,
                pageCount = { DatePagerState.PAGE_COUNT },
            )
        }
    return remember(today) { DatePagerState(today, pagerState) }
}

/** 今日页的日期翻页容器，每个日期一页；滑动交给 [HorizontalPager] 内置手势（自带轴向锁定） */
@Composable
fun DatePager(
    dateState: DatePagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    content: @Composable (date: LocalDate) -> Unit,
) {
    HorizontalPager(
        state = dateState.pagerState,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = 1,
    ) { page ->
        content(dateState.today.plusDays((page - DatePagerState.MAX_DATE_OFFSET).toLong()))
    }
}
