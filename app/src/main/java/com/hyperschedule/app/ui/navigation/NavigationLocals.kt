package com.hyperschedule.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Scaffold

/** 跨页浮层宿主，抽屉这类内容挂在底栏之后绘制 */
class OverlayHostState {
    internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var owner: Any? = null

    internal fun show(key: Any, value: @Composable () -> Unit) {
        owner = key
        content = value
    }

    /** 只撤自己挂的内容，别的页面退出不能把这一页的抽屉一起清掉 */
    internal fun hide(key: Any) {
        if (owner !== key) return
        owner = null
        content = null
    }
}

val LocalOverlayHost = compositionLocalOf<OverlayHostState?> { null }

/** 把内容挂到 [LocalOverlayHost]，[content] 为 null 时撤下 */
@Composable
fun ProvideOverlay(content: (@Composable () -> Unit)?) {
    val host = LocalOverlayHost.current ?: return
    val latest = rememberUpdatedState(content)
    val owner = remember { Any() }
    DisposableEffect(host, content != null) {
        if (content != null) host.show(owner) { latest.value?.invoke() } else host.hide(owner)
        onDispose { host.hide(owner) }
    }
}

/** 底部抽屉的外壳靠根 Scaffold 的浮层宿主渲染，这里只提供宿主，配色与内边距留给下层 */
@Composable
fun RootOverlayHost(content: @Composable () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = { content() },
    )
}

/** 底部导航切换动画中，页面据此临时关掉日期/周 pager 的手势 */
val LocalMainTabNavigating = compositionLocalOf { false }

/** 当前页是否为用户正在看的 Tab，二级页盖住不算离开 */
val LocalPageIsCurrent = compositionLocalOf { true }

/** 停在本页时再点一次本 Tab 的次数，页面据此做复位 */
val LocalPageReselect = compositionLocalOf { 0 }
