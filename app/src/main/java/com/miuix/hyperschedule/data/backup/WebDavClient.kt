package com.miuix.hyperschedule.data.backup

import android.util.Base64
import com.miuix.hyperschedule.data.remote.AppUserAgent
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 云端备份目录，固定在服务器根目录下，别的目录一概不碰 */
const val WEBDAV_FOLDER = "HyperSchedule"

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000

/** 跟随重定向的最大次数 */
private const val MAX_REDIRECTS = 2

/** HttpURLConnection 认识的方法，其余得绕开它的白名单 */
private val BUILT_IN_METHODS = setOf("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE")

private const val PROPFIND_BODY =
    """<?xml version="1.0" encoding="utf-8"?>""" +
        """<d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/></d:prop></d:propfind>"""

/** XML 里的一个 `<response>` 块，命名空间前缀可有可无 */
private val RESPONSE_BLOCK =
    Regex("<(?:\\w+:)?response\\b[^>]*>.*?</(?:\\w+:)?response>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HREF = Regex("<(?:\\w+:)?href>([^<]+)</(?:\\w+:)?href>", RegexOption.IGNORE_CASE)
private val CONTENT_LENGTH = Regex("<(?:\\w+:)?getcontentlength>\\s*(\\d+)\\s*<", RegexOption.IGNORE_CASE)

/** 一次 WebDAV 请求的结果 */
private class DavResponse(val code: Int, val text: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** WebDAV 客户端，只管目录与文件这几个动作 */
class WebDavClient(
    serverUrl: String,
    username: String,
    password: String,
) {
    private val auth =
        "Basic " + Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /** 服务器根地址，用来判断地址本身是否可达 */
    private val baseUrl = serverUrl.trim().trimEnd('/')

    /** 凭据只发给这个域名，重定向到别处时不带 */
    private val host = runCatching { URL(baseUrl).host }.getOrNull()

    /** 备份目录地址 */
    val dirUrl: String = "$baseUrl/$WEBDAV_FOLDER"

    private fun fileUrl(name: String): String = "$dirUrl/$name"

    /** 测试连接，目录没建就顺手建出来 */
    suspend fun testConnection(): String =
        withContext(Dispatchers.IO) {
            val response = runCatching { request("PROPFIND", dirUrl, depth = "0") }
                .getOrElse { throw BackupException(networkMessage(it)) }
            when {
                response.isSuccessful -> "连接成功"
                response.code == 401 || response.code == 403 -> throw BackupException("认证失败，请检查用户名和密码")
                response.code == 404 -> {
                    // 先确认服务器地址本身可达，否则地址填错也会报「连接成功」
                    val root = request("PROPFIND", "$baseUrl/", depth = "0")
                    if (root.code == 401 || root.code == 403) throw BackupException("认证失败，请检查用户名和密码")
                    if (!root.isSuccessful) throw BackupException("服务器返回 ${root.code}")
                    if (!ensureDir()) throw BackupException("无法创建云端目录 $WEBDAV_FOLDER")
                    "连接成功，已创建 $WEBDAV_FOLDER 目录"
                }
                else -> throw BackupException("服务器返回 ${response.code}")
            }
        }

    /** 确保备份目录存在，返回是否可用 */
    suspend fun ensureDir(): Boolean =
        withContext(Dispatchers.IO) {
            if (request("PROPFIND", dirUrl, depth = "0").isSuccessful) return@withContext true
            // 201 新建成功，405 表示已存在
            val made = request("MKCOL", dirUrl).code
            made in 200..299 || made == 405
        }

    /** 云端目录里的备份文件，按时间从新到旧 */
    suspend fun list(): List<BackupFile> =
        withContext(Dispatchers.IO) {
            val response = request("PROPFIND", dirUrl, depth = "1", body = PROPFIND_BODY)
            // 目录还没建就等于没有备份，不当成错误
            if (response.code == 404) return@withContext emptyList()
            if (!response.isSuccessful) throw BackupException("读取云端备份列表失败（HTTP ${response.code}）")
            parseList(response.text)
        }

    /** 上传一份备份，覆盖同名文件；目录不在了就顺手补建 */
    suspend fun upload(name: String, text: String) =
        withContext(Dispatchers.IO) {
            val ready = runCatching { ensureDir() }.getOrElse { throw BackupException(networkMessage(it)) }
            if (!ready) throw BackupException("无法创建云端目录 $WEBDAV_FOLDER")
            val response =
                request("PUT", fileUrl(name), body = text, contentType = "application/json; charset=utf-8")
            if (!response.isSuccessful) throw BackupException(uploadMessage(response.code))
        }

    /** 下载一份备份的原文 */
    suspend fun download(name: String): String =
        withContext(Dispatchers.IO) {
            val response = request("GET", fileUrl(name))
            when {
                response.isSuccessful -> response.text
                response.code == 404 -> throw BackupException("云端已经没有这份备份了")
                response.code == 401 || response.code == 403 -> throw BackupException("认证失败，请检查用户名和密码")
                else -> throw BackupException("下载失败（HTTP ${response.code}）")
            }
        }

    /** 删除一份备份；已经不在了也算成功 */
    suspend fun delete(name: String) =
        withContext(Dispatchers.IO) {
            val code = request("DELETE", fileUrl(name)).code
            if (code !in 200..299 && code != 404) throw BackupException("删除失败（HTTP $code）")
        }

    private fun uploadMessage(code: Int): String =
        when (code) {
            401 -> "认证失败，请检查用户名和密码"
            403 -> "没有写入权限，请检查账号的读写权限"
            507 -> "云端空间已满"
            409 -> "云端目录不存在（HTTP 409）"
            else -> "上传失败（HTTP $code）"
        }

    private fun networkMessage(error: Throwable): String =
        if (error is IOException) "连接失败：${error.message ?: "网络不可用"}" else (error.message ?: "连接失败")

    /** 发一次 WebDAV 请求；扩展方法不在 HttpURLConnection 的白名单里，只能改它的内部字段 */
    private fun request(
        method: String,
        url: String,
        depth: String? = null,
        body: String? = null,
        contentType: String? = null,
        redirects: Int = MAX_REDIRECTS,
    ): DavResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.applyMethod(method)
            // 重定向到别的域名时不能把凭据带过去
            if (connection.url.host.equals(host, ignoreCase = true)) {
                connection.setRequestProperty("Authorization", auth)
            }
            connection.setRequestProperty("User-Agent", AppUserAgent)
            depth?.let { connection.setRequestProperty("Depth", it) }
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setRequestProperty("Content-Type", contentType ?: "application/xml; charset=utf-8")
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            if (code in 301..308 && redirects > 0) {
                val location = connection.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
                    return request(method, next, depth, body, contentType, redirects - 1)
                }
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()
            return DavResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    /** 设置请求方法，设置后回读确认真的生效，避免悄悄发成 GET */
    private fun HttpURLConnection.applyMethod(method: String) {
        if (method in BUILT_IN_METHODS) {
            requestMethod = method
            return
        }
        runCatching { requestMethod = method }
        if (requestMethod == method) return
        // Android 的 https 连接是包装类，真正发请求的是内部的委托对象，方法字段也在它身上
        val targets = methodTargets()
        targets.forEach { target -> setMethodField(target, method) }
        val delegated = targets.last().requestMethod == method
        if (requestMethod != method || !delegated) throw BackupException("当前系统不支持 $method 请求")
    }

    /** 连接自身与它内部的委托对象，从外到内 */
    private fun HttpURLConnection.methodTargets(): List<HttpURLConnection> {
        val targets = mutableListOf(this)
        var current: Any = this
        repeat(2) {
            val delegate =
                runCatching {
                        current.javaClass.declaredFields
                            .firstOrNull { it.name == "delegate" && HttpURLConnection::class.java.isAssignableFrom(it.type) }
                            ?.also { it.isAccessible = true }
                            ?.get(current)
                    }
                    .getOrNull()
            if (delegate is HttpURLConnection) {
                targets += delegate
                current = delegate
            } else {
                return targets
            }
        }
        return targets
    }

    private fun setMethodField(connection: HttpURLConnection, method: String) {
        val field =
            generateSequence<Class<*>>(connection.javaClass) { it.superclass }.firstNotNullOfOrNull { type ->
                runCatching { type.getDeclaredField("method") }.getOrNull()
            } ?: return
        runCatching {
            field.isAccessible = true
            field.set(connection, method)
        }
    }

    private fun parseList(xml: String): List<BackupFile> = buildList {
        for (block in RESPONSE_BLOCK.findAll(xml)) {
            val href = HREF.find(block.value)?.groupValues?.get(1) ?: continue
            val raw = href.substringAfterLast('/', "")
            // 目录自身以 / 结尾，这里取不到文件名，正好跳过
            if (raw.isEmpty()) continue
            val name = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            val size = CONTENT_LENGTH.find(block.value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            backupFileOf(name, size)?.let { add(it) }
        }
    }.sortedByDescending { it.time }
}
