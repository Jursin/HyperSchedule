package com.miuix.hyperschedule.ui.component

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import com.miuix.hyperschedule.data.importer.TimeSlotJson
import com.miuix.hyperschedule.data.importer.edu.EduImportJson
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 教务脚本桥，对应页面里的 window.AndroidBridge，方法名与参数顺序是脚本契约 */
@Keep
class EduWebBridge(
    private val context: Context,
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val repository: ScheduleRepository,
    private val onAlert: (title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit) -> Unit,
    private val onPrompt: (title: String, tip: String, defaultText: String, validatorJs: String, onResult: (String?) -> Unit) -> Unit,
    private val onSelection: (title: String, items: List<String>, defaultIndex: Int, onResult: (Int?) -> Unit) -> Unit,
    private val onCompleted: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    // 同步接口

    @JavascriptInterface
    fun showToast(message: String) {
        handler.post { context.toast(message) }
    }

    @JavascriptInterface
    fun notifyTaskCompletion() {
        // 没有成功写入数据时忽略，避免误报导入完成
        if (!imported) return
        val id = importTableId ?: return
        handler.post { onCompleted(id) }
    }

    // 对话框（结果经 promiseId 回传）

    @JavascriptInterface
    fun showAlert(title: String, content: String, confirmText: String, promiseId: String) {
        handler.post {
            onAlert(title, content, confirmText) { confirmed ->
                resolve(promiseId, if (confirmed) "true" else "false")
            }
        }
    }

    @JavascriptInterface
    fun showPrompt(title: String, tip: String, defaultText: String, validatorJs: String, promiseId: String) {
        handler.post {
            onPrompt(title, tip, defaultText, validatorJs) { input ->
                if (input == null) resolve(promiseId, "null") else resolve(promiseId, JSONObject.quote(input))
            }
        }
    }

    @JavascriptInterface
    fun showSingleSelection(title: String, itemsJson: String, defaultIndex: Int, promiseId: String) {
        scope.launch {
            runCatching { EduImportJson.strings(itemsJson) }
                .onSuccess { items ->
                    onSelection(title, items, defaultIndex) { index ->
                        resolve(promiseId, index?.toString() ?: "null")
                    }
                }
                .onFailure { fail(promiseId, it) }
        }
    }

    // 导入负载

    @JavascriptInterface
    fun saveImportedCourses(json: String, promiseId: String) {
        save(promiseId) { id -> withContext(Dispatchers.IO) { repository.importEduCourses(id, EduImportJson.courses(json)) } }
    }

    @JavascriptInterface
    fun saveCourseConfig(json: String, promiseId: String) {
        save(promiseId) { id -> withContext(Dispatchers.IO) { repository.importEduConfig(id, EduImportJson.config(json)) } }
    }

    @JavascriptInterface
    fun savePresetTimeSlots(json: String, promiseId: String) {
        if (comboImported) {
            fail(promiseId, IllegalStateException("已导入作息方案，不能再导入基础时间"))
            return
        }
        val slots =
            runCatching { EduImportJson.timeSlots(json) }.getOrElse {
                fail(promiseId, it)
                return
            }.map { it.copy(alias = null) }
        save(promiseId, onSuccess = { presetSlots = slots }) { id ->
            withContext(Dispatchers.IO) { repository.importEduTimeSlots(id, slots) }
        }
    }

    @JavascriptInterface
    fun saveComboSchedule(json: String, promiseId: String) {
        val combo =
            runCatching { EduImportJson.combo(json) }.getOrElse {
                fail(promiseId, it)
                return
            }
        val base = presetSlots
        val merged =
            if (base.isNullOrEmpty()) {
                combo.copy(publicSchedules = combo.publicSchedules.map { schedule ->
                    schedule.copy(timeSlots = schedule.timeSlots.map { it.copy(alias = null) })
                })
            } else {
                combo.copy(publicSchedules = combo.publicSchedules.map { it.copy(timeSlots = mergeSlots(base, it.timeSlots)) })
            }
        save(promiseId, onSuccess = { comboImported = true }) { id ->
            withContext(Dispatchers.IO) { repository.importEduCombo(id, merged) }
        }
    }

    // 工具

    /** 本轮导入的目标课表，由页面在执行脚本前设置 */
    @Volatile
    private var importTableId: String? = null

    /** 本轮是否已写入过数据，脚本结束时的完成通知以它为准 */
    @Volatile
    private var imported = false

    /** 组合作息导入后基础时间会覆盖组合的基准作息，锁住后续调用 */
    @Volatile
    private var comboImported = false

    /** 基础时间缓存，组合作息只带部分节次时用来补齐骨架 */
    @Volatile
    private var presetSlots: List<TimeSlotJson>? = null

    /** 执行新一轮脚本前重置导入状态 */
    fun resetImportTarget() {
        importTableId = null
        imported = false
        comboImported = false
        presetSlots = null
    }

    /** 页面选好目标课表后、执行脚本前调用 */
    fun setImportTable(tableId: String) {
        importTableId = tableId
    }

    /** 组合里的节次覆盖基础作息，没给的用基础补齐，超出基础节次的丢弃 */
    private fun mergeSlots(
        base: List<TimeSlotJson>,
        draft: List<TimeSlotJson>,
    ): List<TimeSlotJson> {
        val overrides = draft.associateBy { it.number }
        return base.sortedBy { it.number }.map { slot ->
            overrides[slot.number]?.let {
                slot.copy(startTime = it.startTime, endTime = it.endTime, alias = null)
            } ?: slot.copy(alias = null)
        }
    }

    private fun save(
        promiseId: String,
        onSuccess: () -> Unit = {},
        action: suspend (tableId: String) -> Unit,
    ) {
        scope.launch {
            val id = importTableId
            if (id.isNullOrBlank()) {
                fail(promiseId, IllegalStateException("尚未选择课表"), toast = false)
                return@launch
            }
            runCatching { action(id) }
                .onSuccess {
                    imported = true
                    onSuccess()
                    resolve(promiseId, "true")
                }
                .onFailure { fail(promiseId, it) }
        }
    }

    private fun fail(
        promiseId: String,
        error: Throwable,
        toast: Boolean = true,
    ) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "导入失败"
        if (toast) handler.post { context.toast(message) }
        handler.post {
            runCatching {
                webView.evaluateJavascript(
                    "window._rejectAndroidPromise(${JSONObject.quote(promiseId)}, ${JSONObject.quote(message)});",
                    null,
                )
            }
        }
    }

    private fun resolve(promiseId: String, literal: String) {
        handler.post {
            runCatching {
                webView.evaluateJavascript(
                    "window._resolveAndroidPromise(${JSONObject.quote(promiseId)}, $literal);",
                    null,
                )
            }
        }
    }
}
