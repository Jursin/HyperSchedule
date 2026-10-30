package com.miuix.hyperschedule.data.importer.edu

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.io.File

/** 本地索引与脚本的根目录 */
internal const val EduRepoRoot = "edu/repo"

/** 适配仓库来源 */
enum class EduRepo(
    val label: String,
    val url: String,
) {
    GITHUB("GitHub 仓库", "https://github.com/XingHeYuZhuan/shiguang_warehouse"),
    GITEE("Gitee 仓库", "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"),
    CUSTOM("自定义仓库", ""),
}

/** 教务仓库设置 */
data class EduRepoConfig(
    val autoSync: Boolean = true,
    val repo: EduRepo = EduRepo.GITEE,
    val customUrl: String = "",
    val customBranch: String = "main",
)

/** 教务仓库设置存储，学校索引与适配脚本都从这里取地址 */
class EduRepoStore private constructor(
    private val context: Context,
) {
    private val preferences = context.getSharedPreferences("edu_repo", Context.MODE_PRIVATE)

    var state by mutableStateOf(load())
        private set

    /** 从磁盘重读，恢复备份后调用 */
    fun reload() {
        val before = state
        state = load()
        // 仓库变了，旧脚本缓存作废
        if (before.repo != state.repo || before.customUrl != state.customUrl || before.customBranch != state.customBranch) {
            File(context.filesDir, EduScriptsPath).deleteRecursively()
        }
    }

    /** 写入设置，返回仓库或分支是否发生变化 */
    fun update(transform: (EduRepoConfig) -> EduRepoConfig): Boolean {
        val before = state
        val next = transform(before)
        // 自定义仓库没填地址时保持原设置
        val resolved = if (next.repo == EduRepo.CUSTOM && normalizeUrl(next.customUrl).isEmpty()) before else next
        if (resolved == before) return false
        state = resolved
        preferences.edit {
            putBoolean("autoSync", resolved.autoSync)
            putString("repo", resolved.repo.name)
            putString("customUrl", resolved.customUrl)
            putString("customBranch", resolved.customBranch)
        }
        val changed =
            before.repo != resolved.repo ||
                before.customUrl != resolved.customUrl ||
                before.customBranch != resolved.customBranch
        // 换仓库或分支后旧脚本不能再当缓存用
        if (changed) File(context.filesDir, EduScriptsPath).deleteRecursively()
        return changed
    }

    /** 索引固定在 index-pb-release 分支 */
    fun indexUrl(): String {
        val repo = repoUrl()
        require(repo.isNotBlank()) { "请先填写自定义仓库地址" }
        return "$repo/raw/index-pb-release/school_index.pb"
    }

    /** 脚本地址，自定义仓库没填地址时返回 null */
    fun scriptUrl(
        resourceFolder: String,
        scriptPath: String,
    ): String? {
        val repo = repoUrl()
        if (repo.isBlank()) return null
        return "$repo/raw/${branch()}/resources/$resourceFolder/$scriptPath"
    }

    private fun repoUrl(): String =
        when (state.repo) {
            EduRepo.CUSTOM -> normalizeUrl(state.customUrl)
            else -> state.repo.url
        }

    private fun branch(): String =
        if (state.repo == EduRepo.CUSTOM) state.customBranch.trim().ifBlank { "main" } else "main"

    private fun load(): EduRepoConfig {
        val repo =
            preferences.getString("repo", null)
                ?.let { saved -> EduRepo.entries.firstOrNull { it.name == saved } }
                ?: EduRepo.GITEE
        return EduRepoConfig(
            autoSync = preferences.getBoolean("autoSync", true),
            repo = repo,
            customUrl = preferences.getString("customUrl", "").orEmpty(),
            customBranch = preferences.getString("customBranch", "main").orEmpty().ifBlank { "main" },
        )
    }

    /** 补全协议并去掉末尾斜杠与 .git */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/').removeSuffix(".git")
        if (trimmed.isEmpty()) return ""
        val hasScheme = trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)
        return if (hasScheme) trimmed else "https://$trimmed"
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: EduRepoStore? = null

        /** 进程级单例，恢复备份后各处看到同一份 */
        fun get(context: Context): EduRepoStore =
            instance ?: synchronized(this) {
                instance ?: EduRepoStore(context.applicationContext).also { instance = it }
            }
    }
}
