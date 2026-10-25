package com.miuix.hyperschedule.ui.page

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.ui.CourseTableData
import com.miuix.hyperschedule.ui.component.PreferenceScreen
import com.miuix.hyperschedule.ui.component.PreferenceSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** 课表导入 */
@Composable
fun ScheduleImportPage(
    table: CourseTableData?,
    repository: ScheduleRepository,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val jsonPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val tableId = table?.tableId ?: return@launch
                val result =
                    runCatching {
                        val text =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                    ?: error("无法读取文件")
                            }
                        repository.importCourseTableJson(text, tableId)
                    }
                Toast.makeText(
                    context,
                    result.fold({ "已导入 $it 门课程" }, { "导入失败：${it.message ?: "文件格式不正确"}" }),
                    Toast.LENGTH_LONG,
                ).show()
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
                onClick = { jsonPicker.launch(arrayOf("*/*")) },
            )
            ArrowPreference(
                title = "ICS 文件导入",
                summary = "从 ICS 日历文件导入",
                onClick = {},
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
}
