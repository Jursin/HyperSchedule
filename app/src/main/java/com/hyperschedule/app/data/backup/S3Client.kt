package com.hyperschedule.app.data.backup

import android.util.Xml
import com.hyperschedule.app.data.remote.AppHttp
import com.hyperschedule.app.data.remote.AppUserAgent
import com.hyperschedule.app.data.remote.readCapped
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser

/** 一次 S3 请求的结果 */
private class S3Response(val code: Int, val text: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** 区域不对时 S3 回 301，跟进跳转会被当成别的错误，故关掉重定向 */
private val S3Http: OkHttpClient =
    AppHttp.newBuilder().readTimeout(60, TimeUnit.SECONDS).followRedirects(false).build()

/** 凭据、签名或权限不对时服务器回的错误码 */
private val AUTH_ERRORS = setOf("InvalidAccessKeyId", "SignatureDoesNotMatch", "AccessDenied", "InvalidSecurity")

/** 区域默认值，R2 这类服务不区分区域 */
const val S3_DEFAULT_REGION = "auto"

/** 存储桶名校验，没填不算错误，界面另有未配置提示 */
internal fun s3BucketError(bucket: String): String? =
    when {
        bucket.isBlank() -> null
        '/' in bucket -> "存储桶名不能包含 /"
        bucket.any { it.isWhitespace() } -> "存储桶名不能包含空格"
        else -> null
    }

/** 区域校验，只挡明显不对的输入 */
internal fun s3RegionError(region: String): String? =
    when {
        region.isBlank() -> null
        region.any { it.isWhitespace() || it == '/' } -> "区域名不能包含空格或 /"
        else -> null
    }

/** S3 客户端，兼容 AWS S3、R2、MinIO 这类同协议服务，签名现算 */
class S3Client(
    endpoint: String,
    bucket: String,
    region: String,
    accessKeyId: String,
    secretAccessKey: String,
    private val pathStyle: Boolean = true,
    folder: String = BACKUP_FOLDER,
) : RemoteBackupClient {
    private val endpoint = endpoint.trim().trimEnd('/')
    private val bucket = bucket.trim()
    private val region = region.trim().ifBlank { S3_DEFAULT_REGION }
    private val accessKeyId = accessKeyId.trim()
    private val secretAccessKey = secretAccessKey.trim()

    /** 对象前缀，别的对象一概不碰 */
    private val prefix = backupFolderSegments(folder).joinToString("/") + "/"

    override suspend fun testConnection(): String =
        withContext(Dispatchers.IO) {
            // 列一个对象就足以验证地址、签名与权限
            val response =
                runCatching { request("GET", "", listQuery(maxKeys = 1)) }
                    .getOrElse { throw BackupException(networkMessage(it)) }
            if (!response.isSuccessful) throw BackupException(errorMessage(response))
            "连接成功"
        }

    override suspend fun list(): List<BackupFile> =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<BackupFile>()
            var token: String? = null
            var page = 0
            do {
                val query = listQuery(maxKeys = 1000)
                token?.let { query["continuation-token"] = it }
                val response = request("GET", "", query)
                if (!response.isSuccessful) throw BackupException(errorMessage(response))
                val (listing, nextToken) = parsePage(response.text)
                files += listing
                token = nextToken
            } while (token != null && ++page < MAX_PAGES)
            files.sortedByDescending { it.time }
        }

    override suspend fun upload(name: String, text: String) =
        withContext(Dispatchers.IO) {
            val body = text.toByteArray(Charsets.UTF_8)
            val response =
                runCatching { request("PUT", objectPath(name), payload = body, contentType = JSON_CONTENT_TYPE) }
                    .getOrElse { throw BackupException(networkMessage(it)) }
            if (!response.isSuccessful) throw BackupException(errorMessage(response))
        }

    override suspend fun download(name: String): String =
        withContext(Dispatchers.IO) {
            val response =
                runCatching { request("GET", objectPath(name)) }
                    .getOrElse { throw BackupException(networkMessage(it)) }
            if (response.isSuccessful) response.text else throw BackupException(errorMessage(response))
        }

    override suspend fun delete(name: String) =
        withContext(Dispatchers.IO) {
            val response =
                runCatching { request("DELETE", objectPath(name)) }
                    .getOrElse { throw BackupException(networkMessage(it)) }
            // 已经不在了也算删掉
            if (!response.isSuccessful && response.code != 404) throw BackupException(errorMessage(response))
        }

    private fun listQuery(maxKeys: Int): MutableMap<String, String> =
        mutableMapOf("list-type" to "2", "prefix" to prefix, "max-keys" to maxKeys.toString())

    private fun objectPath(name: String): String = "/$prefix$name"

    private fun errorMessage(response: S3Response): String {
        val code = runCatching { parseErrorCode(response.text) }.getOrNull().orEmpty()
        return when {
            // 对象没了要单独说，否则用户会以为桶被删了
            code == "NoSuchKey" -> "云端已经没有这份备份了"
            response.code == 507 -> "云端空间已满"
            response.code == 401 || response.code == 403 || code in AUTH_ERRORS ->
                "认证失败，请检查访问密钥 ID、机密访问密钥与区域"
            code == "NoSuchBucket" || response.code == 404 -> "存储桶 $bucket 不存在"
            response.code == 301 || code == "PermanentRedirect" || code == "AuthorizationHeaderMalformed" ->
                "区域不匹配，请检查区域设置"
            code == "RequestTimeTooSkewed" -> "设备时间与服务器相差过大，请校准系统时间"
            code.isNotEmpty() -> "服务器返回 ${response.code}（$code）"
            else -> "服务器返回 ${response.code}"
        }
    }

    /** 只认备份路径下的备份文件，别的对象一律忽略；顺带取翻页标记 */
    private fun parsePage(xml: String): Pair<List<BackupFile>, String?> {
        val files = mutableListOf<BackupFile>()
        var token: String? = null
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var key: String? = null
            var size = 0L
            var inside = false
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG ->
                        when (parser.localTag()) {
                            "Contents" -> {
                                inside = true
                                key = null
                                size = 0L
                            }
                            "Key" -> if (inside) key = parser.nextText()
                            "Size" -> if (inside) size = parser.nextText().trim().toLongOrNull() ?: 0L
                            "NextContinuationToken" ->
                                token = parser.nextText().takeIf { it.isNotBlank() }
                        }
                    XmlPullParser.END_TAG ->
                        if (inside && parser.localTag() == "Contents") {
                            inside = false
                            val name = key?.removePrefix(prefix).orEmpty()
                            if (name.isNotEmpty() && '/' !in name) backupFileOf(name, size)?.let(files::add)
                        }
                }
                parser.next()
            }
        }
        return files to token
    }

    /** 错误响应里的 `<Code>`，正文不是 XML 时返回 null */
    private fun parseErrorCode(xml: String): String? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.localTag() == "Code") {
                return parser.nextText()
            }
            parser.next()
        }
        return null
    }

    private fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        payload: ByteArray? = null,
        contentType: String? = null,
    ): S3Response {
        val signed =
            S3Signature.sign(
                endpoint = endpoint,
                bucket = bucket,
                region = region,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                pathStyle = pathStyle,
                method = method,
                path = path,
                query = query,
                payloadHash = payload?.let(S3Signature::sha256Hex) ?: S3Signature.EMPTY_PAYLOAD_HASH,
                contentType = contentType,
            )
        val builder =
            Request.Builder()
                .url(signed.url)
                .header("User-Agent", AppUserAgent)
                .method(method, payload?.toRequestBody(contentType?.toMediaTypeOrNull()))
        signed.headers.forEach { (name, value) -> builder.header(name, value) }
        S3Http.newCall(builder.build()).execute().use { response ->
            return S3Response(response.code, response.body.readCapped().decodeToString())
        }
    }

    private companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"

        /** 翻页上限，避免服务端一直返回截断标记时死循环 */
        const val MAX_PAGES = 10
    }
}

/** 带命名空间的标签取本地名，前缀可有可无 */
private fun XmlPullParser.localTag(): String = name.orEmpty().substringAfter(':')
