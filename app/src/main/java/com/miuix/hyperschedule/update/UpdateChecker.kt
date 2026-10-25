package com.miuix.hyperschedule.update

import com.miuix.hyperschedule.data.remote.httpGet
import org.json.JSONObject

private const val Repository = "Jursin/HyperSchedule"

fun checkLatestRelease(proxyUrl: String, currentVersion: String): String {
    val baseUrl = proxyUrl.trim().trimEnd('/').ifBlank { "https://api.github.com" }
    val endpoint = if (baseUrl.endsWith("/repos/$Repository/releases/latest")) {
        baseUrl
    } else {
        "$baseUrl/repos/$Repository/releases/latest"
    }
    val body = httpGet(endpoint, accept = "application/vnd.github+json")
    val tag =
        JSONObject(body)
            .optString("tag_name")
            .removePrefix("v")
            .trim()
    require(tag.isNotBlank()) { "missing tag_name" }
    return if (compareVersions(tag, currentVersion) > 0) {
        "发现新版本 v$tag"
    } else {
        "当前已是最新版本"
    }
}

private fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    val rightParts = right.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    val size = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until size) {
        val difference = (leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 })
        if (difference != 0) return difference.coerceIn(-1, 1)
    }
    return 0
}
