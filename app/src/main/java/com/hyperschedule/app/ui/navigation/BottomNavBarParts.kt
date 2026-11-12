package com.hyperschedule.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.hyperschedule.app.ui.effect.NavIndicatorAnimation
import com.hyperschedule.app.ui.effect.navBarBlur
import com.hyperschedule.app.ui.effect.wallpaperVisible
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop

/** 分页 tab 的胶囊，指示器与拖动手势都只在这一块里 */
@Composable
internal fun TabsPill(
    initialPage: Int,
    targetPage: State<Int>,
    blurBackdrop: Backdrop?,
    colors: NavBarPalette,
    labelTextStyle: TextStyle,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = tabs.size

    var pressedIndex by remember { mutableIntStateOf(-1) }
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val lift by
        animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = tween(80),
            label = "navBarLift",
        )

    BoxWithConstraints(
        modifier =
            modifier
                .height(NavBarMetrics.containerHeight)
                .navBarSurface(RoundedCornerShape(NavBarMetrics.containerRadius), blurBackdrop, colors, lift),
    ) {
        val barWidthPx = constraints.maxWidth.toFloat()
        val containerPadPx = with(density) { NavBarMetrics.containerPadding.toPx() }
        val overhangPx = with(density) { NavBarMetrics.indicatorOverhang.toPx() }
        val slotWidthPx = (barWidthPx - containerPadPx * 2f) / tabsCount

        var currentIndex by remember { mutableIntStateOf(initialPage) }
        val indicator =
            remember(animationScope, slotWidthPx, barWidthPx) {
                NavIndicatorAnimation(
                    scope = animationScope,
                    initialIndex = initialPage,
                    slotWidth = slotWidthPx,
                    innerWidth = barWidthPx - containerPadPx * 2f,
                    overhang = overhangPx,
                )
            }

        // 用 collect 而非 collectLatest，避免连续点击打断指示器动画
        LaunchedEffect(indicator, targetPage) {
            snapshotFlow { targetPage.value }.collect {
                if (it != currentIndex) {
                    currentIndex = it
                    indicator.select(it)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = NavBarMetrics.containerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                NavBarTab(index = index, onClick = { onSelect(index) }) {
                    val itemColor = if (index == pressedIndex) colors.iconPressed else colors.icon
                    Icon(
                        modifier = Modifier.size(NavBarMetrics.iconSize),
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = itemColor,
                    )
                    Text(
                        text = tab.label,
                        color = itemColor,
                        style = labelTextStyle,
                    )
                }
            }
        }

        Indicator(
            animation = indicator,
            pressed = pressed,
            isLtr = isLtr,
            containerPadPx = containerPadPx,
            indicatorWidthPx = slotWidthPx + overhangPx * 2f,
            barWidthPx = barWidthPx,
            colors = colors,
            density = density,
        )

        // 手势层，按在栏上任意位置即可拖动，越过项中心即换选中项，松手吸附最近项
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(barWidthPx, slotWidthPx, isLtr) {
                    val touchSlop = viewConfiguration.touchSlop
                    // 镜像到逻辑 LTR 序，再减去容器内边距进入条目槽空间
                    fun logicalX(x: Float): Float =
                        (if (isLtr) x else size.width - x) - containerPadPx

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = logicalX(down.position.x)
                        val downIndex = indicator.nearestTo(downX)
                        val onCurrent = downIndex == currentIndex

                        // 按下即选中
                        pressed = true
                        pressedIndex = downIndex
                        if (!onCurrent) {
                            currentIndex = downIndex
                            indicator.select(downIndex)
                            onSelect(downIndex)
                        }

                        var isDragging = false
                        var lastFrameX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                pressed = false
                                pressedIndex = -1
                                dragging = false
                                val target = indicator.nearestTo(lastFrameX)
                                if (target != currentIndex) {
                                    currentIndex = target
                                    onSelect(target)
                                } else if (onCurrent && !isDragging) {
                                    onSelect(target)
                                }
                                // 与当前项相同时也调用一次，把拖动中的拉伸收回原位
                                indicator.select(target)
                                break
                            }

                            val x = logicalX(change.position.x)
                            if (!isDragging && abs(x - downX) > touchSlop) {
                                isDragging = true
                                dragging = true
                                indicator.startDrag(x)
                                lastFrameX = x
                            }
                            if (isDragging) {
                                val nearest = indicator.dragTo(x, lastFrameX)
                                if (nearest != pressedIndex) pressedIndex = nearest
                                lastFrameX = x
                            }
                            change.consume()
                        }
                    }
                },
        )
    }
}

/** 设置单独成圆，只放图标，点击进入设置页 */
@Composable
internal fun SettingsButton(
    colors: NavBarPalette,
    blurBackdrop: Backdrop?,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val pressProgress by
        animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = PressSpec,
            label = "settingsPress",
        )

    Box(
        modifier =
            Modifier
                .size(NavBarMetrics.containerHeight)
                .navBarSurface(CircleShape, blurBackdrop, colors, lift = 0f)
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = settingsTab.label,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .graphicsLayer { alpha = pressProgress }
                .background(colors.selectorPressed),
        )
        Icon(
            modifier = Modifier.size(NavBarMetrics.iconSize),
            imageVector = settingsTab.icon,
            contentDescription = null,
            tint =
                lerp(
                    colors.icon,
                    colors.iconPressed,
                    pressProgress,
                ),
        )
    }
}

/** 底栏容器共用的投影、模糊与底色 */
@Composable
private fun Modifier.navBarSurface(
    shape: Shape,
    blurBackdrop: Backdrop?,
    colors: NavBarPalette,
    lift: Float,
): Modifier =
    this
        .dropShadow(
            shape = shape,
            shadow =
                Shadow(
                    radius = NavBarMetrics.barShadowRadius + NavBarMetrics.liftShadowDelta * lift,
                    color = Color.Black,
                    alpha = colors.barShadowAlpha * (1f + NavBarMetrics.LIFT_SHADOW_ALPHA * lift),
                ),
        )
        .then(
            if (blurBackdrop != null) {
                Modifier.navBarBlur(
                    backdrop = blurBackdrop,
                    shape = shape,
                    // 有壁纸时采样层已带蒙层
                    blendColor = if (wallpaperVisible()) colors.blurTint else colors.contentTint,
                )
            } else {
                Modifier.background(colors.container, shape)
            },
        )

/** 选中指示器，左右边界各自动弹，宽度随两条边变化 */
@Composable
private fun Indicator(
    animation: NavIndicatorAnimation,
    pressed: Boolean,
    isLtr: Boolean,
    containerPadPx: Float,
    indicatorWidthPx: Float,
    barWidthPx: Float,
    colors: NavBarPalette,
    density: Density,
) {
    val overlayHeightPx = with(density) { NavBarMetrics.overlayHeight.toPx() }
    val pressInsetPx = with(density) { NavBarMetrics.pressInset.toPx() }

    // 按下，整体缩 6dp + 填充加深
    val pressProgress by
        animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = PressSpec,
            label = "indicatorPress",
        )

    Box(
        Modifier
            // 位置与宽度在 layout 阶段读取，避免拖动每帧触发重组
            .layout { measurable, constraints ->
                val left = animation.leftPx + containerPadPx
                val right = animation.rightPx + containerPadPx
                val width = (right - left).roundToInt().coerceAtLeast(1)
                val placeable =
                    measurable.measure(
                        Constraints.fixed(width, overlayHeightPx.toInt().coerceAtMost(constraints.maxHeight)),
                    )
                val x = (if (isLtr) left else barWidthPx - right).roundToInt()
                val y = ((constraints.maxHeight - placeable.height) / 2f).roundToInt()
                layout(placeable.width, placeable.height) { placeable.place(x, y) }
            }
            .graphicsLayer {
                scaleX = 1f - pressInsetPx / indicatorWidthPx * pressProgress
                scaleY = 1f - pressInsetPx / overlayHeightPx * pressProgress
            }
            .background(
                color = lerp(colors.selector, colors.selectorPressed, pressProgress),
                shape = RoundedCornerShape(NavBarMetrics.overlayRadius),
            ),
    )
}

private val PressSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 438f)
