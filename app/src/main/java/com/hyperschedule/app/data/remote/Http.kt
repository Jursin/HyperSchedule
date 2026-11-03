package com.hyperschedule.app.data.remote

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

internal const val AppUserAgent = "HyperSchedule"

/** 单次读取上限，远端与用户选的文件都可能被塞大体积内容，超过就直接报错而不是把内存吃干 */
internal const val MaxReadBytes = 16 * 1024 * 1024

/** 全项目共用的连接与超时，UA 由各请求自己带 */
internal val AppHttp: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

/** 重定向由调用方决定怎么走，例如 WebDAV 要带着同一方法重发 */
internal val AppHttpNoRedirect: OkHttpClient = AppHttp.newBuilder().followRedirects(false).build()

/** 按块读到内存，超过 [limit] 抛异常 */
internal fun InputStream.readCapped(limit: Int = MaxReadBytes): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (true) {
        val read = read(chunk)
        if (read < 0) return buffer.toByteArray()
        if (buffer.size() + read > limit) throw IOException("内容超过 ${limit / 1024 / 1024} MB，已停止读取")
        buffer.write(chunk, 0, read)
    }
}

/** 响应体同样按块读，超过 [limit] 抛异常 */
internal fun ResponseBody.readCapped(limit: Int = MaxReadBytes): ByteArray = byteStream().readCapped(limit)

/** 发一次 GET 并返回正文，超时与 UA 全项目统一 */
internal fun httpGet(url: String, accept: String? = null): String =
    httpGetResult(url, accept) { it.readCapped().decodeToString() }

/** 二进制资源（如学校索引）走同一套超时与 UA */
internal fun httpGetBytes(url: String): ByteArray =
    httpGetResult(url, accept = null) { it.readCapped() }

/** gitee raw 偶发返回反盗链签名页，跟随签名链接再取一次 */
internal fun httpGetGiteeRaw(url: String): ByteArray {
    val bytes = httpGetBytes(url)
    if (bytes.size > 2048) return bytes
    val head = bytes.decodeToString().trimStart()
    if (!head.startsWith("<") || !head.contains("raw.giteeusercontent.com")) return bytes
    val signed =
        Regex("href=\"([^\"]+)\"").find(head)?.groupValues?.get(1)?.replace("&amp;", "&")
            ?: error("gitee 地址被拦截")
    return httpGetBytes(signed)
}

private fun <T> httpGetResult(
    url: String,
    accept: String?,
    read: (ResponseBody) -> T,
): T {
    val builder = Request.Builder().url(url).header("User-Agent", AppUserAgent)
    accept?.let { builder.header("Accept", it) }
    AppHttp.newCall(builder.build()).execute().use { response ->
        check(response.isSuccessful) { "HTTP ${response.code}" }
        return read(response.body)
    }
}
