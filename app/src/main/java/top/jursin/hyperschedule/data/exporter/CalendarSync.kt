package top.jursin.hyperschedule.data.exporter

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
import top.jursin.hyperschedule.data.db.AppDatabase
import top.jursin.hyperschedule.data.local.AppLog
import top.jursin.hyperschedule.data.local.SettingsStore
import top.jursin.hyperschedule.data.repository.HolidayRepository
import top.jursin.hyperschedule.data.repository.ScheduleRepository
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把当前课表写进系统日历，写入的账户与日历都是本应用独占的 */
object CalendarSync {
    private const val ACCOUNT_NAME = "HyperSchedule"
    private const val CALENDAR_NAME = "课程表"
    private const val CALENDAR_COLOR = 0xFF2196F3.toInt()

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
                val state = SettingsStore.get(appContext).state
                val repository = ScheduleRepository(AppDatabase.get(appContext))
                val id = repository.existingTableIdOnce(tableId ?: state.currentTableId) ?: error("课表不存在")
                val data = repository.loadExportData(id)
                if (data.courses.isEmpty()) error("课表为空，未做改动")
                val events = data.loadEvents(HolidayRepository.get(appContext))
                val calendarId = ensureCalendar(appContext)
                clearEvents(appContext, calendarId)
                val written = writeEvents(appContext, calendarId, events, state.reminder.icsAlarmMinutes)
                AppLog.i("CalendarSync", "日历同步成功，写入 $written 个事件")
                Result.success(written)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("CalendarSync", "日历同步失败", e)
                Result.failure(e)
            }
        }

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

    /** 分批写入，返回写入的课程节数 */
    private fun writeEvents(context: Context, calendarId: Long, events: List<ExportEvent>, preMinutes: Int?): Int {
        if (events.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val resolver = context.contentResolver
        events.map { eventValues(calendarId, it, zone, preMinutes) }.chunked(EVENTS_PER_BATCH).forEach { chunk ->
            resolver.applyBatch(CalendarContract.AUTHORITY, batchOf(chunk, preMinutes))
        }
        return events.size
    }

    private fun eventValues(calendarId: Long, event: ExportEvent, zone: ZoneId, preMinutes: Int?): ContentValues =
        ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.summary)
            put(CalendarContract.Events.DTSTART, event.start.atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, event.end.atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            if (event.description.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.HAS_ALARM, if (preMinutes != null) 1 else 0)
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
