package com.hyperschedule.app.data.backup

import java.net.URLEncoder
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** AWS 签名版本 4，方法、路径、查询串、请求头与载荷哈希任一项对不上都会 403 */
internal object S3Signature {
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"

    /** 空载荷的 SHA-256，GET/HEAD/DELETE 都用它 */
    val EMPTY_PAYLOAD_HASH: String = sha256Hex(ByteArray(0))

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)

    /** 签好的请求，最终地址与必须原样带上的请求头 */
    class Signed(val url: String, val headers: Map<String, String>)

    fun sign(
        endpoint: String,
        bucket: String,
        region: String,
        accessKeyId: String,
        secretAccessKey: String,
        pathStyle: Boolean,
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        payloadHash: String = EMPTY_PAYLOAD_HASH,
        contentType: String? = null,
        now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
    ): Signed {
        val dateStamp = now.format(dateFormatter)
        val amzDate = now.format(timestampFormatter)

        val https = !endpoint.lowercase(Locale.ROOT).startsWith("http://")
        val host = endpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')
        // path-style 把桶名放进路径，virtual-host-style 放进域名
        val requestHost = if (pathStyle) host else "$bucket.$host"
        // 桶级请求的路径是 /bucket 而不是 /bucket/，空路径按规范写成 /
        val canonicalUri = (if (pathStyle) "/$bucket$path" else path).let { if (it.isEmpty()) "/" else encodePath(it) }
        val canonicalQuery =
            query.entries
                .sortedBy { it.key }
                .joinToString("&") { "${it.key.encodeRfc3986()}=${it.value.encodeRfc3986()}" }

        val headers = sortedMapOf<String, String>()
        headers["host"] = requestHost
        headers["x-amz-content-sha256"] = payloadHash
        headers["x-amz-date"] = amzDate
        contentType?.let { headers["content-type"] = it }

        val signedHeaders = headers.keys.joinToString(";")
        val canonicalHeaders = headers.entries.joinToString("") { "${it.key}:${it.value.trim()}\n" }
        val canonicalRequest =
            "$method\n$canonicalUri\n$canonicalQuery\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        val scope = "$dateStamp/$region/$SERVICE/aws4_request"
        val stringToSign = "$ALGORITHM\n$amzDate\n$scope\n${sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))}"
        val signature = hmacSha256(signingKey(secretAccessKey, dateStamp, region), stringToSign).toHex()

        // host 交给 HTTP 客户端自己带，这里不回写
        val resultHeaders = headers.filterKeys { it != "host" }.toMutableMap()
        resultHeaders["Authorization"] =
            "$ALGORITHM Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature"

        val url =
            buildString {
                append(if (https) "https://" else "http://")
                append(requestHost)
                append(canonicalUri)
                if (canonicalQuery.isNotEmpty()) append("?").append(canonicalQuery)
            }
        return Signed(url, resultHeaders)
    }

    private fun signingKey(secret: String, dateStamp: String, region: String): ByteArray {
        val kDate = hmacSha256("AWS4$secret".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, SERVICE)
        return hmacSha256(kService, "aws4_request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** 路径逐段编码，斜杠保留 */
    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { if (it.isEmpty()) it else it.encodeRfc3986() }

    /** AWS 编码规则，空格是 %20，* 是 %2A，~ 不转义 */
    private fun String.encodeRfc3986(): String =
        URLEncoder.encode(this, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
}
