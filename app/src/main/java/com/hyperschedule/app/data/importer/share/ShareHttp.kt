package com.hyperschedule.app.data.importer.share

import com.hyperschedule.app.data.remote.AppHttp
import com.hyperschedule.app.data.remote.readCapped
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 非 2xx 时抛出，调用方按状态码区分「口令不存在」之类的情况 */
internal class ShareHttpException(val code: Int) : IllegalStateException("服务器返回 HTTP $code")

/** 分享码导入用的最小 HTTP 封装 */
internal object ShareHttp {
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
        val payload = body?.toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaTypeOrNull())
        val builder = Request.Builder().url(url).method(method, payload)
        headers.forEach { (key, value) -> builder.header(key, value) }
        AppHttp.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw ShareHttpException(response.code)
            return response.body.readCapped().decodeToString()
        }
    }
}
