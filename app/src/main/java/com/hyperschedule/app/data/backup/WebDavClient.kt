package com.hyperschedule.app.data.backup

import android.util.Base64
import com.hyperschedule.app.data.remote.AppUserAgent
import com.hyperschedule.app.data.remote.readCapped
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val READ_TIMEOUT_MS = 30_000

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

/** 一次 WebDAV 请求的结果，[challenge] 是 401 时服务器声明的认证方式 */
private class DavResponse(val code: Int, val text: String, val challenge: String? = null) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** WebDAV 客户端，只管目录与文件这几个动作 */
class WebDavClient(
    serverUrl: String,
    username: String,
    password: String,
    folder: String = BACKUP_FOLDER,
) : RemoteBackupClient {
    private val auth =
        "Basic " + Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /** 服务器根地址，地址填错时靠它先探一次 */
    private val baseUrl = serverUrl.trim().trimEnd('/')

    /** 凭据只发给这个域名，重定向到别处时不带 */
    private val host = runCatching { URL(baseUrl).host }.getOrNull()

    /** 路径层级，界面已校验过，这里只按 / 拆开，留空则回落到默认名 */
    private val segments = backupFolderSegments(folder)

    /** 拼接时展示的路径 */
    private val folderPath = segments.joinToString("/")

    /** 集合地址按规范以 / 结尾，少了会被部分服务器当成文件而返回 301 或 409 */
    private val dirUrl = "$baseUrl/" + segments.joinToString("/") { encodeSegment(it) } + "/"

    private fun fileUrl(name: String): String = "$dirUrl$name"

    /** 测试连接，路径不存在就顺手建出来 */
    override suspend fun testConnection(): String =
        withContext(Dispatchers.IO) {
            val response = runCatching { request("PROPFIND", dirUrl, depth = "0") }
                .getOrElse { throw BackupException(networkMessage(it)) }
            when {
                response.isSuccessful -> "连接成功"
                response.code == 401 || response.code == 403 -> throw BackupException(authMessage(response))
                response.code == 404 -> {
                    // 先确认服务器地址本身可达，否则地址填错也会报「连接成功」
                    val root = request("PROPFIND", "$baseUrl/", depth = "0")
                    if (root.code == 401 || root.code == 403) throw BackupException(authMessage(root))
                    if (!root.isSuccessful) throw BackupException("服务器返回 ${root.code}")
                    if (!ensureDir()) throw BackupException("无法创建备份路径 $folderPath")
                    "连接成功，已创建备份路径 $folderPath"
                }
                else -> throw BackupException("服务器返回 ${response.code}")
            }
        }

    /** 逐层建出备份路径，返回是否可用 */
    private suspend fun ensureDir(): Boolean =
        withContext(Dispatchers.IO) {
            var current = "$baseUrl/"
            for (segment in segments) {
                current += encodeSegment(segment) + "/"
                if (request("PROPFIND", current, depth = "0").isSuccessful) continue
                // 201 新建成功，405 表示已存在
                val made = request("MKCOL", current).code
                if (made !in 200..299 && made != 405) return@withContext false
            }
            true
        }

    /** 备份路径里的备份文件，按时间从新到旧 */
    override suspend fun list(): List<BackupFile> =
        withContext(Dispatchers.IO) {
            val response = request("PROPFIND", dirUrl, depth = "1", body = PROPFIND_BODY)
            // 路径还没建就等于没有备份
            if (response.code == 404) return@withContext emptyList()
            if (!response.isSuccessful) throw BackupException("读取云端备份列表失败（HTTP ${response.code}）")
            parseList(response.text)
        }

    /** 上传一份备份，路径不在了就顺手补建 */
    override suspend fun upload(name: String, text: String) =
        withContext(Dispatchers.IO) {
            val ready = runCatching { ensureDir() }.getOrElse { throw BackupException(networkMessage(it)) }
            if (!ready) throw BackupException("无法创建备份路径 $folderPath")
            val response =
                request("PUT", fileUrl(name), body = text, contentType = "application/json; charset=utf-8")
            if (!response.isSuccessful) throw BackupException(uploadMessage(response))
        }

    override suspend fun download(name: String): String =
        withContext(Dispatchers.IO) {
            val response = request("GET", fileUrl(name))
            when {
                response.isSuccessful -> response.text
                response.code == 404 -> throw BackupException("云端已经没有这份备份了")
                response.code == 401 || response.code == 403 -> throw BackupException(authMessage(response))
                else -> throw BackupException("下载失败（HTTP ${response.code}）")
            }
        }

    /** 删除一份备份，已经不在了也算成功 */
    override suspend fun delete(name: String) =
        withContext(Dispatchers.IO) {
            val code = request("DELETE", fileUrl(name)).code
            if (code !in 200..299 && code != 404) throw BackupException("删除失败（HTTP $code）")
        }

    private fun uploadMessage(response: DavResponse): String =
        when (response.code) {
            401, 403 -> authMessage(response)
            404 -> "备份路径不存在（HTTP 404）"
            409 -> "备份路径不存在，无法写入（HTTP 409）"
            423 -> "云端文件被锁定，请稍后重试"
            507 -> "云端空间已满"
            else -> "上传失败（HTTP ${response.code}）"
        }

    /** 认证或权限失败，只认 Digest 的服务器单独说明 */
    private fun authMessage(response: DavResponse): String {
        if (response.code == 403) return "没有访问权限，请检查账号的读写权限"
        val scheme = response.challenge?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        return if (scheme != null && !scheme.equals("Basic", ignoreCase = true)) {
            "服务器要求 $scheme 认证，本应用只支持 Basic"
        } else {
            "认证失败，请检查用户名和密码"
        }
    }

    /** 扩展方法不在 HttpURLConnection 的白名单里，只能绕开它的检查 */
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
            // PROPFIND 带请求体，MKCOL 声明长度 0，缺了部分服务器回 400 或 411
            val payload =
                when {
                    body != null -> body.toByteArray(Charsets.UTF_8)
                    method == "PROPFIND" -> PROPFIND_BODY.toByteArray(Charsets.UTF_8)
                    method == "MKCOL" -> ByteArray(0)
                    else -> null
                }
            if (payload != null) {
                if (payload.isNotEmpty()) {
                    connection.setRequestProperty("Content-Type", contentType ?: "application/xml; charset=utf-8")
                }
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
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
            val text = stream?.use { it.readCapped().decodeToString() }.orEmpty()
            val challenge = if (code == 401) connection.getHeaderField("WWW-Authenticate") else null
            return DavResponse(code, text, challenge)
        } finally {
            connection.disconnect()
        }
    }

    /** 设置完回读确认生效，避免悄悄发成 GET */
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

    /** 连接自身与内部的委托对象，从外到内 */
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
            // href 可能写成绝对地址，取最后一段就是文件名
            val raw = href.substringAfterLast('/', "")
            // 目录自身以 / 结尾，这里取不到文件名，正好跳过
            if (raw.isEmpty()) continue
            val name = decodeSegment(raw)
            val size = CONTENT_LENGTH.find(block.value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            backupFileOf(name, size)?.let { add(it) }
        }
    }.sortedByDescending { it.time }
}

/** 路径段编码，中文与空格都要转义，+ 在路径里不是空格也不能留 */
private fun encodeSegment(segment: String): String =
    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

/** 路径段解码，+ 在路径里是字面加号 */
private fun decodeSegment(raw: String): String =
    runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
