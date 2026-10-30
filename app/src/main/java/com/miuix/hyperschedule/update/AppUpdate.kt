package com.miuix.hyperschedule.update

import com.miuix.hyperschedule.data.remote.httpGet
import org.json.JSONArray
import org.json.JSONObject

/** 更新源 */
enum class UpdateSource(val label: String) {
    GitHub("GitHub"),
    Gitee("Gitee"),
}

/** 一次发布里的安装包 */
data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String?,
)

/** 一次发布 */
data class ReleaseInfo(val version: String, val assets: List<ReleaseAsset>)

/** 版本检查与安装包选择 */
object AppUpdate {
    private const val GITHUB_REPO = "Jursin/HyperSchedule"
    private const val GITEE_REPO = "Hello_Jursin/HyperSchedule"
    private const val GITHUB_API = "https://api.github.com"
    private const val GITEE_API = "https://gitee.com/api/v5"

    /** 拉取指定源的最新发布，[acceptPrerelease] 为假时过滤掉测试版；未发版时按检查失败上报 */
    fun fetchRelease(source: UpdateSource, acceptPrerelease: Boolean, proxy: String): ReleaseInfo {
        val base = if (source == UpdateSource.GitHub) GITHUB_API else GITEE_API
        val repo = if (source == UpdateSource.GitHub) GITHUB_REPO else GITEE_REPO
        // 统一拉列表再本地挑选，Gitee 的 latest 不排除预发布
        val body =
            httpGet(
                "$base/repos/$repo/releases?per_page=30&direction=desc",
                accept = "application/vnd.github+json",
            )
        val array = JSONArray(body.trimStart())
        val node =
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .filter { acceptPrerelease || !it.optBoolean("prerelease") }
                // 没有 tag_name 的发布无法比较版本，直接排除
                .filter { versionOf(it).isNotBlank() }
                .maxWithOrNull { left, right -> compareVersions(versionOf(left), versionOf(right)) }
                ?: error("未获取到发布版本")
        return ReleaseInfo(versionOf(node), parseAssets(node, proxy, source))
    }

    private fun versionOf(node: JSONObject): String = node.optString("tag_name").removePrefix("v").trim()

    /** 按本机 ABI 顺序选包 */
    fun selectAsset(assets: List<ReleaseAsset>, abis: List<String>): ReleaseAsset? =
        abis.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.name.contains(abi, ignoreCase = true) } }

    /** 逐段比较版本号，返回负数、0 或正数 */
    fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val difference = leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }

    /** 加速地址只作用于下载链接 */
    private fun parseAssets(node: JSONObject, proxy: String, source: UpdateSource): List<ReleaseAsset> {
        val array = node.optJSONArray("assets") ?: return emptyList()
        val prefix = if (source == UpdateSource.GitHub) proxy.trim().trimEnd('/') else ""
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
