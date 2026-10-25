package com.miuix.hyperschedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.miuix.hyperschedule.data.model.HolidayEntry
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.schedule.HolidayPlan
import com.miuix.hyperschedule.schedule.MAX_TOTAL_WEEKS
import com.miuix.hyperschedule.schedule.weekDatesOf
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 订阅学期覆盖到的年份的节假日；没设开学日期就没有日期可比，返回空 */
@Composable
fun rememberHolidayEntries(
    repository: HolidayRepository?,
    startDate: LocalDate?,
    totalWeeks: Int,
): List<HolidayEntry> {
    if (repository == null || startDate == null) return emptyList()
    val firstYear = startDate.year
    // 学期最后一天可能跨年（秋季学期），两年都订阅
    val lastYear = weekDatesOf(startDate, totalWeeks.coerceIn(1, MAX_TOTAL_WEEKS)).last().year
    val flow: Flow<List<HolidayEntry>> =
        remember(repository, firstYear, lastYear) {
            if (firstYear == lastYear) {
                repository.observe(firstYear)
            } else {
                combine(listOf(repository.observe(firstYear), repository.observe(lastYear))) { years ->
                    years.flatMap { it.toList() }
                }
            }
        }
    return flow.collectAsState(emptyList()).value
}

/** 学期内的节假日方案；没设开学日期时给空方案 */
@Composable
fun rememberHolidayPlan(
    repository: HolidayRepository?,
    startDate: LocalDate?,
    totalWeeks: Int,
): HolidayPlan {
    val entries = rememberHolidayEntries(repository, startDate, totalWeeks)
    return remember(startDate, totalWeeks, entries) {
        startDate?.let { HolidayPlan.build(it, totalWeeks, entries) } ?: HolidayPlan.Empty
    }
}
