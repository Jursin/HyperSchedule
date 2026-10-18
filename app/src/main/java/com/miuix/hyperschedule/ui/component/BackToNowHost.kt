package com.miuix.hyperschedule.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** 单个 tab 上报给共享「今」按钮的状态；切页时宿主换槽位，按钮就地收起/展开 */
class BackToNowSlot(val visible: State<Boolean>, val onClick: () -> Unit)

class BackToNowHostState {
    internal val slots = mutableStateMapOf<Int, BackToNowSlot>()

    fun slotOf(page: Int): BackToNowSlot? = slots[page]
}

val LocalBackToNowHost = compositionLocalOf<BackToNowHostState?> { null }

/** 当前内容所属的 tab 页码（即 MainScaffold 传入的 page） */
val LocalTabPageIndex = compositionLocalOf { -1 }

/** 把本页的「今」按钮状态挂到跨页共享的宿主上；[visible] 须是 remember 出来的稳定 State */
@Composable
fun ProvideBackToNow(visible: State<Boolean>, onClick: () -> Unit) {
    val host = LocalBackToNowHost.current ?: return
    val page = LocalTabPageIndex.current
    // 点击闭包每次重组都会换实例，包一层避免宿主跟着重组
    val latestOnClick = rememberUpdatedState(onClick)
    val slot = remember(page, visible) { BackToNowSlot(visible) { latestOnClick.value() } }
    DisposableEffect(page, host, slot) {
        host.slots[page] = slot
        onDispose {
            if (host.slots[page] === slot) host.slots.remove(page)
        }
    }
}
