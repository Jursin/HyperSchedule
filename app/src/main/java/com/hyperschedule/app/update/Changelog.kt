package com.hyperschedule.app.update

import android.content.Context
import com.hyperschedule.app.data.remote.httpGet
import org.json.JSONArray

/** 更新日志 */
data class ChangelogEntry(
    val version: String,
    val body: String,
)

/** 发行版的发布日期与预发布标记 */
data class ReleaseMeta(val date: String, val prerelease: Boolean)

/** 版本标题 */
private val VersionHeading = Regex("""^##\s+(.+)$""")

/** 代码围栏的起始标记 */
private val FenceMarker = Regex("""^ {0,3}(`{3,}|~{3,})""")

/** 标题里的版本号 */
private val VersionToken = Regex("""v?\d+(?:\.\d+)*(?:[-+][0-9A-Za-z.-]+)?""")

/** 打包进 assets 的日志文件名，来源是仓库根目录的 CHANGELOG.md */
private const val CHANGELOG_ASSET = "CHANGELOG.md"

/** 应用内置的全部日志 */
fun loadChangelog(context: Context): List<ChangelogEntry> {
    val markdown =
        runCatching {
            context.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
    return parseChangelog(markdown)
        .sortedWith { left, right -> AppUpdate.compareVersions(right.version, left.version) }
}

/** 发布日期与预发布标记 */
fun fetchReleaseMeta(source: UpdateSource): Map<String, ReleaseMeta> {
    val body = httpGet("${source.api}/repos/${source.repo}/releases?per_page=100", accept = "application/vnd.github+json")
    val array = JSONArray(body.trimStart())
    return buildMap {
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }.forEach { node ->
            val version = node.optString("tag_name").removePrefix("v").trim()
            val date = node.optString("published_at").take(10)
            if (version.isNotEmpty() && date.isNotEmpty()) {
                put(version, ReleaseMeta(date, node.optBoolean("prerelease")))
            }
        }
    }
}

/** 按二级标题切版本，标题下面到下一个二级标题之间都是这一版的正文 */
internal fun parseChangelog(markdown: String): List<ChangelogEntry> {
    val entries = mutableListOf<ChangelogEntry>()
    val body = StringBuilder()
    var version = ""
    // 当前所在围栏的标记，为 null 表示不在围栏里
    var fence: String? = null

    fun flush() {
        val text = body.toString().trim()
        // 没有正文的一节多半只是标题，不单独成卡片
        if (version.isNotEmpty() && text.isNotEmpty()) entries += ChangelogEntry(version, text)
        body.setLength(0)
    }

    markdown.replace("\r\n", "\n").lines().forEach { line ->
        val marker = FenceMarker.find(line)?.groupValues?.get(1)
        if (marker != null) {
            // 只有同种标记才能闭合，最外层进入时 fence 为空
            fence = if (fence == null) marker else if (fence == marker) null else fence
        }
        // 围栏行和围栏里的内容原样进正文，不参与切分
        if (marker != null || fence != null) {
            if (version.isNotEmpty()) body.appendLine(line)
            return@forEach
        }
        val heading = VersionHeading.find(line)
        if (heading == null) {
            if (version.isNotEmpty()) body.appendLine(line)
            return@forEach
        }
        flush()
        // 取不到版本号的标题（如「未发布」）不算一版
        version = VersionToken.find(heading.groupValues[1])?.value?.removePrefix("v").orEmpty()
    }
    flush()
    return entries
}
