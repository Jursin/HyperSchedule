package com.miuix.hyperschedule.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val Repository = "Jursin/HyperSchedule"
private const val CurrentVersion = "1.0"

fun checkLatestRelease(proxyUrl: String): String {
    val baseUrl = proxyUrl.trim().trimEnd('/').ifBlank { "https://api.github.com" }
    val endpoint = if (baseUrl.endsWith("/repos/$Repository/releases/latest")) {
        baseUrl
    } else {
        "$baseUrl/repos/$Repository/releases/latest"
    }
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "HyperSchedule")
    return try {
        if (connection.responseCode !in 200..299) {
            error("HTTP ${connection.responseCode}")
        }
        val tag = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            .optString("tag_name")
            .removePrefix("v")
            .trim()
        if (tag.isBlank()) error("missing tag_name")
        if (compareVersions(tag, CurrentVersion) > 0) {
            "发现新版本 v$tag"
        } else {
            "当前已是最新版本"
        }
    } finally {
        connection.disconnect()
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
