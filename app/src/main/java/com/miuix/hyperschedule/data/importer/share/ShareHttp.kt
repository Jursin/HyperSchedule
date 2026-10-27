package com.miuix.hyperschedule.data.importer.share

import java.net.HttpURLConnection
import java.net.URL

/** 非 2xx 时抛出，调用方按状态码区分「口令不存在」之类的情况 */
internal class ShareHttpException(val code: Int) : IllegalStateException("服务器返回 HTTP $code")

/** 分享码导入用的最小 HTTP 封装 */
internal object ShareHttp {
    private const val TIMEOUT_MILLIS = 20_000

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String = request(url, method = "GET", body = null, headers = headers)

    fun postForm(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): String = request(url, method = "POST", body = body, headers = headers)

    private fun request(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8",
                )
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                // 只把状态码带出去
                connection.errorStream?.close()
                throw ShareHttpException(code)
            }
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }
}
