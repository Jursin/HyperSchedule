package com.miuix.hyperschedule.ui.page

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.data.repository.TableSummary
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.IconHeaderDialog
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import com.miuix.hyperschedule.ui.component.SelectableEditRow
import com.miuix.hyperschedule.ui.component.TableNameDialog
import com.miuix.hyperschedule.ui.component.WideActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** 导入来源：决定用哪个解析器 */
private enum class ImportSource {
    JSON,
    ICS,
}

/** 课表导入：选好文件后先选目标课表，可直接新建一张再导入 */
@Composable
fun ScheduleImportPage(
    tables: List<TableSummary>,
    table: CourseTableData?,
    repository: ScheduleRepository,
    appBlur: Boolean = false,
    onSwitchTable: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 读到的文件内容；选完课表才真正落库
    var pending by remember { mutableStateOf<Pair<ImportSource, String>?>(null) }
    var launching by remember { mutableStateOf(ImportSource.JSON) }
    var showPicker by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var selectedTableId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun importInto(tableId: String) {
        val (source, text) = pending ?: return
        if (busy) return
        busy = true
        showPicker = false
        scope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) {
                        when (source) {
                            ImportSource.JSON -> repository.importCourseTableJson(text, tableId)
                            ImportSource.ICS -> repository.importIcs(text, tableId)
                        }
                    }
                }
            busy = false
            pending = null
            result.fold(
                onSuccess = {
                    onSwitchTable(tableId)
                    toast("已导入 $it 门课程")
                },
                onFailure = { toast("导入失败：${it.message ?: "文件格式不正确"}") },
            )
        }
    }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val source = launching
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("无法读取文件")
                    }
                }.fold(
                    onSuccess = { text ->
                        if (text.isBlank()) {
                            toast("文件是空的")
                        } else {
                            pending = source to text
                            selectedTableId = table?.tableId ?: tables.firstOrNull()?.table?.id
                            showPicker = true
                        }
                    },
                    onFailure = { toast("读取失败：${it.message ?: "无法读取文件"}") },
                )
            }
        }

    PreferenceScreen(title = "课表导入", appBlur = appBlur, onBack = onBack) {
        PreferenceSection {
            ArrowPreference(
                title = "教务系统导入",
                summary = "从学校教务系统抓取课表导入",
                onClick = {},
            )
        }

        PreferenceSection("文件") {
            ArrowPreference(
                title = "JSON 文件导入",
                summary = "从本应用/拾光课程表 JSON 文件导入",
                onClick = {
                    launching = ImportSource.JSON
                    filePicker.launch(arrayOf("*/*"))
                },
            )
            ArrowPreference(
                title = "ICS 文件导入",
                summary = "从 ICS 日历文件导入",
                onClick = {
                    launching = ImportSource.ICS
                    filePicker.launch(arrayOf("*/*"))
                },
            )
        }

        PreferenceSection("口令") {
            ArrowPreference(
                title = "WakeUp 课程表分享口令导入",
                onClick = {},
            )
            ArrowPreference(
                title = "星链课表分享码导入",
                onClick = {},
            )
            ArrowPreference(
                title = "Nexio 课程表分享口令导入",
                onClick = {},
            )
        }
    }

    val picked = pending
    if (showPicker && picked != null) {
        IconHeaderDialog(
            title = "导入到课表",
            appBlur = appBlur,
            onDismiss = {
                showPicker = false
                pending = null
            },
            onConfirm = { selectedTableId?.let(::importInto) },
            confirmEnabled = selectedTableId != null && !busy,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tables.forEach { summary ->
                    SelectableEditRow(
                        title = summary.table.name.ifBlank { "未命名" },
                        summary =
                            if (summary.courseCount > 0) {
                                "共 ${summary.courseCount} 门课程，导入后将被覆盖"
                            } else {
                                "暂无课程"
                            },
                        selected = summary.table.id == selectedTableId,
                        onSelect = { selectedTableId = summary.table.id },
                    )
                }
            }
            WideActionButton(
                text = "添加课表",
                icon = MiuixIcons.Add,
                onClick = { showNameDialog = true },
            )
        }
    }

    if (showNameDialog) {
        TableNameDialog(
            title = "新建课表",
            appBlur = appBlur,
            onDismiss = { showNameDialog = false },
            onConfirm = { name ->
                showNameDialog = false
                if (busy) return@TableNameDialog
                busy = true
                scope.launch {
                    val created = runCatching { repository.createTable(name) }
                    busy = false
                    created.fold(
                        onSuccess = { tableId -> importInto(tableId) },
                        onFailure = { toast("新建课表失败：${it.message ?: "未知错误"}") },
                    )
                }
            },
        )
    }
}
