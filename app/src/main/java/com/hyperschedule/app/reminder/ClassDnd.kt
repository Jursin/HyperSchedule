package com.hyperschedule.app.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.Toast
import androidx.core.content.edit
import com.hyperschedule.app.theme.SettingsStore

/** 上课自动勿扰 */
internal object ClassDnd {

    private const val PREF = "reminder_runtime"
    private const val KEY_MANUAL = "dnd_manual"
    private const val KEY_EXPECTED_END = "dnd_expected_end"
    private const val KEY_FILTER = "dnd_original_filter"
    private const val KEY_APPLIED_FILTER = "dnd_applied_filter"
    private const val KEY_RINGER = "dnd_original_ringer"
    private const val KEY_APPLIED_RINGER = "dnd_applied_ringer"
    private const val STALE_GRACE_MILLIS = 60_000L

    /** 与 [com.hyperschedule.app.theme.ReminderSettings.DND_MODE_LABELS] 的下标一一对应 */
    private const val MODE_SILENT = 1
    private const val MODE_PRIORITY = 2

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 当前是否由本应用接管 */
    fun isApplied(context: Context): Boolean {
        val store = prefs(context)
        return store.getInt(KEY_APPLIED_FILTER, -1) >= 0 || store.getInt(KEY_APPLIED_RINGER, -1) >= 0
    }

    /** 点击通知的「上课勿扰」，接管与续期共用同一条路径，课后照常按档位还原 */
    fun isManual(context: Context): Boolean =
        isApplied(context) && prefs(context).getBoolean(KEY_MANUAL, false)

    fun toggle(context: Context, mode: Int): Boolean = apply(context, mode, manual = true)

    /** 按档位补上对应的一项，只写一次 */
    fun apply(
        context: Context,
        mode: Int,
        endMillis: Long = 0L,
        manual: Boolean = false,
    ): Boolean {
        if (!ReminderPermissions.hasDndAccess(context)) return false
        val store = prefs(context)
        var expectedEnd = store.getLong(KEY_EXPECTED_END, 0L)
        // 上一节漏收尾的记录先结清，别把它的原值当成这节的
        if (expectedEnd > 0L && System.currentTimeMillis() >= expectedEnd + STALE_GRACE_MILLIS) {
            restore(context)
            expectedEnd = 0L
        }

        val silent = mode == MODE_SILENT
        val target =
            when {
                silent -> AudioManager.RINGER_MODE_SILENT
                mode == MODE_PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else -> NotificationManager.INTERRUPTION_FILTER_NONE
            }
        val current = currentValue(context, silent) ?: return false
        val appliedKey = if (silent) KEY_APPLIED_RINGER else KEY_APPLIED_FILTER
        if (store.getInt(appliedKey, -1) == target) {
            if (endMillis > expectedEnd) store.edit { putLong(KEY_EXPECTED_END, endMillis) }
            return true
        }
        if (!write(context, silent, target)) return false
        val firstTake = !isApplied(context)
        val originalKey = if (silent) KEY_RINGER else KEY_FILTER
        val original = store.getInt(originalKey, -1)
        store.edit {
            // 只认第一次接管前的值，课中换档位、重复点按钮都不改它
            if (firstTake) putBoolean(KEY_MANUAL, manual)
            if (original < 0) putInt(originalKey, current)
            putInt(appliedKey, target)
            putLong(KEY_EXPECTED_END, maxOf(expectedEnd, endMillis))
        }
        return true
    }

    /** 把记录的有效期拉到这门课下课，用户课前点开的也留到那时才收 */
    fun extend(context: Context, endMillis: Long) {
        if (endMillis <= 0L || !isApplied(context)) return
        val store = prefs(context)
        val expectedEnd = store.getLong(KEY_EXPECTED_END, 0L)
        if (endMillis <= expectedEnd) return
        store.edit { putLong(KEY_EXPECTED_END, endMillis) }
    }

    /** 下课收尾 */
    fun restore(context: Context) {
        if (!isApplied(context)) return
        val store = prefs(context)
        val filterApplied = store.getInt(KEY_APPLIED_FILTER, -1)
        val filterOriginal = store.getInt(KEY_FILTER, -1)
        if (filterApplied >= 0 && filterOriginal >= 0) {
            val manager = context.getSystemService(NotificationManager::class.java)
            runCatching {
                if (manager != null && manager.currentInterruptionFilter == filterApplied) {
                    manager.setInterruptionFilter(filterOriginal)
                }
            }
        }
        val ringerApplied = store.getInt(KEY_APPLIED_RINGER, -1)
        val ringerOriginal = store.getInt(KEY_RINGER, -1)
        if (ringerApplied >= 0 && ringerOriginal >= 0) {
            val audio = context.getSystemService(AudioManager::class.java)
            runCatching {
                if (audio != null && audio.ringerMode == ringerApplied) audio.ringerMode = ringerOriginal
            }
        }
        clear(context)
    }

    /** 清除状态 */
    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }

    /** 当前系统状态 */
    private fun currentValue(context: Context, silent: Boolean): Int? =
        if (silent) {
            context.getSystemService(AudioManager::class.java)?.ringerMode
        } else {
            context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        }

    /** 写目标状态 */
    private fun write(context: Context, silent: Boolean, target: Int): Boolean =
        runCatching {
            if (silent) {
                val audio = context.getSystemService(AudioManager::class.java)
                if (audio != null && audio.ringerMode != target) audio.ringerMode = target
            } else {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager != null && manager.currentInterruptionFilter != target) {
                    manager.setInterruptionFilter(target)
                }
            }
        }.isSuccess
}

class ClassDndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        if (!ReminderPermissions.hasDndAccess(context)) {
            // 没有勿扰权限时 setInterruptionFilter 会被系统静默丢掉，得说一声
            Toast.makeText(context, "请先在课程提醒里授予勿扰权限", Toast.LENGTH_SHORT).show()
            return
        }
        val applied = ClassDnd.toggle(context, SettingsStore.get(context).state.reminder.dndMode)
        val text = if (applied) "已开启勿扰" else "开启勿扰失败"
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_TOGGLE = "com.hyperschedule.app.action.TOGGLE_DND"
    }
}
