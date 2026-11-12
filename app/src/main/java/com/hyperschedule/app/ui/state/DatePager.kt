package com.hyperschedule.app.ui.state

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** 今日页日期翻页状态，页码即相对今天的天数偏移，第 [MAX_DATE_OFFSET] 页是今天 */
class DatePagerState internal constructor(
    /** 今天，跨零点后重建状态，让"今"指向真实今天 */
    val today: LocalDate,
    initialPagerState: PagerState,
) {

    var pagerState: PagerState = initialPagerState
        private set

    val dayOffset: Int get() = pagerState.currentPage - MAX_DATE_OFFSET

    val date: LocalDate get() = today.plusDays(dayOffset.toLong())

    val isToday: Boolean get() = dayOffset == 0

    suspend fun backToToday() {
        if (!isToday) {
            pagerState.animateScrollToPage(MAX_DATE_OFFSET)
        }
    }

    suspend fun animateToDate(date: LocalDate) {
        val target = pageOf(date)
        if (target in PAGE_RANGE) {
            pagerState.animateScrollToPage(target)
        }
    }

    suspend fun snapToDate(date: LocalDate) {
        val target = pageOf(date)
        if (target in PAGE_RANGE) {
            pagerState.scrollToPage(target)
        }
    }

    private fun pageOf(date: LocalDate): Int =
        MAX_DATE_OFFSET + ChronoUnit.DAYS.between(today, date).toInt()

    companion object {
        /** 今天所在页码，两侧各留 1000 天 */
        const val MAX_DATE_OFFSET = 1000

        const val PAGE_COUNT = MAX_DATE_OFFSET * 2 + 1

        val PAGE_RANGE = 0 until PAGE_COUNT
    }
}

/** 创建日期翻页状态，跨零点后以新的一天重建 */
@Composable
fun rememberDatePagerState(): DatePagerState {
    val today = rememberToday()
    // 不用 rememberPagerState，页面恢复时要回到今天而非上次的日期
    val pagerState =
        remember {
            PagerState(
                currentPage = DatePagerState.MAX_DATE_OFFSET,
                pageCount = { DatePagerState.PAGE_COUNT },
            )
        }
    return remember(today) { DatePagerState(today, pagerState) }
}

/** 今日页日期翻页容器，每个日期一页，滑动交给 [HorizontalPager] 内置手势 */
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

/** 今天，跨零点自动更新，避免页面一直用启动那天的日期 */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L).milliseconds)
            today = LocalDate.now()
        }
    }
    return today
}
