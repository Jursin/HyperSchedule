package com.hyperschedule.app.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.theme.PanelCorner
import com.hyperschedule.app.theme.SectionSpacing
import com.hyperschedule.app.theme.SheetLargeMaxWidth
import com.hyperschedule.app.theme.ShortSheetMaxHeight
import com.hyperschedule.app.theme.panelOutlineColor
import com.hyperschedule.app.theme.sheetPanelColor
import com.hyperschedule.app.theme.useLargeScreenWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 拉到这条分界线以下才收起，没到就弹回去 */
private val DismissDistance = 150.dp

/** 甩得够快时不必非要拉过 [DismissDistance] */
private val DismissVelocity = 800.dp

/** 居中浮层距屏幕左右边的最小留白 */
private val OutsideMargin = 12.dp

private val HandleWidth = 45.dp
private val HandleHeight = 4.dp
private val HandleAreaHeight = 21.dp

/** 标题行与面板边缘的水平留白 */
private val HeaderHorizontalPadding = 16.dp

/** 抽屉内容的水平留白 */
private val SheetContentInset = 16.dp

/** 抽屉内可滚动的内容列，最大高度与统一内边距 */
@Composable
fun SheetScrollColumn(
    maxHeight: Dp = ShortSheetMaxHeight,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SheetContentInset),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        content = content,
    )
}

/** 底部抽屉外壳 */
@Composable
fun SheetScaffold(
    title: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    startAction: (@Composable () -> Unit)? = null,
    endAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (useLargeScreenWindow()) {
        FloatingSheet(title, show, onDismiss, onDismissFinished, startAction, endAction, content)
    } else {
        // 面板贴到屏幕底边，内容自己让开手势条
        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        OverlayBottomSheet(
            show = show,
            title = title,
            startAction = startAction,
            endAction = endAction,
            backgroundColor = sheetPanelColor(),
            cornerRadius = PanelCorner,
            // 左右留白由各内容自己给
            insideMargin = DpSize(0.dp, navBarInset + SheetContentInset),
            onDismissRequest = onDismiss,
            onDismissFinished = onDismissFinished,
            content = content,
        )
    }
}

/** 平板上的居中浮层 */
@Composable
private fun FloatingSheet(
    title: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    startAction: (@Composable () -> Unit)?,
    endAction: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    // 下拉位移，不参与进出场动画
    var dragY by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDistance = remember(density) { with(density) { DismissDistance.toPx() } }
    val dismissVelocity = remember(density) { with(density) { DismissVelocity.toPx() } }
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    // 展开过才算收起完成
    var opened by remember { mutableStateOf(false) }
    val dismissed = remember { derivedStateOf { progress.value <= 0f } }
    if (dismissed.value && !show) {
        // 通知外层卸载，放进动画协程会被这里的提前返回取消
        LaunchedEffect(Unit) { if (opened) currentOnDismissFinished() }
        return
    }

    BackHandler(enabled = show) { onDismiss() }

    val dragState =
        rememberDraggableState { delta ->
            // 新的拖动打断上一次的弹回动画
            settleJob?.cancel()
            dragY = (dragY + delta).coerceAtLeast(0f)
        }

    val panelColor = sheetPanelColor()
    val shape = remember { RoundedCornerShape(PanelCorner) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        LaunchedEffect(show) {
            if (show) {
                opened = true
                dragY = 0f
                progress.animateTo(targetValue = 1f, animationSpec = folmeSpring(damping = 0.9f, response = 0.3f))
            } else {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 260, easing = DecelerateEasing(1.5f)),
                )
            }
        }

        // 遮罩跟着下拉一起变淡
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val remain = 1f - (dragY / dismissDistance).coerceIn(0f, 1f)
                        alpha = progress.value * remain
                    }
                    .background(MiuixTheme.colorScheme.windowDimming)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    // 先留白再限宽
                    .padding(horizontal = OutsideMargin)
                    .widthIn(max = SheetLargeMaxWidth)
                    .heightIn(max = maxHeight * 2f / 3f)
                    .graphicsLayer {
                        // 居中浮层同样从屏幕底边滑入滑出
                        translationY = (1f - progress.value) * (screenHeightPx + size.height) / 2f + dragY
                    }
                    .clip(shape)
                    .background(panelColor)
                    // 顶边一道极淡的高光，避免深色下和背景黏在一起
                    .border(width = 1.dp, color = panelOutlineColor(), shape = shape),
        ) {
            // 只有头部可拖，内容区的列表要能自己滚
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                val passed = dragY > dismissDistance && velocity > -dismissVelocity
                                if (passed || velocity > dismissVelocity) {
                                    onDismiss()
                                } else {
                                    // 没到分界线就带着速度弹回
                                    settleJob =
                                        scope.launch {
                                            animate(
                                                initialValue = dragY,
                                                targetValue = 0f,
                                                initialVelocity = velocity.coerceAtLeast(0f),
                                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                                            ) { value, _ ->
                                                dragY = value
                                            }
                                        }
                                }
                            },
                        ),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(HandleAreaHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = HandleWidth, height = HandleHeight)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.3f)),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 12.dp, start = HeaderHorizontalPadding, end = HeaderHorizontalPadding),
                ) {
                    startAction?.let { action ->
                        Box(modifier = Modifier.align(Alignment.CenterStart)) { action() }
                    }
                    Text(
                        text = title,
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    endAction?.let { action ->
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) { action() }
                    }
                }
            }
            // 内容底部留白，最后一张卡片不贴圆角
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = SheetContentInset)) {
                content()
            }
        }
    }
}
