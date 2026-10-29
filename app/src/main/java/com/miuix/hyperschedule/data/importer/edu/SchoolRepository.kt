package com.miuix.hyperschedule.data.importer.edu

import android.content.Context
import com.miuix.hyperschedule.data.remote.httpGetGiteeRaw
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    fun schools(): List<School> =
        load()
            .schools
            .filter { school -> school.adapters.any { it.category in ImportCategories } }
            .sortedBy { it.initial.uppercase() + it.name }

    /** 拉一次远端索引，返回是否换了新版本；失败抛异常 */
    suspend fun update(): Boolean =
        withContext(Dispatchers.IO) {
            val url = repoStore.indexUrl()
            val bytes = httpGetGiteeRaw(url)
            val remote = SchoolIndexParser.parse(bytes)
            require(remote.schools.isNotEmpty() && remote.versionId.isNotBlank()) { "索引内容不完整" }
            require(remote.protocolVersion <= SUPPORTED_PROTOCOL_VERSION) { "索引协议不兼容，请更新应用" }
            // 版本号是 TIME_yyyyMMddHHmmss_xxx，同一仓库回滚或不变都不写入；换了仓库则直接接受
            val local = load().versionId
            val sameSource = sourceFile.exists() && sourceFile.readText() == url
            if (sameSource && local.isNotEmpty() && remote.versionId <= local) return@withContext false
            indexFile.parentFile?.mkdirs()
            indexFile.writeBytes(bytes)
            sourceFile.writeText(url)
            cached = remote
            true
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

    private fun readIndexBytes(): ByteArray = if (indexFile.exists()) indexFile.readBytes() else assetBytes()

    private fun assetBytes(): ByteArray = context.assets.open(INDEX_ASSET).use { it.readBytes() }

    private companion object {
        const val INDEX_ASSET = "offline_repo/index/school_index.pb"

        /** 解析器能处理的索引协议上限 */
        const val SUPPORTED_PROTOCOL_VERSION = 2
        val ImportCategories = setOf(Adapter.TOOL, Adapter.BACHELOR, Adapter.POSTGRADUATE)
    }
}
