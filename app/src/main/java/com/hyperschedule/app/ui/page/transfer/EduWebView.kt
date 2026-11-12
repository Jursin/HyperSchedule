package com.hyperschedule.app.ui.page.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.hyperschedule.app.ui.component.EduPostBridge
import com.hyperschedule.app.ui.component.EduWebBridge
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
internal fun createWebView(context: Context): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        val view = this
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
    }

internal fun WebView.attachBridges(bridge: EduWebBridge) {
    addJavascriptInterface(bridge, "AndroidBridge")
    addJavascriptInterface(EduPostBridge(), "WebPostService")
}

internal fun normalizeUrl(url: String): String =
    if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"

internal fun openExternal(context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "javascript") return false
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    return true
}

/** 电脑模式用桌面 UA 并关闭宽视口，手机模式恢复默认 */
internal fun applyDesktopMode(webView: WebView, desktop: Boolean) {
    val context = webView.context
    webView.settings.apply {
        userAgentString = if (desktop) desktopUserAgent(context) else WebSettings.getDefaultUserAgent(context)
        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        useWideViewPort = !desktop
        loadWithOverviewMode = !desktop
    }
    webView.setInitialScale(if (desktop) 100 else 0)
}

internal fun desktopUserAgent(context: Context): String {
    val real = WebSettings.getDefaultUserAgent(context)
    val chrome = Regex("Chrome/(\\d+)").find(real)?.groupValues?.getOrNull(1) ?: "120"
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/$chrome.0.0.0 Safari/537.36"
}

/** 跑脚本给的校验函数，返回非空文本表示不通过 */
internal fun validatePromptInput(
    webView: WebView,
    validatorJs: String,
    input: String,
    onResult: (String?) -> Unit,
) {
    if (validatorJs.isBlank()) {
        onResult(null)
        return
    }
    webView.evaluateJavascript("$validatorJs(${JSONObject.quote(input)})") { raw ->
        val value = runCatching { JSONObject("{\"v\":$raw}").opt("v") }.getOrNull()
        val text = if (value == null || value == JSONObject.NULL) "" else value.toString()
        onResult(if (text.isBlank() || text.equals("false", true)) null else text)
    }
}
