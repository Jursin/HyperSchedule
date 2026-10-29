package com.miuix.hyperschedule.ui.component

import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.annotation.Keep
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

// 电脑模式下部分教务站点的 WAF 只认桌面浏览器，脚本把 body 登记到原生后由 shouldInterceptRequest 重放

/** 请求体登记表，id 由页面 JS 生成，重放时取走 */
internal object EduPostBodyRegistry {
    private const val MaxEntries = 32

    private val bodies =
        object : LinkedHashMap<String, Pair<String, String>>(16, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, String>>?): Boolean =
                size > MaxEntries
        }

    fun put(id: String, body: String, contentType: String) {
        synchronized(bodies) { bodies[id] = body to contentType }
    }

    fun take(id: String): Pair<String, String>? = synchronized(bodies) { bodies.remove(id) }

    /** 页面销毁时清掉残留 */
    fun clear() {
        synchronized(bodies) { bodies.clear() }
    }
}

/** 对应页面里的 window.WebPostService */
@Keep
@Suppress("unused")
class EduPostBridge {
    @JavascriptInterface
    fun register(id: String, body: String, contentType: String) {
        EduPostBodyRegistry.put(id, body, contentType)
    }
}

/** 拦截 XHR / fetch / 表单提交，把 body 交给原生并打上请求 id */
internal val EduPostInterceptScript =
    """
    (function() {
        if (!window.WebPostService || window._postInterceptInjected) return;
        window._postInterceptInjected = true;

        var idHeader = 'X-WebView-Post-Id';
        var idParam = '_webview_post_id';

        function register(id, body, contentType) {
            try {
                window.WebPostService.register(id, body, contentType || '');
            } catch (e) {
                console.error('WebPostService register error:', e);
            }
        }

        var oldOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            this._method = method;
            this._headers = {};
            return oldOpen.apply(this, arguments);
        };

        var oldSetHeader = XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
            try {
                if (this._headers && header) this._headers[header] = value;
                if (header && header.toLowerCase() === 'x-requested-with') return;
            } catch (e) {
                console.error('XHR setRequestHeader error:', e);
            }
            return oldSetHeader.apply(this, arguments);
        };

        var oldSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function(body) {
            try {
                if (this._method && this._method.toUpperCase() !== 'GET' && body) {
                    var id = 'xhr_' + Date.now() + '_' + Math.random().toString(36).substring(2);
                    var contentType = (this._headers && (this._headers['Content-Type'] || this._headers['content-type'])) || '';
                    var bodyStr = serializeBody(body);
                    if (bodyStr) {
                        if (!contentType) contentType = 'application/x-www-form-urlencoded';
                        register(id, bodyStr, contentType);
                        this.setRequestHeader(idHeader, id);
                    }
                }
            } catch (e) {
                console.error('XHR send intercept error:', e);
            }
            return oldSend.apply(this, arguments);
        };

        if (window.fetch) {
            var oldFetch = window.fetch;
            window.fetch = function(input, init) {
                try {
                    var options = init || {};
                    var method = options.method || (input && typeof input === 'object' ? input.method : null) || 'GET';
                    var body = options.body;
                    if (method.toUpperCase() !== 'GET' && body) {
                        var id = 'fetch_' + Date.now() + '_' + Math.random().toString(36).substring(2);
                        var headers = options.headers || (input && typeof input === 'object' ? input.headers : null);
                        var contentType = readContentType(headers);
                        var bodyStr = serializeBody(body);
                        if (bodyStr) {
                            if (!contentType) contentType = 'application/x-www-form-urlencoded';
                            register(id, bodyStr, contentType);
                            if (!options.headers) options.headers = {};
                            if (window.Headers && options.headers instanceof Headers) {
                                options.headers.set(idHeader, id);
                            } else if (Array.isArray(options.headers)) {
                                options.headers.push([idHeader, id]);
                            } else if (typeof options.headers === 'object') {
                                options.headers[idHeader] = id;
                            }
                        }
                    }
                    return oldFetch.call(this, input, options);
                } catch (e) {
                    console.error('Fetch intercept error:', e);
                    return oldFetch.apply(this, arguments);
                }
            };
        }

        document.addEventListener('submit', function(e) {
            try {
                var form = e.target;
                if (!form || !form.tagName || form.tagName.toLowerCase() !== 'form') return;
                if (!form.method || form.method.toLowerCase() !== 'post') return;
                if (form.querySelector && form.querySelector('input[type="file"]')) return;

                var id = 'form_' + Date.now() + '_' + Math.random().toString(36).substring(2);
                var formData = new FormData(form);
                var submitter = e.submitter || document.activeElement;
                if (submitter && submitter.form === form && submitter.name) {
                    formData.append(submitter.name, submitter.value);
                }
                var params = new URLSearchParams(formData);
                register(id, params.toString(), 'application/x-www-form-urlencoded');

                var action = form.getAttribute('action') || window.location.href;
                var separator = action.indexOf('?') !== -1 ? '&' : '?';
                form.setAttribute('action', action + separator + idParam + '=' + id);
            } catch (err) {
                console.error('Form submit intercept error:', err);
            }
        }, true);

        function serializeBody(body) {
            if (typeof body === 'string') return body;
            if (window.URLSearchParams && body instanceof URLSearchParams) return body.toString();
            if (window.FormData && body instanceof FormData) {
                var params = new URLSearchParams();
                body.forEach(function(value, key) { params.append(key, value); });
                return params.toString();
            }
            return '';
        }

        function readContentType(headers) {
            if (!headers) return '';
            if (window.Headers && headers instanceof Headers) {
                return headers.get('Content-Type') || headers.get('content-type') || '';
            }
            if (Array.isArray(headers)) {
                for (var i = 0; i < headers.length; i++) {
                    if (headers[i] && headers[i][0] && headers[i][0].toLowerCase() === 'content-type') {
                        return headers[i][1];
                    }
                }
                return '';
            }
            if (typeof headers === 'object') {
                return headers['Content-Type'] || headers['content-type'] || '';
            }
            return '';
        }
    })();
    """.trimIndent()

/** 请求 id 的头与查询参数，与页面脚本里的一致 */
private const val PostIdHeader = "X-WebView-Post-Id"
private const val PostIdParam = "_webview_post_id"

/** 需要剔除的请求头，前两个是移动端 WebView 特征 */
private val DroppedHeaders =
    setOf(
        "x-requested-with",
        "x-webview-post-id",
        "cookie",
        "host",
        "content-length",
        "accept-encoding",
        "connection",
        "transfer-encoding",
    )

/** 主框架重放失败时直接放行，让 WebView 自己再发一次 */
internal fun interceptPostRequest(
    request: WebResourceRequest,
    desktopMode: Boolean,
    cookieManager: CookieManager,
): WebResourceResponse? {
    val rawUrl = request.url.toString()
    if (!desktopMode || !rawUrl.startsWith("http")) return null

    // 请求头名大小写不固定，按不敏感方式取
    val requestId =
        request.requestHeaders.entries.firstOrNull { it.key.equals(PostIdHeader, true) }?.value
            ?: request.url.getQueryParameter(PostIdParam)
    // 普通子资源直接走 WebView 原生网络栈
    if (!request.isForMainFrame && requestId == null) return null

    val url = if (request.url.getQueryParameter(PostIdParam) != null) stripPostId(rawUrl) else rawUrl

    val body = requestId?.let(EduPostBodyRegistry::take)
    val method = request.method.uppercase()
    if (method != "GET" && body == null) return null

    return runCatching { replay(url, method, body, request, cookieManager) }.getOrNull()
}

/** 去掉转发用的内部参数，其余 query 保持原始编码 */
private fun stripPostId(rawUrl: String): String {
    val hashIndex = rawUrl.indexOf('#')
    val fragment = if (hashIndex >= 0) rawUrl.substring(hashIndex) else ""
    val withoutFragment = if (hashIndex >= 0) rawUrl.substring(0, hashIndex) else rawUrl
    val queryIndex = withoutFragment.indexOf('?')
    if (queryIndex < 0) return rawUrl
    val base = withoutFragment.substring(0, queryIndex)
    val kept =
        withoutFragment.substring(queryIndex + 1)
            .split('&')
            .filter { it.isNotEmpty() && !it.startsWith("$PostIdParam=") }
            .joinToString("&")
    return if (kept.isEmpty()) base + fragment else "$base?$kept$fragment"
}

private fun replay(
    url: String,
    method: String,
    body: Pair<String, String>?,
    request: WebResourceRequest,
    cookieManager: CookieManager,
): WebResourceResponse? {
    val connection = URL(url).openConnection() as HttpURLConnection
    // 主框架的 3xx 由 WebView 通过 JS 跳转处理，不能在这里跟着走
    connection.instanceFollowRedirects = !request.isForMainFrame
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.requestMethod =
        if (method in SupportedMethods) {
            method
        } else {
            connection.setRequestProperty("X-HTTP-Method-Override", method)
            "POST"
        }

    request.requestHeaders.forEach { (name, value) ->
        if (name.lowercase() !in DroppedHeaders) connection.setRequestProperty(name, value)
    }
    cookieManager.getCookie(url)?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
    connection.setRequestProperty("Accept-Encoding", "identity")

    if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", body.second.ifBlank { "application/x-www-form-urlencoded" })
        connection.outputStream.use { it.write(body.first.toByteArray()) }
    }

    val status = connection.responseCode
    val location = connection.getHeaderField("Location")
    // WebView 自己处理不了 3xx 响应，主框架改用 JS 跳转
    if (status in 300..399) {
        connection.disconnect()
        if (!request.isForMainFrame || location.isNullOrBlank()) return null
        val target = runCatching { URI(url).resolve(location).toString() }.getOrDefault(location)
        val escaped = target.replace("\\", "\\\\").replace("'", "\\'")
        val html = "<html><script>window.location.replace('$escaped');</script></html>"
        return WebResourceResponse(
            "text/html",
            "UTF-8",
            200,
            "OK",
            mapOf("Cache-Control" to "no-cache"),
            html.byteInputStream(),
        )
    }

    connection.headerFields
        .filterKeys { it != null && it.equals("Set-Cookie", true) }
        .values
        .flatten()
        .forEach { cookieManager.setCookie(url, it) }
    cookieManager.flush()

    val contentType = connection.contentType ?: "text/html"
    val mimeType = contentType.substringBefore(';').trim().ifBlank { "text/html" }
    val charset =
        Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(contentType)
            ?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: "UTF-8"

    val headers =
        buildMap {
            connection.headerFields.forEach { (name, values) ->
                if (name != null && !name.equals("Content-Encoding", true) && !name.equals("Content-Length", true)) {
                    put(name, values.joinToString(", "))
                }
            }
        }

    val raw = if (status >= 400) connection.errorStream ?: connection.inputStream else connection.inputStream
    val stream =
        when {
            connection.getHeaderField("Content-Encoding").orEmpty().contains("gzip", true) -> GZIPInputStream(raw)
            connection.getHeaderField("Content-Encoding").orEmpty().contains("deflate", true) -> InflaterInputStream(raw)
            else -> raw
        }

    return WebResourceResponse(mimeType, charset, status, connection.responseMessage ?: "OK", headers, stream)
}

private val SupportedMethods = setOf("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE")
