package com.miuix.hyperschedule.ui.page

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.exporter.buildIcs
import com.miuix.hyperschedule.data.exporter.buildTableJson
import com.miuix.hyperschedule.data.exporter.lastWeek
import com.miuix.hyperschedule.data.repository.HolidayRepository
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.data.repository.TableSummary
import com.miuix.hyperschedule.schedule.semesterStartMonday
import com.miuix.hyperschedule.theme.ReminderSettings
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.pref.ArrowPreference
import com.miuix.hyperschedule.ui.component.pref.OverlayDropdownPreference
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 导出格式：扩展名与保存窗口用的 MIME */
private enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    ICS("ics", "text/calendar"),
}

/** 文件名里的日期 */
private val FileNameDate = DateTimeFormatter.ofPattern("yyyyMMdd")
private val UnsupportedFileNameChars = Regex("""[\\/:*?"<>|]""")

/** 课表导出：选择课表与格式；JSON 存课表结构，ICS 按实际日期展开日历事件 */
@Composable
fun ScheduleExportPage(
    tables: List<TableSummary>,
    repository: ScheduleRepository,
    holidayRepository: HolidayRepository,
    currentTableId: String?,
    reminder: ReminderSettings = ReminderSettings.Default,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ids = tables.map { it.table.id }
    var selectedId by remember { mutableStateOf(currentTableId) }
    val activeId = selectedId?.takeIf { it in ids } ?: ids.firstOrNull()
    val selectedIndex = ids.indexOf(activeId).coerceAtLeast(0)
    var busy by remember { mutableStateOf(false) }
    // 系统保存窗口的回调是异步的：内容先算好放这里，选好路径再写
    var pending by remember { mutableStateOf<Pair<ExportFormat, String>?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun save(uri: Uri?) {
        val (format, text) = pending ?: return
        pending = null
        if (uri == null) return
        scope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                            ?: error("无法写入文件")
                    }
                }
            result.fold(
                onSuccess = { toast("已导出 ${format.extension.uppercase()} 文件") },
                onFailure = { toast("导出失败：${it.message ?: "无法写入文件"}") },
            )
        }
    }

    val jsonSaver =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(ExportFormat.JSON.mimeType),
        ) { save(it) }
    val icsSaver =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(ExportFormat.ICS.mimeType),
        ) { save(it) }

    fun export(format: ExportFormat) {
        val tableId = activeId ?: return
        if (busy) return
        busy = true
        scope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) {
                        val data = repository.loadExportData(tableId)
                        // 空课表不生成文件：导出的内容再导回来会被判定为无效
                        require(data.courses.isNotEmpty()) { "课表为空" }
                        when (format) {
                            ExportFormat.JSON -> buildTableJson(data)
                            ExportFormat.ICS -> {
                                val start = data.startDate ?: error("请先在课表设置里填写开学日期")
                                // 假期查到最后实际导出的一周，超出总周数的周也按放假规则过滤
                                val end = semesterStartMonday(start).plusWeeks(data.lastWeek.toLong()).minusDays(1)
                                buildIcs(data, holidayRepository.entriesIn(start, end), reminder.icsAlarmMinutes)
                            }
                        }
                    }
                }
            busy = false
            result.fold(
                onSuccess = { text ->
                    pending = format to text
                    val tableName = tables.firstOrNull { it.table.id == tableId }?.table?.name.orEmpty()
                    val fileName = exportFileName(tableName, format.extension)
                    when (format) {
                        ExportFormat.JSON -> jsonSaver.launch(fileName)
                        ExportFormat.ICS -> icsSaver.launch(fileName)
                    }
                },
                onFailure = { toast("导出失败：${it.message ?: "生成文件出错"}") },
            )
        }
    }

    PreferenceScreen(title = "课表导出", appBlur = appBlur, onBack = onBack) {
        PreferenceSection("导出课表") {
            if (tables.isEmpty()) {
                Text(
                    text = "暂无课表，请先创建或导入后再导出",
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                OverlayDropdownPreference(
                    title = "选择课表",
                    items = tables.map { it.table.name.ifBlank { "未命名" } },
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedId = ids[it] },
                )
            }
        }

        PreferenceSection("导出格式") {
            ArrowPreference(
                title = "JSON 文件导出",
                summary = "导出课表为 JSON 文件",
                onClick = { export(ExportFormat.JSON) },
            )
            ArrowPreference(
                title = "ICS 文件导出",
                summary = "导出课表为 ICS 日历文件",
                onClick = { export(ExportFormat.ICS) },
            )
        }
    }
}

/** 文件名：课表名_日期.扩展名，去掉路径不允许的字符 */
private fun exportFileName(tableName: String, extension: String): String {
    val base = tableName.ifBlank { "课表" }.replace(UnsupportedFileNameChars, "_").take(30)
    return "${base}_${LocalDate.now().format(FileNameDate)}.$extension"
}
