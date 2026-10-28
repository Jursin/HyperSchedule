package com.miuix.hyperschedule.data.remote

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** 发一次 GET 并返回正文；超时与 UA 全项目统一 */
internal fun httpGet(url: String, accept: String? = null): String =
    httpGetResult(url, accept) { it.bufferedReader().use { reader -> reader.readText() } }

/** 二进制资源（如学校索引）走同一套超时与 UA */
internal fun httpGetBytes(url: String): ByteArray =
    httpGetResult(url, accept = null) { it.use { stream -> stream.readBytes() } }

private fun <T> httpGetResult(
    url: String,
    accept: String?,
    read: (InputStream) -> T,
): T {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    accept?.let { connection.setRequestProperty("Accept", it) }
    connection.setRequestProperty("User-Agent", "HyperSchedule")
    return try {
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
        read(connection.inputStream)
    } finally {
        connection.disconnect()
    }
}
