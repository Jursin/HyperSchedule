package com.miuix.hyperschedule.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.Toast
import androidx.core.content.edit
import com.miuix.hyperschedule.theme.SettingsStore

/** 上课自动勿扰：进入时记下用户原来的档位，退出时只还原自己动过的那一项 */
internal object ClassDnd {

    private const val PREF = "reminder_runtime"
    private const val KEY_APPLIED = "dnd_applied"
    private const val KEY_MANUAL = "dnd_manual"
    private const val KEY_TOUCHED_FILTER = "dnd_touched_filter"
    private const val KEY_TOUCHED_RINGER = "dnd_touched_ringer"
    private const val KEY_FILTER = "dnd_original_filter"
    private const val KEY_RINGER = "dnd_original_ringer"

    /** 与 [com.miuix.hyperschedule.theme.ReminderSettings.DND_MODE_LABELS] 的下标一一对应 */
    private const val MODE_SILENT = 1
    private const val MODE_PRIORITY = 2

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 当前是不是由本应用接管着；通知上的「上课勿扰」按钮靠它判断该开还是该关 */
    fun isApplied(context: Context): Boolean = prefs(context).getBoolean(KEY_APPLIED, false)

    /** 这次接管是手动开的，此时自动勿扰逻辑不许插手 */
    fun isManual(context: Context): Boolean = prefs(context).getBoolean(KEY_MANUAL, false)

    /** 手动开关：[isApplied] 为真就还给用户，否则按设定档位接管 */
    fun toggle(context: Context, mode: Int): Boolean {
        if (isApplied(context)) {
            restore(context)
            return false
        }
        prefs(context).edit { putBoolean(KEY_MANUAL, true) }
        return apply(context, mode)
    }

    /** 按档位接管系统状态；返回是否真的接管成功 */
    fun apply(context: Context, mode: Int): Boolean {
        if (!ReminderPermissions.hasDndAccess(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val store = prefs(context)
        // 连着几节课只记第一次接管前的状态：别用上一节课的状态盖掉用户的原设置
        if (!store.getBoolean(KEY_APPLIED, false)) {
            store.edit {
                putBoolean(KEY_APPLIED, true)
                putBoolean(KEY_TOUCHED_FILTER, false)
                putBoolean(KEY_TOUCHED_RINGER, false)
                putInt(KEY_FILTER, manager.currentInterruptionFilter)
                putInt(KEY_RINGER, audio.ringerMode)
            }
        }
        // 静音模式动的是铃声，另外两档动的是勿扰过滤
        val filter =
            when (mode) {
                MODE_SILENT -> null
                MODE_PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else -> NotificationManager.INTERRUPTION_FILTER_NONE
            }
        val done =
            runCatching {
                if (filter == null) {
                    audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                } else {
                    manager.setInterruptionFilter(filter)
                }
            }.isSuccess
        if (done) {
            store.edit { putBoolean(if (filter == null) KEY_TOUCHED_RINGER else KEY_TOUCHED_FILTER, true) }
        }
        return done
    }

    /** 退出勿扰接管；没接管过时什么都不做，不碰用户自己设的档位 */
    fun restore(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_APPLIED, false)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        runCatching {
            if (prefs.getBoolean(KEY_TOUCHED_FILTER, false)) {
                prefs.getInt(KEY_FILTER, -1).takeIf { it >= 0 }?.let { manager?.setInterruptionFilter(it) }
            }
            if (prefs.getBoolean(KEY_TOUCHED_RINGER, false)) {
                prefs.getInt(KEY_RINGER, -1).takeIf { it >= 0 }?.let { audio?.ringerMode = it }
            }
        }
        clear(context)
    }

    /** 一次性忘掉接管记录：开机、应用更新、关闭提醒后原状态已无意义 */
    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }
}

/** 通知与超级岛上的「上课勿扰」按钮：点一下开，再点一下关 */
class ClassDndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        if (!ReminderPermissions.hasDndAccess(context)) {
            // 没有勿扰权限时 setInterruptionFilter 会被系统静默丢掉，得说一声
            Toast.makeText(context, "请先在课程提醒里授予勿扰权限", Toast.LENGTH_SHORT).show()
            return
        }
        val applied = ClassDnd.toggle(context, SettingsStore(context).state.reminder.dndMode)
        Toast.makeText(context, if (applied) "已开启勿扰" else "已关闭勿扰", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_TOGGLE = "com.miuix.hyperschedule.action.TOGGLE_DND"
    }
}
