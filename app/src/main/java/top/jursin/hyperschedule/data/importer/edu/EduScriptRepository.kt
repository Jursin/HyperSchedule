package top.jursin.hyperschedule.data.importer.edu

import android.content.Context
import top.jursin.hyperschedule.data.importer.ComboScheduleJson
import top.jursin.hyperschedule.data.importer.CourseJson
import top.jursin.hyperschedule.data.importer.CourseTableConfigJson
import top.jursin.hyperschedule.data.local.AppLog
import top.jursin.hyperschedule.data.importer.TimeSlotJson
import top.jursin.hyperschedule.data.remote.httpGetGiteeRaw
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 脚本缓存目录，换仓库时整目录清掉 */
internal const val EduScriptsPath = "$EduRepoRoot/schools/resources"

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
            if (!isSafeRelative(resourceFolder) || !isSafeRelative(scriptPath)) return@withContext null
            val target = File(scriptsDir, "$resourceFolder/$scriptPath")
            val url = repoStore.scriptUrl(resourceFolder, scriptPath)
            val remote =
                url?.let {
                    runCatching { httpGetGiteeRaw(it) }
                        .onFailure { error -> AppLog.w("Edu", "远端脚本拉取失败，回落到本地：$scriptPath ${error.message.orEmpty()}") }
                        .getOrNull()
                }
            when {
                remote != null -> {
                    target.parentFile?.mkdirs()
                    target.writeBytes(remote)
                    remote.decodeToString()
                }
                target.exists() -> target.readText()
                else ->
                    assetScript("$EduScriptAssets/$resourceFolder/$scriptPath")
                        ?: run {
                            AppLog.w("Edu", "脚本三级来源均不可用：$resourceFolder/$scriptPath")
                            null
                        }
            }
        }

    private fun assetScript(path: String): String? =
        runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()?.decodeToString()
}

/** 索引里的路径由远端下发，只接受不带 .. 的相对路径，避免写出缓存目录 */
private fun isSafeRelative(path: String): Boolean =
    path.isNotBlank() &&
        !path.startsWith("/") &&
        !path.startsWith("\\") &&
        path.split('/', '\\').none { it == ".." }

/** 教务脚本回传的四类负载，字段沿用课程表 JSON 导入的 DTO */
object EduImportJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    fun courses(text: String): List<CourseJson> = json.decodeFromString<List<CourseJson>>(text)

    fun timeSlots(text: String): List<TimeSlotJson> = json.decodeFromString<List<TimeSlotJson>>(text)

    fun config(text: String): CourseTableConfigJson = json.decodeFromString<CourseTableConfigJson>(text)

    fun combo(text: String): ComboScheduleJson = json.decodeFromString<ComboScheduleJson>(text)

    fun strings(text: String): List<String> = json.decodeFromString<List<String>>(text)
}
