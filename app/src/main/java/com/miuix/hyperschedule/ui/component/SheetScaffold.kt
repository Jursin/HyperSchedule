package com.miuix.hyperschedule.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.PanelCorner
import com.miuix.hyperschedule.theme.SheetLargeMaxWidth
import com.miuix.hyperschedule.theme.ShortSheetMaxHeight
import com.miuix.hyperschedule.theme.panelOutlineColor
import com.miuix.hyperschedule.theme.sheetPanelColor
import com.miuix.hyperschedule.theme.useLargeScreenWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 拉到这条分界线以下才收起，没到就弹回去 */
private val DismissDistance = 150.dp

/** 甩得够快时不必非要拉过 [DismissDistance] */
private val DismissVelocity = 800.dp

/** 居中浮层与屏幕边缘的距离 */
private val OutsideMargin = 12.dp

/** 拖动条 */
private val HandleWidth = 60.dp
private val HandleHeight = 3.dp
private val HandleAreaHeight = 21.dp

/** 标题行 */
private val HeaderMinHeight = 46.dp
private val HeaderHorizontalPadding = 20.dp
/** 标题图标，对齐 title3 的字面高度 */
private val TitleIconSize = 22.dp

/** 标题与正文之间的间距 */
private val HeaderToContentSpacing = 18.dp
private val HeaderToContentSpacingNoSubtitle = 10.dp

/** 抽屉内可滚动的内容列：高度上限、下拉回弹、统一内边距 */
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
                .bouncyScroll()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
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
    subtitle: String? = null,
    titleIcon: ImageVector? = null,
    startAction: (@Composable () -> Unit)? = null,
    endAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 居中浮层 / 贴底，判定和对话框一致
        val largeScreen = useLargeScreenWindow()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val showSpec: AnimationSpec<Float> =
            if (largeScreen) {
                folmeSpring(damping = 0.9f, response = 0.3f)
            } else {
                spring(dampingRatio = 0.86f, stiffness = 520f)
            }
        val hideSpec: AnimationSpec<Float> =
            if (largeScreen) {
                tween(durationMillis = 260, easing = DecelerateEasing(1.5f))
            } else {
                spring(dampingRatio = 0.95f, stiffness = 620f)
            }
        LaunchedEffect(show, largeScreen) {
            if (show) {
                opened = true
                dragY = 0f
                progress.animateTo(targetValue = 1f, animationSpec = showSpec)
            } else {
                progress.animateTo(targetValue = 0f, animationSpec = hideSpec)
            }
        }

        val shape =
            if (largeScreen) {
                RoundedCornerShape(PanelCorner)
            } else {
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            }
        // 遮罩跟着下拉一起变淡，强度取 windowDimming
        val dimming = MiuixTheme.colorScheme.windowDimming
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val remain = 1f - (dragY / dismissDistance).coerceIn(0f, 1f)
                        alpha = progress.value * remain
                    }
                    .background(dimming)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(if (largeScreen) Alignment.Center else Alignment.BottomCenter)
                    .then(
                        // 宽屏固定宽度居中，窄屏贴底铺满
                        if (largeScreen) {
                            Modifier
                                .padding(horizontal = OutsideMargin)
                                .widthIn(max = SheetLargeMaxWidth)
                                .heightIn(max = maxHeight * 2f / 3f)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    )
                    .graphicsLayer {
                        translationY = if (largeScreen) {
                            // 居中层同样从屏幕底边滑入滑出
                            (1f - progress.value) * (screenHeightPx + size.height) / 2f + dragY
                        } else {
                            (1f - progress.value) * size.height + dragY
                        }
                    }
                    .clip(shape)
                    .background(panelColor)
                    // 顶边一道极淡的高光，避免深色下和背景黏在一起
                    .border(width = 1.dp, color = panelOutlineColor(), shape = shape),
        ) {
            // 只有顶部可拖，内容区列表要能自己滚
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                val passed =
                                    dragY > dismissDistance && velocity > -dismissVelocity
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 拖动条：贴底抽屉和居中浮层都画，两种形态都有「往下拉收起」的手势
                    Box(
                        modifier = Modifier.fillMaxWidth().height(HandleAreaHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = HandleWidth, height = HandleHeight)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.3f),
                                    ),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = HeaderMinHeight)
                                .padding(horizontal = HeaderHorizontalPadding),
                    ) {
                        startAction?.let { action ->
                            Box(modifier = Modifier.align(Alignment.CenterStart)) { action() }
                        }
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                if (titleIcon != null) {
                                    Icon(
                                        imageVector = titleIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(TitleIconSize),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = title,
                                    style = MiuixTheme.textStyles.title3,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        endAction?.let { action ->
                            Box(modifier = Modifier.align(Alignment.CenterEnd)) { action() }
                        }
                    }
                }
            }
            Spacer(
                modifier =
                    Modifier.height(
                        if (subtitle == null) HeaderToContentSpacingNoSubtitle else HeaderToContentSpacing,
                    ),
            )
            content()
        }
    }
}
