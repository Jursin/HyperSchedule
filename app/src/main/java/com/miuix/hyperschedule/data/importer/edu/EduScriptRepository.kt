package com.miuix.hyperschedule.data.importer.edu

import android.content.Context
import com.miuix.hyperschedule.data.remote.httpGetGiteeRaw
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 脚本缓存目录，换仓库时整目录清掉 */
internal const val EduScriptsPath = "$EduRepoRoot/schools/resources"

/** 内置脚本目录 */
private const val EduScriptAssets = "offline_repo/schools/resources"

/** 教务适配脚本仓库，脚本地址为 {repo}/raw/{branch}/resources/{resourceFolder}/{scriptPath} */
class EduScriptRepository(
    private val context: Context,
    private val repoStore: EduRepoStore,
) {
    private val scriptsDir = File(context.filesDir, EduScriptsPath)

    /** 取脚本内容，远端优先，失败按本地缓存、内置脚本依次回退 */
    suspend fun script(resourceFolder: String, scriptPath: String): String? =
        withContext(Dispatchers.IO) {
            if (resourceFolder.isBlank() || scriptPath.isBlank()) return@withContext null
            val target = File(scriptsDir, "$resourceFolder/$scriptPath")
            val url = repoStore.scriptUrl(resourceFolder, scriptPath)
            val remote = url?.let { runCatching { httpGetGiteeRaw(it) }.getOrNull() }
            when {
                remote != null -> {
                    target.parentFile?.mkdirs()
                    target.writeBytes(remote)
                    remote.decodeToString()
                }
                target.exists() -> target.readText()
                else -> assetScript("$EduScriptAssets/$resourceFolder/$scriptPath")
            }
        }

    private fun assetScript(path: String): String? =
        runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()?.decodeToString()
}
