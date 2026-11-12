package com.hyperschedule.app.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.hyperschedule.app.theme.PanelCorner
import com.hyperschedule.app.theme.WidePageHeightFraction
import com.hyperschedule.app.theme.WidePageMaxWidth
import com.hyperschedule.app.ui.component.WidePageWindow
import com.hyperschedule.app.ui.component.WindowDismissState
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 浮窗滑入滑出时长与缓动 */
internal const val WindowSlideMillis = 330

internal val WindowSlideEasing = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }

/** 浮窗页面的进出场，一级从底边滑入，二级及更深从下层浮窗右缘滑入 */
internal fun windowPageTransition(level: State<Int>): NavTransition =
    object : NavTransition {
        // 默认 1f 只留顶层下面一层，叠两层浮窗时最底下的整页会被剔除
        override val opaqueDepth: Float = 8f

        override val motion: NavMotion =
            NavMotion(programmatic = NavSettleSpec.Tween(WindowSlideMillis, WindowSlideEasing))

        override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier =
            // 裁剪与平移放在同一个 draw 内完成
            this.drawWithContent {
                val depth = scope.relativeDepth
                val progress = (-depth).coerceIn(0f, 1f)
                if (progress <= 0f) {
                    drawContent()
                    return@drawWithContent
                }
                val w = size.width
                val h = size.height
                val cardHeight = minOf(w, h) * WidePageHeightFraction
                if (level.value >= 2) {
                    // 二级及更深，位移一个浮窗宽度，裁在浮窗圆角轮廓内
                    val cardWidth = minOf(w, WidePageMaxWidth.toPx())
                    val radius = PanelCorner.toPx()
                    val left = (w - cardWidth) / 2f
                    val top = (h - cardHeight) / 2f
                    val path =
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(left, top, left + cardWidth, top + cardHeight),
                                    cornerRadius = CornerRadius(radius, radius),
                                ),
                            )
                        }
                    // 对齐到整设备像素，圆角边缘不会泛细线
                    val dx = (progress * minOf(w, cardWidth)).roundToInt().toFloat()
                    clipPath(path) {
                        translate(dx, 0f) {
                            this@drawWithContent.drawContent()
                        }
                    }
                } else {
                    // 一级，从屏幕底边滑入
                    val dy = (progress * (h + cardHeight) / 2f).roundToInt().toFloat()
                    translate(0f, dy) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
    }

/** 主页的整屏遮罩，栈里有浮窗页就压暗主页，浮窗整块收起时同步淡出 */
@Composable
internal fun WindowScrim(
    backStack: List<NavKey>,
    dismissState: WindowDismissState,
) {
    val open = backStack.size > 1
    val dismissing = dismissState.active
    val dim = remember { Animatable(0f) }
    LaunchedEffect(open, dismissing) {
        when {
            // 整块收起，遮罩跟卡片同步淡出
            dismissing -> dim.animateTo(0f, tween(durationMillis = WindowSlideMillis, easing = WindowSlideEasing))
            // 打开时快速压暗
            open -> dim.animateTo(1f, tween(durationMillis = 140, easing = WindowSlideEasing))
            // 关闭时等卡片滑出去再淡出
            else -> dim.animateTo(0f, tween(240, delayMillis = 200, easing = WindowSlideEasing))
        }
    }
    val progress = dim.value
    if (progress <= 0f) return
    val dimColor = MiuixTheme.colorScheme.windowDimming
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(dimColor.copy(alpha = dimColor.alpha * progress)),
    )
}

@Composable
internal fun NavEntry(
    interceptPredictiveBack: Boolean,
    onBack: () -> Unit,
    onDismissToHome: () -> Unit = onBack,
    windowed: Boolean,
    content: @Composable () -> Unit,
) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = state,
        isBackEnabled = interceptPredictiveBack,
        onBackCompleted = onBack,
    )
    // 宽屏下整页收成居中浮窗，手机和首页始终铺满，点浮窗外区域即关闭整个浮窗
    if (windowed) WidePageWindow(onDismiss = onDismissToHome) { content() } else content()
}
