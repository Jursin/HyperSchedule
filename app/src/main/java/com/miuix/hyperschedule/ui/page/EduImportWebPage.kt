package com.miuix.hyperschedule.ui.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.miuix.hyperschedule.data.importer.edu.EduScriptRepository
import com.miuix.hyperschedule.data.importer.edu.SchoolRepository
import com.miuix.hyperschedule.data.repository.ScheduleRepository
import com.miuix.hyperschedule.data.repository.TableSummary
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.ConfirmDialog
import com.miuix.hyperschedule.ui.component.CreateTableDialog
import com.miuix.hyperschedule.ui.component.Devices
import com.miuix.hyperschedule.ui.component.EduAlertState
import com.miuix.hyperschedule.ui.component.EduPromptDialog
import com.miuix.hyperschedule.ui.component.EduPromptState
import com.miuix.hyperschedule.ui.component.EduPostBodyRegistry
import com.miuix.hyperschedule.ui.component.EduPostBridge
import com.miuix.hyperschedule.ui.component.EduPostInterceptScript
import com.miuix.hyperschedule.ui.component.EduPromiseBridgeScript
import com.miuix.hyperschedule.ui.component.EduSelectionDialog
import com.miuix.hyperschedule.ui.component.EduSelectionState
import com.miuix.hyperschedule.ui.component.EduSslState
import com.miuix.hyperschedule.ui.component.ImportTablePickerDialog
import com.miuix.hyperschedule.ui.component.EduWebBridge
import com.miuix.hyperschedule.ui.component.interceptPostRequest
import com.miuix.hyperschedule.ui.component.toast
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 教务系统网页导入，右下角切换电脑模式与导入 */
@SuppressLint("MissingOnRenderProcessGone", "WebViewClientOnReceivedSslError")
@Composable
fun EduImportWebPage(
    schoolRepository: SchoolRepository,
    scriptRepository: EduScriptRepository,
    repository: ScheduleRepository,
    schoolId: String,
    adapterId: String,
    tables: List<TableSummary>,
    currentTableId: String?,
    appBlur: Boolean = false,
    onBack: () -> Unit,
    onImported: () -> Unit = {},
    onSwitchTable: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val school = remember(schoolId) { schoolRepository.schools().firstOrNull { it.id == schoolId } }
    val adapter = remember(school, adapterId) { school?.adapters?.firstOrNull { it.id == adapterId } }
    if (school == null || adapter == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val scrollBehavior = MiuixScrollBehavior()
    val webView = remember { createWebView(context) }

    var pageTitle by remember { mutableStateOf(school.name) }
    var progress by remember { mutableIntStateOf(0) }
    var pageReady by remember { mutableStateOf(false) }
    var rendererGone by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    val currentDesktopMode by rememberUpdatedState(desktopMode)
    var alert by remember { mutableStateOf<EduAlertState?>(null) }
    var prompt by remember { mutableStateOf<EduPromptState?>(null) }
    var selection by remember { mutableStateOf<EduSelectionState?>(null) }
    var sslError by remember { mutableStateOf<EduSslState?>(null) }

    val currentOnImported by rememberUpdatedState(onImported)
    val currentOnSwitchTable by rememberUpdatedState(onSwitchTable)
    var pickerVisible by remember { mutableStateOf(false) }
    var selectedTableId by remember { mutableStateOf<String?>(null) }
    var newTableDialog by remember { mutableStateOf(false) }
    // 页面退出后脚本可能还会回调完成，标记后忽略
    val disposed = remember { mutableStateOf(false) }

    val bridge: EduWebBridge = remember(webView) {
        EduWebBridge(
            context = context,
            webView = webView,
            scope = scope,
            repository = repository,
            onAlert = { title, content, confirmText, result ->
                alert = EduAlertState(title, content, confirmText, result)
            },
            onPrompt = { title, tip, defaultText, validatorJs, result ->
                prompt = EduPromptState(title, tip, defaultText, validatorJs, result)
            },
            onSelection = { title, items, defaultIndex, result ->
                selection = EduSelectionState(title, items, defaultIndex, result)
            },
            onCompleted = { tableId ->
                if (!disposed.value) {
                    currentOnSwitchTable(tableId)
                    context.toast("导入完成")
                    currentOnImported()
                }
            },
        )
    }

    DisposableEffect(webView) {
        onDispose {
            disposed.value = true
            if (!rendererGone) {
                webView.stopLoading()
                webView.webChromeClient = null
                webView.webViewClient = WebViewClient()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.clearHistory()
                webView.clearCache(true)
                webView.clearFormData()
                webView.clearSslPreferences()
                webView.destroy()
            }
            // 登录态留在 Cookie 里，同一学校下次进来不用重新登录
            CookieManager.getInstance().flush()
            EduPostBodyRegistry.clear()
        }
    }

    LaunchedEffect(webView) {
        webView.attachBridges(bridge)
        // 能在文档开始前注入就尽早接管请求，否则退到 onPageFinished
        val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        // lint 只认紧贴调用的特性检查
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, EduPostInterceptScript, setOf("*"))
        }
        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank() && !title.startsWith("http")) pageTitle = title
                }
            }
        webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                    openExternal(context, request.url)

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                    url?.let { openExternal(context, it.toUri()) } ?: false

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? =
                    interceptPostRequest(request, currentDesktopMode, CookieManager.getInstance())

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    // 空白页不会走完整加载流程，保持已就绪
                    if (url != "about:blank") pageReady = false
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    sslError =
                        EduSslState(error.url.orEmpty()) { proceed ->
                            sslError = null
                            if (proceed) handler.proceed() else handler.cancel()
                        }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!documentStartSupported) view?.evaluateJavascript(EduPostInterceptScript, null)
                    view?.evaluateJavascript(EduPromiseBridgeScript, null)
                    pageReady = true
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    // 渲染进程崩溃后 WebView 不能复用，销毁并换成提示页，否则整个应用会被杀掉
                    view?.let {
                        (it.parent as? ViewGroup)?.removeView(it)
                        it.destroy()
                    }
                    rendererGone = true
                    return true
                }
            }
        applyDesktopMode(webView, desktopMode)
        val target = adapter.importUrl?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeUrl)
        if (target != null) {
            webView.loadUrl(target)
        } else {
            // 通用工具里的测试适配器没有网页，开空白页只跑桥接演示
            pageTitle = adapter.name
            webView.loadUrl("about:blank")
            pageReady = true
            webView.post {
                webView.evaluateJavascript(EduPostInterceptScript, null)
                webView.evaluateJavascript(EduPromiseBridgeScript, null)
            }
        }
    }

    fun goBack() {
        if (!rendererGone && webView.canGoBack()) webView.goBack() else onBack()
    }

    BackHandler { goBack() }

    fun toggleDesktopMode() {
        desktopMode = !desktopMode
        applyDesktopMode(webView, desktopMode)
        // 重新 GET 当前地址，避免 reload 把上一次的 POST 再发一遍
        val current = webView.url
        if (!current.isNullOrBlank() && current != "about:blank") webView.loadUrl(current) else webView.reload()
    }

    // 脚本名缺省时用适配器 id 兜底
    val scriptName = adapter.scriptPath.ifBlank { "${adapter.id}.js" }

    /** 点导入先选课表，选完才下载并执行脚本 */
    fun askTargetTable() {
        if (!pageReady) {
            context.toast("页面还在加载，请稍候再试")
            return
        }
        selectedTableId = currentTableId ?: tables.firstOrNull()?.table?.id
        pickerVisible = true
    }

    fun runImportScript(tableId: String) {
        bridge.resetImportTarget()
        bridge.setImportTable(tableId)
        scope.launch {
            val js = scriptRepository.script(school.resourceFolder, scriptName)
            if (js == null) {
                context.toast("导入脚本不存在或下载失败")
                return@launch
            }
            // 脚本契约里通过 window.currentTableId 拿目标课表
            webView.evaluateJavascript("window.currentTableId = ${JSONObject.quote(tableId)};\n$js", null)
            context.toast("正在执行导入脚本")
        }
    }

    Scaffold(
        topBar = {
            BlurredTopAppBar(
                title = pageTitle,
                blurBackdrop = null,
                scrollBehavior = scrollBehavior,
                onBack = ::goBack,
                actions = {
                    IconButton(onClick = { if (!rendererGone) webView.reload() }) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = "刷新")
                    }
                },
                // 网页页用紧凑顶栏，把高度让给网页
                pinnedTitle = true,
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (rendererGone) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "网页渲染进程已退出，请返回重试",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

                if (progress in 1..99) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(progress / 100f)
                                .height(2.dp)
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 28.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) {
                    FloatingActionButton(
                        onClick = ::toggleDesktopMode,
                        containerColor = MiuixTheme.colorScheme.secondaryVariant,
                    ) {
                        Icon(
                            imageVector = Devices,
                            contentDescription = if (desktopMode) "切换到手机模式" else "切换到电脑模式",
                            modifier = Modifier.size(22.dp),
                            tint = MiuixTheme.colorScheme.onSecondaryVariant,
                        )
                    }
                    FloatingActionButton(
                        onClick = ::askTargetTable,
                        containerColor =
                            if (pageReady) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.disabledPrimaryButton
                            },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Download,
                            contentDescription = "执行导入脚本",
                            modifier = Modifier.size(24.dp),
                            tint =
                                if (pageReady) {
                                    MiuixTheme.colorScheme.onPrimary
                                } else {
                                    MiuixTheme.colorScheme.disabledOnPrimaryButton
                                },
                        )
                    }
                }
            }
        }
    }

    if (pickerVisible) {
        ImportTablePickerDialog(
            tables = tables,
            selectedTableId = selectedTableId,
            appBlur = appBlur,
            onSelect = { selectedTableId = it },
            onDismiss = { pickerVisible = false },
            onConfirm = {
                val tableId = selectedTableId
                pickerVisible = false
                if (tableId != null) runImportScript(tableId)
            },
            onAddTable = {
                pickerVisible = false
                newTableDialog = true
            },
        )
    }

    if (newTableDialog) {
        CreateTableDialog(
            repository = repository,
            appBlur = appBlur,
            onDismiss = {
                newTableDialog = false
                pickerVisible = true
            },
            onCreated = { tableId ->
                newTableDialog = false
                runImportScript(tableId)
            },
            onFailed = {
                newTableDialog = false
                pickerVisible = true
            },
        )
    }

    alert?.let { data ->
        ConfirmDialog(
            title = data.title.ifBlank { "提示" },
            appBlur = appBlur,
            message = data.content,
            confirmText = data.confirmText.ifBlank { "确定" },
            cancelText = "取消",
            onDismiss = {
                alert = null
                data.onResult(false)
            },
            onConfirm = {
                alert = null
                data.onResult(true)
            },
        )
    }

    prompt?.let { data ->
        EduPromptDialog(
            title = data.title.ifBlank { "请输入" },
            tip = data.tip,
            initial = data.defaultText,
            appBlur = appBlur,
            validate = { input, done -> validatePromptInput(webView, data.validatorJs, input, done) },
            onDismiss = {
                prompt = null
                data.onResult(null)
            },
            onConfirm = { input ->
                prompt = null
                data.onResult(input)
            },
        )
    }

    selection?.let { data ->
        EduSelectionDialog(
            title = data.title.ifBlank { "请选择" },
            items = data.items,
            defaultIndex = data.defaultIndex,
            appBlur = appBlur,
            onDismiss = {
                selection = null
                data.onResult(null)
            },
            onConfirm = { index ->
                selection = null
                data.onResult(index)
            },
        )
    }

    sslError?.let { data ->
        ConfirmDialog(
            title = "证书不受信任",
            appBlur = appBlur,
            message = "${data.failingUrl}\n\n该站点的证书校验未通过，继续访问可能不安全。",
            confirmText = "继续访问",
            cancelText = "取消",
            onDismiss = { data.onResult(false) },
            onConfirm = { data.onResult(true) },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
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

private fun WebView.attachBridges(bridge: EduWebBridge) {
    addJavascriptInterface(bridge, "AndroidBridge")
    addJavascriptInterface(EduPostBridge(), "WebPostService")
}

private fun normalizeUrl(url: String): String =
    if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"

private fun openExternal(context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "javascript") return false
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    return true
}

/** 电脑模式用桌面 UA 并关闭宽视口，手机模式恢复默认 */
private fun applyDesktopMode(webView: WebView, desktop: Boolean) {
    val context = webView.context
    webView.settings.apply {
        userAgentString = if (desktop) desktopUserAgent(context) else WebSettings.getDefaultUserAgent(context)
        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        useWideViewPort = !desktop
        loadWithOverviewMode = !desktop
    }
    webView.setInitialScale(if (desktop) 100 else 0)
}

private fun desktopUserAgent(context: Context): String {
    val real = WebSettings.getDefaultUserAgent(context)
    val chrome = Regex("Chrome/(\\d+)").find(real)?.groupValues?.getOrNull(1) ?: "120"
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/$chrome.0.0.0 Safari/537.36"
}

/** 跑脚本给的校验函数，返回非空文本表示不通过 */
private fun validatePromptInput(
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
