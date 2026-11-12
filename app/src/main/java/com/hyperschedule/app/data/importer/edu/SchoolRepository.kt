package com.hyperschedule.app.data.importer.edu

import android.content.Context
import com.hyperschedule.app.data.remote.httpGetGiteeRaw
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 同步结果，added 为相对本地索引新增的可导入学校数 */
data class RepoUpdate(
    val changed: Boolean = false,
    val added: Int = 0,
)

/** 教务学校索引，内置离线索引打底，同步后缓存到 files/edu/repo */
class SchoolRepository(
    private val context: Context,
    private val repoStore: EduRepoStore,
) {
    private val indexDir = File(context.filesDir, "$EduRepoRoot/index")
    private val indexFile = File(indexDir, "school_index.pb")
    private val sourceFile = File(indexDir, "school_index.source")

    @Volatile
    private var cached: SchoolIndex? = null

    /** 可导入的学校，按首字母与校名排序 */
    fun schools(): List<School> = importable(load()).sortedBy { it.initial.uppercase() + it.name }

    /** 拉一次远端索引，返回换版本与新增学校数，失败抛异常 */
    suspend fun update(): RepoUpdate =
        withContext(Dispatchers.IO) {
            val url = repoStore.indexUrl()
            val bytes = httpGetGiteeRaw(url)
            val remote = SchoolIndexParser.parse(bytes)
            require(remote.schools.isNotEmpty() && remote.versionId.isNotBlank()) { "索引内容不完整" }
            require(remote.protocolVersion <= SUPPORTED_PROTOCOL_VERSION) { "索引协议不兼容，请更新应用" }
            // 版本号形如 TIME_yyyyMMddHHmmss_xxx，同仓库回滚或不变都不写入
            val local = load()
            val sameSource = sourceFile.exists() && sourceFile.readText() == url
            if (sameSource && local.versionId.isNotEmpty() && remote.versionId <= local.versionId) {
                return@withContext RepoUpdate()
            }
            val known = importable(local).mapTo(HashSet()) { it.id }
            val added = importable(remote).count { it.id !in known }
            indexFile.parentFile?.mkdirs()
            indexFile.writeBytes(bytes)
            sourceFile.writeText(url)
            cached = remote
            RepoUpdate(changed = true, added = added)
        }

    private fun load(): SchoolIndex {
        cached?.let { return it }
        // 缓存损坏时回落到内置索引
        val parsed =
            runCatching { SchoolIndexParser.parse(readIndexBytes()) }.getOrNull()
                ?: runCatching { SchoolIndexParser.parse(assetBytes()) }.getOrNull()
                ?: SchoolIndex()
        cached = parsed
        return parsed
    }

    private fun importable(index: SchoolIndex): List<School> =
        index.schools.filter { school -> school.adapters.any { it.category in ImportCategories } }

    private fun readIndexBytes(): ByteArray = if (indexFile.exists()) indexFile.readBytes() else assetBytes()

    private fun assetBytes(): ByteArray = context.assets.open(INDEX_ASSET).use { it.readBytes() }

    private companion object {
        const val INDEX_ASSET = "offline_repo/index/school_index.pb"

        /** 解析器能处理的索引协议上限 */
        const val SUPPORTED_PROTOCOL_VERSION = 2
        val ImportCategories = setOf(Adapter.TOOL, Adapter.BACHELOR, Adapter.POSTGRADUATE)
    }
}
