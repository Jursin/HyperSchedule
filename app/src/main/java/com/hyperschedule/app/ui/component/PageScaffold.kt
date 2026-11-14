package com.hyperschedule.app.ui.component

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperschedule.app.theme.PageBottomInset
import com.hyperschedule.app.theme.PageHorizontalMargin
import com.hyperschedule.app.theme.PageTopPadding
import com.hyperschedule.app.theme.PanelCorner
import com.hyperschedule.app.theme.SectionSpacing
import com.hyperschedule.app.ui.effect.LocalPageOverscroll
import com.hyperschedule.app.ui.effect.appBarBand
import com.hyperschedule.app.ui.effect.appBarBandBackdrop
import com.hyperschedule.app.ui.effect.captureForBlur
import com.hyperschedule.app.ui.effect.overscrollTracker
import com.hyperschedule.app.ui.effect.wallpaperVisible
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.core.graphics.withSave

internal val TopBarTitlePadding = 28.dp

internal val TopBarIconPadding = 14.dp

/** 浮窗内顶栏要补的高度，返回按钮圆心对齐窗口圆角的圆心 */
@Composable
private fun windowTopBarInset(): Dp =
    if (LocalInsideFloatingWindow.current) {
        PanelCorner - TopAppBarDefaults.SmallTopAppBarCenterHeight / 2
    } else {
        0.dp
    }

@Composable
private fun windowTopBarIconPadding(): Dp =
    if (LocalInsideFloatingWindow.current) {
        PanelCorner - IconButtonDefaults.MinWidth / 2
    } else {
        TopBarIconPadding
    }

/** 浮窗内顶栏整体下移，补出来的一截用 [color] 铺底 */
@Composable
private fun Modifier.windowTopBarTrim(color: Color): Modifier {
    val inset = windowTopBarInset()
    return if (inset > 0.dp) this.background(color).padding(top = inset) else this
}

@Composable
fun BlurredTopAppBar(
    title: String,
    blurBackdrop: Backdrop?,
    scrollBehavior: ScrollBehavior,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    pinnedTitle: Boolean = false,
    titlePadding: Dp = TopBarTitlePadding,
    color: Color? = null,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    materialPinned: Boolean = false,
    materialStart: Dp = LocalPageTopPadding.current,
) {
    val navigationIcon = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TopBarIconButton(onClick = onBack) {
                    Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "返回")
                }
            }
            leading?.invoke()
        }
    }
    val insideWindow = LocalInsideFloatingWindow.current
    val bandBackdrop = appBarBandBackdrop(blurBackdrop)
    val banded = bandBackdrop != null
    val barColor = color ?: if (banded) Color.Transparent else MiuixTheme.colorScheme.surface
    // 浮窗内顶栏下移、图标边距收紧
    val iconPadding = windowTopBarIconPadding()
    val barModifier = Modifier.windowTopBarTrim(barColor)
    // 浮窗内一律用单行顶栏
    val singleLine = pinnedTitle || insideWindow
    // 单行顶栏的标题居中，左右 padding 会挤掉标题宽度
    val effectiveTitlePadding = if (insideWindow) 0.dp else titlePadding
    // 绘制期读滚动量与拉伸量，滚动不进重组
    val density = LocalDensity.current
    val startPx = with(density) { materialStart.toPx() }
    val fade = with(density) { TopBarMaterialFade.toPx() }
    val overscroll = LocalPageOverscroll.current
    val stretchGain = remember { Animatable(0f) }
    val overScrollState = LocalOverScrollState.current
    LaunchedEffect(overScrollState, overscroll) {
        snapshotFlow { overScrollState.isOverScrollActive }.collect { active ->
            if (active) {
                stretchGain.snapTo(1f)
            } else {
                overscroll.settle()
                stretchGain.animateTo(0f, tween(StretchFadeMillis))
            }
        }
    }
    val materialAlpha: () -> Float =
        if (materialPinned) {
            { 1f }
        } else {
            {
                val scrolled = -scrollBehavior.state.contentOffset
                val stretched = (overscroll.stretch - startPx) * stretchGain.value
                (maxOf(scrolled - startPx, stretched) / fade).coerceIn(0f, 1f)
            }
        }
    // 材质强度只管按钮底色，糊带常驻
    CompositionLocalProvider(
        LocalTopBarBanded provides banded,
        LocalTopBarMaterialAlpha provides materialAlpha,
    ) {
        Box {
            if (banded) {
                // 糊带常驻不淡入
                if (singleLine) {
                    // 单行顶栏高度固定，糊带直接铺满
                    Box(modifier = Modifier.matchParentSize().appBarBand(bandBackdrop))
                } else {
                    // 大标题顶栏折叠时高度逐帧变，糊带跟着变会让模糊的文字忽明忽暗
                    BandOverlay(
                        height = TopBarBandHeight + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        modifier = Modifier.fillMaxWidth().appBarBand(bandBackdrop),
                    )
                }
            }
            if (singleLine) {
                SmallTopAppBar(
                    title = title,
                    modifier = barModifier,
                    color = barColor,
                    titleColor = titleColor,
                    scrollBehavior = scrollBehavior,
                    titlePadding = effectiveTitlePadding,
                    navigationIconPadding = iconPadding,
                    actionIconPadding = iconPadding,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    bottomContent = bottomContent,
                )
            } else {
                TopAppBar(
                    title = title,
                    modifier = barModifier,
                    color = barColor,
                    titleColor = titleColor,
                    scrollBehavior = scrollBehavior,
                    titlePadding = effectiveTitlePadding,
                    navigationIconPadding = iconPadding,
                    actionIconPadding = iconPadding,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    bottomContent = bottomContent,
                )
            }
        }
    }
}

/** 糊带在栏体之外的固定高度，折叠时也不跟着变 */
private val TopBarBandHeight = 72.dp

/** 零高度的糊带宿主：按固定高度向下溢出绘制，不参与测量，折叠时几何不变 */
@Composable
private fun BandOverlay(height: Dp, modifier: Modifier) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable =
                        measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    layout(constraints.maxWidth, 0) { placeable.place(0, 0) }
                },
    ) {
        Box(modifier = modifier.height(height))
    }
}

/** 按钮底色浮现距离：内容滚过这么多，圆片才从 0 淡到满 */
private val TopBarMaterialFade = 12.dp

/** 回弹结束时拉伸量的淡出时长（ms） */
private const val StretchFadeMillis = 120

/** 顶栏是否叠着渐进糊带，栏内按钮据此决定要不要垫底色 */
val LocalTopBarBanded = staticCompositionLocalOf { false }

/** 顶栏材质强度（0..1）：内容滚到栏下才浮现，绘制期读取 */
val LocalTopBarMaterialAlpha = staticCompositionLocalOf<() -> Float> { { 0f } }

/** 顶栏图标按钮：内容滚到栏下时图标才垫上一层容器色，回到顶部跟着消失 */
@Composable
fun TopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = IconButtonDefaults.MinWidth,
    minHeight: Dp = IconButtonDefaults.MinHeight,
    content: @Composable () -> Unit,
) {
    val banded = LocalTopBarBanded.current
    if (!banded) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minWidth = minWidth,
            minHeight = minHeight,
            content = content,
        )
        return
    }
    val materialAlpha = LocalTopBarMaterialAlpha.current
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    // 有壁纸时用半透明容器色，透出糊带里已模糊的内容，实心圆会在壁纸上糊出一块灰
    val fill =
        if (wallpaperVisible()) {
            MiuixTheme.colorScheme.surfaceContainer.copy(
                alpha = if (dark) ChipGlassAlphaDark else ChipGlassAlphaLight,
            )
        } else {
            MiuixTheme.colorScheme.surfaceContainer
        }
    // 画在外层：按钮会按自身形状裁剪，画在里面阴影会被裁掉
    Box(modifier = modifier.topBarChip(materialAlpha, fill, dark)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            minWidth = minWidth,
            minHeight = minHeight,
            content = content,
        )
    }
}

/** 浮起底色：圆形容器色 + 贴外沿 1dp 高光描边 + 极淡外投影，都乘材质强度 */
private fun Modifier.topBarChip(
    alpha: () -> Float,
    fill: Color,
    dark: Boolean,
): Modifier =
    drawWithCache {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // 本体轮廓：投影按它镂空，只落在圆外
        val body = Path().apply { addOval(Rect(center, radius)) }.asAndroidPath()
        val strokeWidth = ChipStrokeWidth.toPx()
        val stroke = Color.White.copy(alpha = if (dark) ChipStrokeAlphaDark else ChipStrokeAlphaLight)
        val rimShadow = if (dark) ChipShadowAlphaDark else ChipShadowAlphaLight
        val shadowOffset = ChipShadowOffsetY.toPx()
        // 外投影用径向渐变（setShadowLayer 在硬件加速画布上不出影），深浅交给 Paint.alpha
        val haloRadius = radius + ChipShadowExtent.toPx()
        val shadowPaint =
            Paint().apply {
                isAntiAlias = true
                shader =
                    RadialGradient(
                        center.x,
                        center.y,
                        haloRadius,
                        intArrayOf(
                            ChipShadowColor.copy(alpha = 1f).toArgb(),
                            ChipShadowColor.copy(alpha = 0.5f).toArgb(),
                            ChipShadowColor.copy(alpha = 0f).toArgb(),
                        ),
                        floatArrayOf(0f, radius / haloRadius, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        onDrawBehind {
            val strength = alpha()
            if (strength <= 0.01f) return@onDrawBehind
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.withSave {
                    // 先镂空再位移：圆内一点投影都留不下，圆外才是一整片模糊
                    native.clipOutPath(body)
                    native.translate(0f, shadowOffset)
                    // 外沿处是 shader 的一半浓淡，所以这里按两倍给
                    shadowPaint.alpha = (rimShadow * 2f * strength * 255f).toInt().coerceIn(0, 255)
                    native.drawCircle(center.x, center.y, haloRadius, shadowPaint)
                }
            }
            drawCircle(color = fill.copy(alpha = fill.alpha * strength), radius = radius, center = center)
            // 贴外沿的内描边
            drawCircle(
                color = stroke.copy(alpha = stroke.alpha * strength),
                radius = radius - strokeWidth / 2f,
                center = center,
                style = Stroke(strokeWidth),
            )
        }
    }

/** 有壁纸时按钮底色的不透明度 */
private const val ChipGlassAlphaLight = 0.5f

private const val ChipGlassAlphaDark = 0.66f

/** 高光描边宽度 */
private val ChipStrokeWidth = 1.dp

private const val ChipStrokeAlphaLight = 0.9f

private const val ChipStrokeAlphaDark = 0.1f

/** 外投影：外沿最浓，往外淡到没有，只往下压 1dp */
private val ChipShadowColor = Color.Black

private const val ChipShadowAlphaLight = 0.04f

private const val ChipShadowAlphaDark = 0.07f

/** 投影铺开的宽度 */
private val ChipShadowExtent = 8.dp

private val ChipShadowOffsetY = 1.dp

/** 页面内容的水平边距，浮窗里换成窗口内边距 */
val LocalPageHorizontalMargin = staticCompositionLocalOf { PageHorizontalMargin }

/** 正文距顶栏的间距，浮窗里换成窗口内边距 */
val LocalPageTopPadding = staticCompositionLocalOf { PageTopPadding }

/** 当前页面是否画在整页浮窗里 */
val LocalInsideFloatingWindow = staticCompositionLocalOf { false }

@Composable
fun PageScrollColumn(
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    topInset: Dp,
    topPadding: Dp = LocalPageTopPadding.current,
    bottomPadding: Dp = PageBottomInset,
    // 翻页容器里只有当前页传 true
    syncMaterial: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val horizontalPadding = LocalPageHorizontalMargin.current
    val scrollState = rememberScrollState()
    // 各页共用一个 behavior，翻页后要按本页真实位置改写
    LaunchedEffect(syncMaterial, scrollState) {
        if (syncMaterial) scrollBehavior.state.contentOffset = -scrollState.value.toFloat()
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .pageScroll(blurBackdrop, scrollBehavior)
                .clearFocusOnPress(focusManager)
                .verticalScroll(scrollState)
                .padding(
                    start = horizontalPadding,
                    top = topInset + topPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        content = content,
    )
}

/** 页面滚动容器共用的毛玻璃与滚动手势 */
@Composable
fun Modifier.pageScroll(
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
): Modifier {
    val overscroll = LocalPageOverscroll.current
    // 阻尼按容器高度算，得和 miuix 同口径才还原得出内容实际走的距离
    val stretchRange = LocalWindowInfo.current.containerSize.height.toFloat()
    val tracker = remember(overscroll, stretchRange) { overscrollTracker(overscroll, stretchRange) }
    return this
        .captureForBlur(blurBackdrop)
        .bouncyScroll()
        .nestedScroll(scrollBehavior.nestedScrollConnection)
        // 挂在 overScroll 里面：先看到还没被拉伸吃掉的位移，自己一个像素都不消费
        .nestedScroll(tracker)
}

/** 滚动容器的回弹与滚到底触感 */
fun Modifier.bouncyScroll(): Modifier = overScrollVertical().scrollEndHaptic()

@Composable
fun pageContentPadding(
    paddingValues: PaddingValues,
    bottom: Dp,
): PaddingValues =
    PaddingValues(
        start = LocalPageHorizontalMargin.current,
        top = paddingValues.calculateTopPadding() + LocalPageTopPadding.current,
        end = LocalPageHorizontalMargin.current,
        bottom = bottom,
    )

/** 按下页面内任意位置（包括按钮）时收起输入焦点与键盘 */
internal fun Modifier.clearFocusOnPress(focusManager: FocusManager): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                    focusManager.clearFocus()
                }
            }
        }
    }

/** 右下角保存按钮，不可保存时用禁用配色 */
@Composable
fun SaveFab(canSave: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor =
            if (canSave) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.disabledPrimaryButton
            },
    ) {
        Icon(
            imageVector = MiuixIcons.Ok,
            contentDescription = "保存",
            tint =
                if (canSave) {
                    MiuixTheme.colorScheme.onPrimary
                } else {
                    MiuixTheme.colorScheme.disabledOnPrimaryButton
                },
        )
    }
}

/** 右下角附加操作按钮（如添加规则），不可用时用禁用配色 */
@Composable
fun AddFab(enabled: Boolean, description: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor =
            if (enabled) {
                MiuixTheme.colorScheme.secondaryVariant
            } else {
                MiuixTheme.colorScheme.disabledSecondaryVariant
            },
    ) {
        Icon(
            imageVector = MiuixIcons.Add,
            contentDescription = description,
            tint =
                if (enabled) {
                    MiuixTheme.colorScheme.onSecondaryVariant
                } else {
                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                },
        )
    }
}
