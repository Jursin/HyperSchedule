package com.hyperschedule.app.update

import com.hyperschedule.app.data.remote.httpGet
import org.json.JSONArray
import org.json.JSONObject

enum class UpdateSource(val label: String, val api: String, val repo: String) {
    GitHub("GitHub", api = "https://api.github.com", repo = "Jursin/HyperSchedule"),
}

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String?,
)

data class ReleaseInfo(val version: String, val assets: List<ReleaseAsset>)

object AppUpdate {
    /** 拉取最新发布，[acceptPrerelease] 为假时过滤掉测试版，未发版时按检查失败上报 */
    fun fetchRelease(source: UpdateSource, acceptPrerelease: Boolean, proxy: String): ReleaseInfo {
        // 统一拉列表再本地挑选，只按版本号取出最大的一条
        val body =
            httpGet(
                "${source.api}/repos/${source.repo}/releases?per_page=30&direction=desc",
                accept = "application/vnd.github+json",
            )
        val array = JSONArray(body.trimStart())
        val node =
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .filter { acceptPrerelease || !isPrerelease(it) }
                // 没有 tag_name 的发布无法比较版本，直接排除
                .filter { versionOf(it).isNotBlank() }
                .maxWithOrNull { left, right -> compareVersions(versionOf(left), versionOf(right)) }
                ?: error("未获取到发布版本")
        return ReleaseInfo(versionOf(node), parseAssets(node, proxy))
    }

    private fun versionOf(node: JSONObject): String = node.optString("tag_name").removePrefix("v").trim()

    /** tag 包括 beta.N 后缀或勾了预发布，都按测试版处理 */
    private fun isPrerelease(node: JSONObject): Boolean =
        node.optBoolean("prerelease") || versionOf(node).contains('-')

    /** 按本机 ABI 顺序选包 */
    fun selectAsset(assets: List<ReleaseAsset>, abis: List<String>): ReleaseAsset? =
        abis.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.name.contains(abi, ignoreCase = true) } }

    /** 按 semver 比较，返回负数、0 或正数 */
    fun compareVersions(left: String, right: String): Int {
        val leftCore = left.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val rightCore = right.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(leftCore.size, rightCore.size)) {
            val difference = leftCore.getOrElse(index) { 0 } - rightCore.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        // 主版本号相同时预发布版低于正式版
        val leftPre = left.substringAfter('-', "").split('.').filter { it.isNotEmpty() }
        val rightPre = right.substringAfter('-', "").split('.').filter { it.isNotEmpty() }
        return when {
            leftPre.isEmpty() && rightPre.isEmpty() -> 0
            leftPre.isEmpty() -> 1
            rightPre.isEmpty() -> -1
            else -> comparePrerelease(leftPre, rightPre)
        }
    }

    /** 预发布标识逐段比较，数字段按数值、字母段按字典序，数字段小于字母段，前缀相同则段数少者小 */
    private fun comparePrerelease(left: List<String>, right: List<String>): Int {
        for (index in 0 until minOf(left.size, right.size)) {
            val a = left[index].toIntOrNull()
            val b = right[index].toIntOrNull()
            val difference =
                when {
                    a != null && b != null -> a - b
                    a != null -> -1
                    b != null -> 1
                    else -> left[index].compareTo(right[index])
                }
            if (difference != 0) return difference
        }
        return left.size - right.size
    }

    /** 加速地址只作用于下载链接 */
    private fun parseAssets(node: JSONObject, proxy: String): List<ReleaseAsset> {
        val array = node.optJSONArray("assets") ?: return emptyList()
        val prefix = proxy.trim().trimEnd('/')
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("browser_download_url")
            if (url.isBlank()) return@mapNotNull null
            ReleaseAsset(
                name = item.optString("name"),
                url = if (prefix.isEmpty()) url else "$prefix/$url",
                size = item.optLong("size"),
                sha256 = item.optString("digest").removePrefix("sha256:").ifBlank { null },
            )
        }
    }
}
