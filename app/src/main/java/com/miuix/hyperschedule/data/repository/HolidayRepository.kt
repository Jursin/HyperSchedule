package com.miuix.hyperschedule.data.repository

import android.content.Context
import androidx.core.content.edit
import com.miuix.hyperschedule.data.model.HolidayEntry
import com.miuix.hyperschedule.data.remote.httpGet
import com.miuix.hyperschedule.schedule.parseDateOrNull
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 节假日与调休：按年份存 SharedPreferences，远端数据源为 holiday-calendar */
class HolidayRepository(context: Context) {
    private val preferences = context.getSharedPreferences("holiday_settings", Context.MODE_PRIVATE)

    /** 数据版本；写入后自增，[observe] 据此重新读取 */
    private val revisionFlow = MutableStateFlow(0)

    /** 某年的条目，写入 / 拉取后自动更新 */
    fun observe(year: Int): Flow<List<HolidayEntry>> =
        revisionFlow.map { readEntries(year) }.flowOn(Dispatchers.IO)

    /** 新增或覆盖；[old] 非空表示编辑原条目（可跨年移动） */
    suspend fun upsert(entry: HolidayEntry, old: HolidayEntry? = null) =
        withContext(Dispatchers.IO) {
            val targetYear = yearOf(entry.date) ?: return@withContext
            val oldYear = old?.let { yearOf(it.date) }
            if (oldYear != null && oldYear != targetYear) {
                writeEntries(oldYear, readEntries(oldYear).filterNot { it == old })
            }
            val base =
                readEntries(targetYear)
                    .filterNot { it == old }
                    // 同一天只保留一条调休
                    .filterNot { entry.type == HolidayEntry.TYPE_WORKSWAP && it.type == entry.type && it.date == entry.date }
            writeEntries(targetYear, (base + entry).sortedBy { it.date })
        }

    suspend fun delete(entry: HolidayEntry) =
        withContext(Dispatchers.IO) {
            val year = yearOf(entry.date) ?: return@withContext
            writeEntries(year, readEntries(year).filterNot { it == entry })
        }

    /** 拉取该年的远端数据并合并，返回远端条目数 */
    suspend fun fetchRemote(year: Int): Int =
        withContext(Dispatchers.IO) {
            val remote =
                parseRemote(httpGet("https://unpkg.com/holiday-calendar@1/data/CN/$year.json"))
            if (remote.isEmpty()) error("未获取到 $year 年的节假日数据")
            mergeRemote(year, remote)
            remote.size
        }

    /** 远端覆盖同日期同类型的旧数据，手工编辑过的条目优先保留 */
    private fun mergeRemote(year: Int, remote: List<HolidayEntry>) {
        val existing = readEntries(year)
        val customKeys = existing.filter { it.custom }.flatMap { it.dateKeys() }.toSet()
        val remoteKeys = remote.flatMap { it.dateKeys() }.toSet()
        val kept = existing.flatMap { entry -> if (entry.custom) listOf(entry) else entry.trimming(remoteKeys) }
        val added = remote.flatMap { it.trimming(customKeys) }
        writeEntries(year, (kept + added).sortedBy { it.date })
    }

    /** 去掉被手工条目占用的日期；区间被切开时按连续段拆成多条 */
    private fun HolidayEntry.trimming(takenKeys: Set<String>): List<HolidayEntry> {
        if (takenKeys.isEmpty()) return listOf(this)
        val start = parseDateOrNull(date) ?: return listOf(this)
        val end = (parseDateOrNull(endDate) ?: start).coerceAtLeast(start)
        val result = mutableListOf<HolidayEntry>()
        var runStart: LocalDate? = null
        var lastFree: LocalDate? = null
        var day = start
        var guard = 0
        while (day <= end && guard++ < 366) {
            if ("$day|$type" in takenKeys) {
                runStart?.let { from -> result += slice(from, lastFree ?: from) }
                runStart = null
                lastFree = null
            } else {
                if (runStart == null) runStart = day
                lastFree = day
            }
            day = day.plusDays(1)
        }
        runStart?.let { from -> result += slice(from, lastFree ?: from) }
        return result
    }

    private fun HolidayEntry.slice(from: LocalDate, to: LocalDate): HolidayEntry =
        copy(date = from.toString(), endDate = if (from == to) "" else to.toString())

    private fun readEntries(year: Int): List<HolidayEntry> {
        val raw = preferences.getString(key(year), null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val date = item.optString("date")
                if (yearOf(date) == null) return@mapNotNull null
                HolidayEntry(
                    date = date,
                    name = item.optString("name").ifBlank { "节假日" },
                    type = item.optInt("type", HolidayEntry.TYPE_HOLIDAY),
                    endDate = item.optString("endDate"),
                    followDate = item.optString("followDate"),
                    custom = item.optBoolean("custom", false),
                )
            }
            .sortedBy { it.date }
    }

    private fun writeEntries(year: Int, entries: List<HolidayEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("date", entry.date)
                    .put("name", entry.name)
                    .put("type", entry.type)
                    .put("endDate", entry.endDate)
                    .put("followDate", entry.followDate)
                    .put("custom", entry.custom),
            )
        }
        preferences.edit { putString(key(year), array.toString()) }
        revisionFlow.update { it + 1 }
    }

    private fun key(year: Int) = "entries_$year"
}

private fun yearOf(date: String): Int? = runCatching { LocalDate.parse(date).year }.getOrNull()

/** 条目覆盖的「日期|类型」键；区间假期按天展开，最多 366 天防脏数据 */
private fun HolidayEntry.dateKeys(): List<String> {
    val start = parseDateOrNull(date) ?: return emptyList()
    val end = (parseDateOrNull(endDate) ?: start).coerceAtLeast(start)
    return generateSequence(start) { it.plusDays(1) }
        .take(366)
        .takeWhile { it <= end }
        .map { "$it|$type" }
        .toList()
}

/** holiday-calendar 响应：{"dates":[{"date","name","name_cn","type"}]} */
private fun parseRemote(text: String): List<HolidayEntry> {
    val dates = runCatching { JSONObject(text).optJSONArray("dates") }.getOrNull() ?: return emptyList()
    val parsed =
        (0 until dates.length()).mapNotNull { index ->
            val item = dates.optJSONObject(index) ?: return@mapNotNull null
            val type =
                when (item.optString("type")) {
                    "public_holiday" -> HolidayEntry.TYPE_HOLIDAY
                    "transfer_workday" -> HolidayEntry.TYPE_WORKSWAP
                    else -> return@mapNotNull null
                }
            val date = item.optString("date")
            if (yearOf(date) == null) return@mapNotNull null
            val name = item.optString("name_cn").ifBlank { item.optString("name").ifBlank { date } }
            HolidayEntry(date = date, name = name, type = type)
        }
    return mergeConsecutive(parsed)
}

/** 连续的同名假期合并成一个区间 */
private fun mergeConsecutive(entries: List<HolidayEntry>): List<HolidayEntry> {
    val result = mutableListOf<HolidayEntry>()
    for (entry in entries.sortedBy { it.date }) {
        val previous = result.lastOrNull()
        val previousEnd = previous?.let { runCatching { LocalDate.parse(it.endDate.ifBlank { it.date }) }.getOrNull() }
        val start = runCatching { LocalDate.parse(entry.date) }.getOrNull()
        val continues =
            previous != null && previousEnd != null && start != null &&
                entry.type == HolidayEntry.TYPE_HOLIDAY && previous.type == HolidayEntry.TYPE_HOLIDAY &&
                previous.name == entry.name && previousEnd.plusDays(1) == start
        if (continues) {
            result[result.lastIndex] = previous.copy(endDate = entry.date)
        } else {
            result += entry
        }
    }
    return result
}
