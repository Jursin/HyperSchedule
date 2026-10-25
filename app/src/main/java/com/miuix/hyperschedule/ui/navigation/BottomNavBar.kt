package com.miuix.hyperschedule.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.isDarkTheme
import com.miuix.hyperschedule.ui.effect.NavIndicatorAnimation
import com.miuix.hyperschedule.ui.effect.navBarBlur
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Years

internal data class Tab(val label: String, val icon: ImageVector)

internal val tabs =
    listOf(
        Tab("今日课表", MiuixIcons.Months),
        Tab("课程表", MiuixIcons.Years),
        Tab("设置", MiuixIcons.Settings),
    )

/** 单个 tab：等分宽度、满高；纯视觉，手势统一由外层处理 */
@Composable
internal fun RowScope.NavBarTab(
    index: Int,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .semantics {
                    role = Role.Tab
                    onClick(label = tabs[index].label) {
                        onClick()
                        true
                    }
                }
                .fillMaxHeight()
                .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** 底部导航栏 */
@Composable
internal fun BottomNavBar(
    initialPage: Int,
    targetPage: State<Int>,
    blurBackdrop: LayerBackdrop?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = tabs.size

    val isDark = isDarkTheme()
    val colors = if (isDark) NavBarColors.Dark else NavBarColors.Light

    val labelTextStyle =
        remember(density) {
            TextStyle(fontSize = with(density) { NavBarMetrics.labelFontSize.toSp() })
        }

    BoxWithConstraints(modifier.height(NavBarMetrics.containerHeight)) {
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

        // 用 collect 而非 collectLatest：后者会在新值到来时取消上一次收集，
        // 连续点击时指示器动画被反复打断，观感发涩
        LaunchedEffect(indicator, targetPage) {
            snapshotFlow { targetPage.value }.collect {
                if (it != currentIndex) {
                    currentIndex = it
                    indicator.select(it)
                }
            }
        }

        var pressedIndex by remember { mutableIntStateOf(-1) }
        var pressed by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        val lift by
            animateFloatAsState(
                targetValue = if (dragging) 1f else 0f,
                animationSpec = tween(80),
                label = "navBarLift",
            )

        Row(
            Modifier
                .dropShadow(
                    shape = RoundedCornerShape(NavBarMetrics.containerRadius),
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
                            shape = RoundedCornerShape(NavBarMetrics.containerRadius),
                            blurRadius = NavBarMetrics.BAR_BLUR_RADIUS,
                            blendColor = colors.blurTint,
                        )
                    } else {
                        Modifier.background(colors.container, RoundedCornerShape(NavBarMetrics.containerRadius))
                    },
                )
                .height(NavBarMetrics.containerHeight)
                .fillMaxWidth()
                .padding(horizontal = NavBarMetrics.containerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                NavBarTab(index = index, onClick = { onSelect(index) }) {
                    val itemColor = if (index == pressedIndex) colors.iconPressed else colors.icon
                    Image(
                        modifier = Modifier.size(NavBarMetrics.iconSize),
                        imageVector = tab.icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(itemColor),
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

        // 手势层：按在栏上任意位置即可拖动（无「抓胶囊」判定），位移按手指增量累加，
        // 越过项中心即换选中项，松手按手指位置吸附最近项
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

                        // 按下即选中
                        pressed = true
                        pressedIndex = downIndex
                        if (downIndex != currentIndex) {
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

/** 选中指示器：左右边界各自动弹，宽度随两条边变化 */
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

    // 按下：整体缩 6dp + 填充加深
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

/** 按下缩放与加亮的弹簧曲线 */
private val PressSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 438f)

/** 底栏尺寸，数值取自日历真机实测（1080×2400，density 2.8125） */
internal object NavBarMetrics {
    val containerHeight: Dp = 54.dp // 实测 152px
    val containerRadius: Dp = containerHeight / 2 // 圆角 = 高度 / 2
    val containerPadding: Dp = 8.dp // 实测 22px
    val overlayHeight: Dp = containerHeight - 6.dp // 指示器上下各内缩 3dp
    val overlayRadius: Dp = containerRadius - 3.dp // 指示器圆角 = 容器圆角 − 3dp
    val indicatorOverhang: Dp = 5.dp // 指示器每边比条目槽宽出的量
    val pressInset: Dp = 6.dp // 按下时宽高各缩 6dp
    val iconSize: Dp = 26.dp // 按可见墨迹 57×53px 校准（日历盒子 28dp）
    val labelFontSize: Dp = 11.dp // footnote3，注意是 dp
    val barShadowRadius: Dp = 10.dp
    val liftShadowDelta: Dp = 8.dp // 拖动中额外抬升，对应日历 setElevation(8dp)
    const val LIFT_SHADOW_ALPHA = 0.8f // 抬升时投影浓度放大系数
    const val BAR_BLUR_RADIUS = 25f // 模糊半径（dp）
}

/** 底栏配色，取日历底栏用到的 miuix 颜色资源 */
internal object NavBarColors {
    val Light =
        NavBarPalette(
            container = Color(0xFFFFFFFF), // surface_highest_light
            blurTint = Color(0xFFFFFFFF).copy(0.92f),
            selector = Color(0x0F000000), // black_level9
            selectorPressed = Color(0x29000000), // 按下 16%
            icon = Color(0xFF000000), // black_level1，选中不变色
            iconPressed = Color(0x99000000), // black_level4
            barShadowAlpha = 0.1f,
        )

    val Dark =
        NavBarPalette(
            // 实测容器为 21，对应 white_solid_20，而非 surface_highest 的 #2C2C2C
            container = Color(0xFF181818),
            blurTint = Color(0xFF181818).copy(0.92f),
            selector = Color(0x1FFFFFFF), // white_level9
            selectorPressed = Color(0x42FFFFFF),
            icon = Color(0xF2FFFFFF), // white_level1_1
            iconPressed = Color(0x91FFFFFF),
            barShadowAlpha = 0.2f,
        )
}

internal data class NavBarPalette(
    val container: Color,
    val blurTint: Color,
    val selector: Color,
    val selectorPressed: Color,
    val icon: Color,
    val iconPressed: Color,
    val barShadowAlpha: Float,
)
