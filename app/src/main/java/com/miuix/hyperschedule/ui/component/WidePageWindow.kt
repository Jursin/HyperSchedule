package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.WidePageContentInset
import com.miuix.hyperschedule.theme.panelOutlineColor
import com.miuix.hyperschedule.theme.PanelCorner
import com.miuix.hyperschedule.theme.WidePageMaxWidth
import com.miuix.hyperschedule.theme.pageWindowColor
import com.miuix.hyperschedule.theme.widePageHeight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 浮窗整块收起的状态，全局一份 */
class WindowDismissState {
    /** 收起代数：前进说明该浮窗已被收起 */
    var generation by mutableIntStateOf(0)

    /** 收起进度 0..1 */
    var progress by mutableFloatStateOf(0f)

    /** 是否正在收起 */
    var active by mutableStateOf(false)
}

/** 浮窗投影的模糊半径 */
private val WindowShadowRadius = 24.dp

private val DefaultDismissState = WindowDismissState()

/** 由 MainScaffold 提供 */
val LocalWindowDismissState = staticCompositionLocalOf { DefaultDismissState }

/** 把整页收成屏幕中央的浮窗，由调用方判断是否宽屏；遮罩在主页那层画 */
@Composable
fun WidePageWindow(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val shape = RoundedCornerShape(PanelCorner)
        val panel = pageWindowColor()
        val cardHeight = widePageHeight(maxWidth, maxHeight)
        val dismissState = LocalWindowDismissState.current
        // 诞生时的收起代数，代数变了说明自己已被收起
        val bornGeneration = remember { dismissState.generation }
        // 卡片完全滑出屏幕的距离，必须滑满全程，否则会和之后 Miuix 的退栈动画接成两段
        val dismissTravel =
            with(LocalDensity.current) {
                ((maxHeight + cardHeight) / 2 + WindowShadowRadius).coerceAtLeast(0.dp).toPx()
            }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 点浮窗外的区域直接关闭
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        // widthIn 要在 fillMaxWidth 外面，反过来最小宽度会被顶到全屏
                        .widthIn(max = WidePageMaxWidth)
                        .fillMaxWidth()
                        .height(cardHeight)
                        // 所有浮窗读同一个收起进度一起下滑
                        .graphicsLayer {
                            val progress =
                                when {
                                    dismissState.generation > bornGeneration -> 1f
                                    dismissState.active -> dismissState.progress
                                    else -> 0f
                                }
                            translationY = progress * dismissTravel
                        }
                        .dropShadow(
                            shape = shape,
                            shadow = Shadow(radius = WindowShadowRadius, color = Color.Black, alpha = 0.2f),
                        )
                        .clip(shape)
                        // 卡片自己铺一层面板色，首帧内容透明时不会露出投影
                        .background(panel)
                        .border(1.dp, panelOutlineColor(), shape)
                        // 卡片自己吃掉点击，否则会落到外层的关闭手势上
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
            ) {
                // 浮窗不在系统栏底下，吃掉 inset 顶栏才不多出一截
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
                ) {
                    CompositionLocalProvider(LocalInsideFloatingWindow provides true) {
                        WindowContent(panel, content)
                    }
                }
            }
        }
    }
}

/** 浮窗内容：把页面边距换成窗口内边距，面板色与 surface 不同时换一层表面色 */
@Composable
private fun WindowContent(
    panel: Color,
    content: @Composable () -> Unit,
) {
    val windowed: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalPageHorizontalMargin provides WidePageContentInset,
            LocalPageTopPadding provides 0.dp,
        ) { content() }
    }
    if (panel == MiuixTheme.colorScheme.surface) {
        windowed()
    } else {
        MiuixTheme(colors = MiuixTheme.colorScheme.copy(surface = panel)) { windowed() }
    }
}
