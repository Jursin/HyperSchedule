package com.hyperschedule.app.data.backup

import android.util.Base64
import android.util.Xml
import com.hyperschedule.app.data.remote.AppHttpNoRedirect
import com.hyperschedule.app.data.remote.AppUserAgent
import com.hyperschedule.app.data.remote.readCapped
import java.io.StringReader
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser

private const val MAX_REDIRECTS = 2

private const val PROPFIND_BODY =
    """<?xml version="1.0" encoding="utf-8"?>""" +
        """<d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/></d:prop></d:propfind>"""

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
            parseBackupList(response.text)
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

    /** 各动作统一走这里，重定向自己处理，好让非标准方法原样重发 */
    private fun request(
        method: String,
        url: String,
        depth: String? = null,
        body: String? = null,
        contentType: String? = null,
        redirects: Int = MAX_REDIRECTS,
    ): DavResponse {
        val payload =
            when {
                body != null -> body.toByteArray(Charsets.UTF_8)
                method == "PROPFIND" -> PROPFIND_BODY.toByteArray(Charsets.UTF_8)
                method == "MKCOL" -> ByteArray(0)
                else -> null
            }
        val builder = Request.Builder().url(url).header("User-Agent", AppUserAgent)
        // 重定向到别的域名时不能把凭据带过去
        if (runCatching { URL(url).host }.getOrNull().equals(host, ignoreCase = true)) {
            builder.header("Authorization", auth)
        }
        depth?.let { builder.header("Depth", it) }
        val type = contentType ?: if (method == "PROPFIND") "application/xml; charset=utf-8" else null
        builder.method(method, payload?.toRequestBody(type?.toMediaTypeOrNull()))
        AppHttpNoRedirect.newCall(builder.build()).execute().use { response ->
            val code = response.code
            if (code in 301..308 && redirects > 0) {
                val location = response.header("Location")
                if (!location.isNullOrBlank()) {
                    val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
                    return request(method, next, depth, body, contentType, redirects - 1)
                }
            }
            val text = response.body.readCapped().decodeToString()
            val challenge = if (code == 401) response.header("WWW-Authenticate") else null
            return DavResponse(code, text, challenge)
        }
    }
}

/** 逐个 `<response>` 取 href 与长度，目录自身没有文件名自然跳过 */
internal fun parseBackupList(xml: String): List<BackupFile> {
    val files = mutableListOf<BackupFile>()
    runCatching {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var href: String? = null
        var size = 0L
        var inside = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG ->
                    when (parser.localTag()) {
                        "response" -> {
                            inside = true
                            href = null
                            size = 0L
                        }
                        "href" -> if (inside) href = parser.nextText()
                        "getcontentlength" ->
                            if (inside) size = parser.nextText().trim().toLongOrNull() ?: 0L
                    }
                XmlPullParser.END_TAG ->
                    if (inside && parser.localTag() == "response") {
                        inside = false
                        // href 可能写成绝对地址，取最后一段就是文件名
                        val name = href?.substringAfterLast('/', "")?.let(::decodeSegment).orEmpty()
                        if (name.isNotEmpty()) backupFileOf(name, size)?.let(files::add)
                    }
            }
            parser.next()
        }
    }
    return files.sortedByDescending { it.time }
}

/** 带命名空间的标签取本地名，前缀可有可无 */
private fun XmlPullParser.localTag(): String = name.orEmpty().substringAfter(':')

/** 路径段编码，中文与空格都要转义，+ 在路径里不是空格也不能留 */
internal fun encodeSegment(segment: String): String =
    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

/** 路径段解码，+ 在路径里是字面加号 */
internal fun decodeSegment(raw: String): String =
    runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
