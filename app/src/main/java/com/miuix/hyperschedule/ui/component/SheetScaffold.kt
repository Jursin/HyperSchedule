package com.miuix.hyperschedule.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.theme.ShortSheetMaxHeight
import com.miuix.hyperschedule.theme.sheetPanelColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 拉到这条分界线以下才收起，没到就弹回去 */
private val DismissDistance = 150.dp

/** 甩得够快时不必非要拉过 [DismissDistance] */
private val DismissVelocity = 800.dp

/** 遮罩颜色 */
private val ScrimColor = Color.Black.copy(alpha = 0.32f)

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
                .overScrollVertical()
                .scrollEndHaptic()
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
    LaunchedEffect(show) {
        if (show) {
            dragY = 0f
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 520f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 620f))
            currentOnDismissFinished()
        }
    }
    // 完全收起后整棵子树退出，只在收起/展开翻转时失效
    val dismissed = remember { derivedStateOf { progress.value <= 0f } }
    if (dismissed.value && !show) return

    BackHandler(enabled = show) { onDismiss() }

    val dragState =
        rememberDraggableState { delta ->
            // 新的拖动打断上一次的弹回动画
            settleJob?.cancel()
            dragY = (dragY + delta).coerceAtLeast(0f)
        }

    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val panelColor = sheetPanelColor()
    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩，跟着下拉一起变淡
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val remain = 1f - (dragY / dismissDistance).coerceIn(0f, 1f)
                        alpha = progress.value * remain
                    }
                    .background(ScrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { translationY = (1f - progress.value) * size.height + dragY }
                    .clip(shape)
                    .background(panelColor)
                    // 顶边一道极淡的高光，避免深色下和背景黏在一起
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = shape,
                    ),
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
                    // 拖动条
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
