package top.jursin.hyperschedule.data.importer.edu

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

internal const val EduFavoritePrefsName = "edu_favorites"

/** 收藏的学校 id，进程内共享一份，改动即时反映到列表 */
class EduFavoriteStore private constructor(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(EduFavoritePrefsName, Context.MODE_PRIVATE)

    var ids by mutableStateOf(load())
        private set

    /** 从磁盘重读，恢复备份后调用 */
    fun reload() {
        ids = load()
    }

    fun toggle(id: String) {
        if (id.isBlank()) return
        ids = if (id in ids) ids - id else ids + id
        preferences.edit { putStringSet(KEY, ids) }
    }

    private fun load(): Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty().toSet()

    companion object {
        private const val KEY = "schools"

        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: EduFavoriteStore? = null

        fun get(context: Context): EduFavoriteStore =
            instance ?: synchronized(this) {
                instance ?: EduFavoriteStore(context.applicationContext).also { instance = it }
            }
    }
}
