package com.miuix.hyperschedule.data.remote

import java.net.HttpURLConnection
import java.net.URL

/** 发一次 GET 并返回正文；超时与 UA 全项目统一 */
internal fun httpGet(url: String, accept: String? = null): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    accept?.let { connection.setRequestProperty("Accept", it) }
    connection.setRequestProperty("User-Agent", "HyperSchedule")
    return try {
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
