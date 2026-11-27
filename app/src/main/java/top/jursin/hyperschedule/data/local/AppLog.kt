package top.jursin.hyperschedule.data.local

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 保留数量，-1 表示无限制 */
object LogKeep {
    const val UNLIMITED = -1
    const val DEFAULT = 7
    val OPTIONS = listOf(3, 7, 30, 90, UNLIMITED)
    val LABELS = listOf("3", "7", "30", "90", "无限制")

    fun coerce(value: Int): Int = if (value in OPTIONS) value else DEFAULT

    fun indexOf(value: Int): Int = OPTIONS.indexOf(coerce(value))
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 日志门面 */
object AppLog {
    private val lock = Any()

    private var dir: File? = null
    private var store: SettingsStore? = null
    private var deviceInfo = ""
    @Volatile private var initialized = false

    fun init(context: Context, settings: SettingsStore) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            initialized = true
            val app = context.applicationContext
            store = settings
            dir = File(app.filesDir, "logs").apply { mkdirs() }
            deviceInfo =
                buildString {
                    appendLine("Brand: ${Build.BRAND}")
                    appendLine("Model: ${Build.MODEL}")
                    appendLine("Name: ${Build.PRODUCT}")
                    appendLine("Android: ${Build.VERSION.RELEASE}")
                    appendLine("SDK: ${Build.VERSION.SDK_INT}")
                    appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
                    append("=========================")
                }
        }
        onSettingsChanged()
        installCrashHandler()
    }

    fun d(tag: String, message: String) = emit(Log.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = emit(Log.INFO, tag, message, null)

    fun w(tag: String, message: String) = emit(Log.WARN, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable?) = emit(Log.WARN, tag, message, throwable)

    fun e(tag: String, message: String) = emit(Log.ERROR, tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable?) = emit(Log.ERROR, tag, message, throwable)

    fun onSettingsChanged() {
        val settings = store ?: return
        if (settings.state.logLevel == LogLevel.Off) return
        synchronized(lock) {
            ensureTodayFileLocked()
            trimLocked()
        }
    }

    fun files(): List<File> =
        synchronized(lock) {
            filesLocked()
        }

    fun clear() =
        synchronized(lock) {
            filesLocked().forEach { runCatching { it.delete() } }
        }

    fun delete(file: File): Boolean =
        synchronized(lock) {
            runCatching { file.delete() }.getOrDefault(false)
        }

    private fun emit(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val settings = store ?: return
        if (priority < settings.state.logLevel.priority) return
        val body = message + (if (throwable == null) "" else "\n${Log.getStackTraceString(throwable)}")
        runCatching { Log.println(priority, tag, body) }
        val now = LocalDateTime.now()
        val line = "${now.format(TIME_FORMATTER)} ${priorityName(priority)}/$tag: $body"
        synchronized(lock) { runCatching { appendLocked(line, now) } }
    }

    private fun ensureTodayFileLocked(now: LocalDateTime = LocalDateTime.now()): File? {
        val folder = dir ?: return null
        val file = File(folder, "log_${now.format(DATE_FORMATTER)}.txt")
        if (!file.exists()) {
            file.writeText("=== HyperSchedule Log ===\n$deviceInfo\n")
            trimLocked()
        }
        return file
    }

    private fun appendLocked(line: String, now: LocalDateTime) {
        val file = ensureTodayFileLocked(now) ?: return
        file.appendText(line + "\n")
    }

    private fun filesLocked(): List<File> =
        dir
            ?.listFiles { file -> file.isFile && file.name.startsWith("log_") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    private fun trimLocked() {
        val keep = store?.state?.logKeep ?: return
        if (keep == LogKeep.UNLIMITED) return
        filesLocked().drop(keep).forEach { runCatching { it.delete() } }
    }

    private fun priorityName(priority: Int) =
        when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            else -> "ERROR"
        }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { e("Crash", "Uncaught on ${thread.name}", error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Log.e("AppLog", "Uncaught exception on ${thread.name}", error)
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(2)
            }
        }
    }
}
