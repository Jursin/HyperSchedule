package com.hyperschedule.app.data.exporter

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.hyperschedule.app.data.db.AppDatabase
import com.hyperschedule.app.data.db.entity.CourseWithWeeks
import com.hyperschedule.app.data.db.entity.TimeSlot
import com.hyperschedule.app.data.local.SettingsStore
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.data.repository.ScheduleRepository
import com.hyperschedule.app.reminder.DayCourse
import com.hyperschedule.app.reminder.coursesOn
import com.hyperschedule.app.schedule.HolidayPlan
import com.hyperschedule.app.schedule.parseDateOrNull
import com.hyperschedule.app.schedule.semesterStartMonday
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把当前课表写进系统日历，写入的账户与日历都是本应用独占的 */
object CalendarSync {
    private const val ACCOUNT_NAME = "HyperSchedule"
    private const val CALENDAR_NAME = "课程表"
    private const val CALENDAR_COLOR = 0xFF2196F3.toInt()

    /** 缺少下课时间时的兜底时长 */
    private const val FALLBACK_MINUTES = 90L

    /** 单批事件数，一整批走一次 binder，太大触发 TransactionTooLargeException */
    private const val EVENTS_PER_BATCH = 150

    val permissions: Array<String> =
        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(context: Context): Boolean =
        permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** 重新写一份整表，重复调用不会产生重复事件 */
    suspend fun sync(context: Context, tableId: String? = null): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val data = loadTable(appContext, tableId) ?: error("还未设置开学日期")
                val calendarId = ensureCalendar(appContext)
                clearEvents(appContext, calendarId)
                Result.success(writeEvents(appContext, calendarId, data))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 课表、作息、节假日一次读齐 */
    private suspend fun loadTable(context: Context, tableId: String?): TableData? {
        val state = SettingsStore.get(context).state
        val repository = ScheduleRepository(AppDatabase.get(context))
        val current = repository.existingTableIdOnce(tableId ?: state.currentTableId) ?: return null
        val config = repository.configOnce(current) ?: return null
        val startDate = config.semesterStartDate?.let(::parseDateOrNull) ?: return null
        val totalWeeks = config.semesterTotalWeeks.coerceAtLeast(1)
        val semesterEnd = semesterStartMonday(startDate).plusWeeks(totalWeeks.toLong()).minusDays(1)
        val holidays = HolidayRepository.get(context).entriesIn(startDate, semesterEnd)
        return TableData(
            startDate = startDate,
            totalWeeks = totalWeeks,
            slotMap = repository.slotsOnce(current).associateBy { it.number },
            courses = repository.coursesOnce(current),
            plan = HolidayPlan.build(startDate, totalWeeks, holidays),
            preMinutes = state.reminder.preMinutes.takeIf { state.reminder.enabled },
        )
    }

    private class TableData(
        val startDate: LocalDate,
        val totalWeeks: Int,
        val slotMap: Map<Int, TimeSlot>,
        val courses: List<CourseWithWeeks>,
        val plan: HolidayPlan,
        val preMinutes: Int?,
    )

    private fun ensureCalendar(context: Context): Long {
        findCalendar(context)?.let { return it }

        // 部分 ROM 注册账户会抛 SecurityException，忽略即可，CalendarProvider 自己会登记
        runCatching {
            val manager = AccountManager.get(context)
            val exists =
                manager.getAccountsByType(CalendarContract.ACCOUNT_TYPE_LOCAL).any { it.name == ACCOUNT_NAME }
            if (!exists) manager.addAccountExplicitly(Account(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL), null, null)
        }

        val values =
            ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
                put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
            }
        val inserted = context.contentResolver.insert(CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(), values)
        if (inserted != null) return ContentUris.parseId(inserted)
        return findCalendar(context) ?: error("无法创建日历")
    }

    private fun findCalendar(context: Context): Long? =
        context.contentResolver
            .query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=? AND ${CalendarContract.Calendars.NAME}=?",
                arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL, CALENDAR_NAME),
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    /** 只清本应用这份日历里的事件，其它日历一概不碰 */
    private fun clearEvents(context: Context, calendarId: Long) {
        context.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI.asSyncAdapter(),
            "${CalendarContract.Events.CALENDAR_ID}=?",
            arrayOf(calendarId.toString()),
        )
    }

    /** 整学期逐日展开成事件，分批写入，返回写入的课程节数 */
    private fun writeEvents(context: Context, calendarId: Long, data: TableData): Int {
        val zone = ZoneId.systemDefault()
        val last = semesterStartMonday(data.startDate).plusWeeks(data.totalWeeks.toLong()).minusDays(1)
        val events = ArrayList<ContentValues>()
        var date = data.startDate
        while (!date.isAfter(last)) {
            data.plan.coursesOn(date, data.startDate, data.courses, data.slotMap).forEach { course ->
                events += eventValues(calendarId, course, zone, data.preMinutes)
            }
            date = date.plusDays(1)
        }
        if (events.isEmpty()) return 0
        val resolver = context.contentResolver
        events.chunked(EVENTS_PER_BATCH).forEach { chunk ->
            resolver.applyBatch(CalendarContract.AUTHORITY, batchOf(chunk, data.preMinutes))
        }
        return events.size
    }

    private fun eventValues(calendarId: Long, course: DayCourse, zone: ZoneId, preMinutes: Int?): ContentValues {
        val end = course.end ?: course.start.plusMinutes(FALLBACK_MINUTES)
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, course.name)
            put(CalendarContract.Events.DTSTART, course.start.atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, end.atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.EVENT_LOCATION, course.position)
            // 地点已经在 LOCATION 里，描述只放教师
            if (course.teacher.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, course.teacher)
            put(CalendarContract.Events.HAS_ALARM, if (preMinutes != null) 1 else 0)
        }
    }

    /** 一批事件连同各自的提醒，提醒用批内下标回指刚插入的事件 */
    private fun batchOf(events: List<ContentValues>, preMinutes: Int?): ArrayList<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>(events.size * 2)
        events.forEach { values ->
            val eventIndex = ops.size
            ops +=
                ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI.asSyncAdapter())
                    .withValues(values)
                    .build()
            if (preMinutes != null) {
                val reminder =
                    ContentValues().apply {
                        put(CalendarContract.Reminders.MINUTES, preMinutes)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                ops +=
                    ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI.asSyncAdapter())
                        .withValues(reminder)
                        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventIndex)
                        .build()
            }
        }
        return ops
    }

    /** 写入时带上 sync adapter 参数，否则会被拒绝 */
    private fun Uri.asSyncAdapter(): Uri =
        buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
}
