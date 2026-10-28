package com.miuix.hyperschedule.data.importer.edu

import android.content.Context
import com.miuix.hyperschedule.data.remote.httpGetBytes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 教务学校索引，内置文件打底，更新后缓存到 files/edu */
class SchoolRepository(
    private val context: Context,
) {
    private val indexFile = File(context.filesDir, "edu/school_index.pb")

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
            val bytes = download()
            val remote = SchoolIndexParser.parse(bytes)
            require(remote.schools.isNotEmpty() && remote.versionId.isNotBlank()) { "索引内容不完整" }
            // 版本号是 TIME_yyyyMMddHHmmss_xxx，回滚或不变都不写入
            val local = load().versionId
            if (local.isNotEmpty() && remote.versionId <= local) return@withContext false
            indexFile.parentFile?.mkdirs()
            indexFile.writeBytes(bytes)
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

    /** gitee raw 可能先返回带签名的拦截页 */
    private fun download(): ByteArray {
        val bytes = httpGetBytes(INDEX_URL)
        val head = if (bytes.size <= 2048) bytes.decodeToString().trimStart() else ""
        if (!head.startsWith("<") || !head.contains("raw.giteeusercontent.com")) return bytes
        val signed =
            Regex("href=\"([^\"]+)\"").find(head)?.groupValues?.get(1)?.replace("&amp;", "&")
                ?: error("索引地址被拦截")
        return httpGetBytes(signed)
    }

    private companion object {
        const val INDEX_ASSET = "eduloader/school_index.pb"
        const val INDEX_URL =
            "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/index-pb-release/school_index.pb"
        val ImportCategories = setOf(Adapter.TOOL, Adapter.BACHELOR, Adapter.POSTGRADUATE)
    }
}
