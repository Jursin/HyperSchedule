package top.jursin.hyperschedule.ui.page.transfer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.jursin.hyperschedule.data.importer.edu.Adapter
import top.jursin.hyperschedule.theme.CardCornerRadius
import top.jursin.hyperschedule.theme.SheetMaxHeight
import top.jursin.hyperschedule.theme.dangerColor
import top.jursin.hyperschedule.theme.isDarkTheme
import top.jursin.hyperschedule.theme.sheetCardColor
import top.jursin.hyperschedule.ui.component.ConfirmDialog
import top.jursin.hyperschedule.ui.component.SheetScaffold
import top.jursin.hyperschedule.ui.component.SheetScrollColumn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Contacts
import top.yukonga.miuix.kmp.icon.glass.FavoriteFill
import top.yukonga.miuix.kmp.icon.glass.FavoriteOutline
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.jursin.hyperschedule.data.importer.edu.School as EduSchool

/** 通用工具进入前填网址，留空禁用确定 */
@Composable
internal fun UrlInputDialog(
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val urlInput = rememberTextFieldState()
    ConfirmDialog(
        title = "输入网址",
        message = "请输入要访问的学校教务系统网址",
        appBlur = appBlur,
        confirmEnabled = urlInput.text.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(urlInput.text.toString().trim()) },
    ) {
        GlassTextField(
            state = urlInput,
            label = "网址",
            useLabelAsPlaceholder = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 适配器抽屉，点一个适配器进入网页导入 */
@Composable
internal fun AdapterSheet(
    school: EduSchool,
    show: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpen: (Adapter) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val adapters = school.adapters.filter { it.id !in ShareToolIds }

    SheetScaffold(
        title = school.name,
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        endAction = {
            TooltipBox(text = if (favorite) "取消收藏" else "收藏学校") {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (favorite) MiuixIcons.Glass.FavoriteFill else MiuixIcons.Glass.FavoriteOutline,
                        contentDescription = if (favorite) "取消收藏" else "收藏学校",
                        modifier = Modifier.size(24.dp),
                        tint = if (favorite) dangerColor() else MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) {
        if (adapters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "该学校暂无可用方案",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        } else {
            SheetScrollColumn(maxHeight = SheetMaxHeight) {
                adapters.forEach { adapter -> AdapterCard(adapter) { onOpen(adapter) } }
            }
        }
    }
}

/** 适配器卡片，名称、分类、描述与贡献者 */
@Composable
internal fun AdapterCard(adapter: Adapter, onClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCornerRadius))
                .clickable(onClick = onClick),
        cornerRadius = CardCornerRadius,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = sheetCardColor(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = adapter.name,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = adapter.categoryLabel(),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = adapter.description.ifBlank { "暂无描述" },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            if (adapter.maintainer.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiuixIcons.Glass.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "贡献者：${adapter.maintainer}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

private fun Adapter.categoryLabel(): String =
    when (category) {
        Adapter.BACHELOR -> "本科"
        Adapter.POSTGRADUATE -> "研究生"
        Adapter.TOOL -> "通用工具"
        else -> "其他"
    }

@Composable
internal fun SchoolItem(
    school: EduSchool,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
                .clip(RoundedCornerShape(CardCornerRadius))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                }
                .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = school.name,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 索引条尺寸 */
private val IndexBarWidth = 22.dp
private val IndexBarEndMargin = 14.dp

/** 触摸区 */
private val IndexBarTouchWidth = IndexBarWidth + IndexBarEndMargin + 20.dp
private val IndexBarPaddingVertical = 8.dp
private val IndexLetterSize = 10.sp
private val BubbleWidth = 82.dp
private val BubbleHeight = 62.dp
private val BubbleTextWidth = 62.dp
private val BubbleTextSize = 29.sp
private val BubblePopMargin = 12.dp

/** 气泡里的内容 */
private sealed interface BubbleTarget {
    data class Letter(val text: String) : BubbleTarget

    data object Favorite : BubbleTarget
}

internal val AlphabetBarWidth = BubblePopMargin + BubbleWidth + IndexBarWidth + IndexBarEndMargin

/** 气泡弹簧 */
private val BubbleShowSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 322f)
private val BubbleHideSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 987f)

internal val IndexLetters = ('A'..'Z').map(Char::toString)

/** 气泡轮廓 */
private const val BubblePathData =
    "M82,34.3458V41.4576C82,41.4576 81.4558,34.5282 72.2035,34.5282C67.0774,34.5282 62.2383,37.7886 58.6592,44.4635C53.6864,54.8752 43.0987,62.0635 30.8407,62.0635C13.8079,62.0635 0,48.1843 0,31.0635C0,13.9426 13.8079,0.0635 30.8407,0.0635C43.0987,0.0635 53.6864,7.2518 58.6592,17.6635C62.2383,24.3384 67.0774,27.5988 72.2035,27.5988C81.4558,27.5988 82,20.6694 82,20.6694V27.7811V34.3458Z"

@Composable
internal fun AlphabetBar(
    available: Set<String>,
    active: String?,
    onJump: (letter: String) -> Unit,
    modifier: Modifier = Modifier,
    showFavorite: Boolean = false,
    favoriteActive: Boolean = false,
    onJumpFavorite: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val shapeColor = MiuixTheme.colorScheme.onSurface
    val fillAlpha = if (isDarkTheme()) 0.08f else 0.04f
    val textMeasurer = rememberTextMeasurer()
    val starPainter = rememberVectorPainter(MiuixIcons.Glass.FavoriteOutline)
    val bubbleTextColor = MiuixTheme.colorScheme.primary
    val bubbleTextStyle = remember { TextStyle(fontSize = BubbleTextSize) }
    val bubble = remember { Animatable(0f) }
    var bubbleVisible by remember { mutableStateOf(false) }
    var bubbleTarget by remember { mutableStateOf<BubbleTarget?>(null) }
    var pressed by remember { mutableStateOf(false) }
    var dragLetter by remember { mutableStateOf<String?>(null) }
    var showJob by remember { mutableStateOf<Job?>(null) }
    var favoritePressed by remember { mutableStateOf(false) }

    fun hideBubble() {
        if (!bubbleVisible) return
        bubbleVisible = false
        scope.launch {
            bubble.animateTo(0f, BubbleHideSpring)
            if (!bubbleVisible) bubbleTarget = null
        }
    }

    fun showBubble(target: BubbleTarget) {
        bubbleTarget = target
        if (bubbleVisible) return
        bubbleVisible = true
        showJob =
            scope.launch {
                bubble.animateTo(1f, BubbleShowSpring)
                if (!pressed) hideBubble()
            }
    }

    BoxWithConstraints(modifier) {
        val padPx = with(density) { IndexBarPaddingVertical.toPx() }
        val barWidthPx = with(density) { IndexBarWidth.toPx() }
        val barEndPx = with(density) { IndexBarEndMargin.toPx() }
        val bubbleWidthPx = with(density) { BubbleWidth.toPx() }
        val bubbleHeightPx = with(density) { BubbleHeight.toPx() }
        val bubbleTextWidthPx = with(density) { BubbleTextWidth.toPx() }
        val bubblePath = remember(density) { bubblePath(density) }
        val innerPx = (constraints.maxHeight - padPx * 2).coerceAtLeast(1f)
        val leading = if (showFavorite) 1 else 0
        val slotPx = innerPx / (IndexLetters.size + leading)
        val slot = with(density) { slotPx.toDp() }
        val fitSize = with(density) { (slot - 2.dp).toSp() }
        val letterSize = if (fitSize < IndexLetterSize) fitSize else IndexLetterSize
        val starSize = with(density) { letterSize.toDp() }
        val bubbleStarPx = with(density) { BubbleTextSize.toDp().toPx() }

        fun bubbleTop(slotIndex: Int): Float =
            (padPx + (slotIndex + 0.5f) * slotPx - bubbleHeightPx / 2f)
                .coerceIn(0f, (constraints.maxHeight - bubbleHeightPx).coerceAtLeast(0f))

        fun pick(y: Float) {
            val index = ((y - padPx) / slotPx).toInt().coerceIn(0, IndexLetters.size + leading - 1)
            if (showFavorite && index == 0) {
                if (favoritePressed) return
                favoritePressed = true
                dragLetter = null
                showBubble(BubbleTarget.Favorite)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onJumpFavorite()
                return
            }
            favoritePressed = false
            val letter = IndexLetters[index - leading]
            if (letter == dragLetter || letter !in available) return
            dragLetter = letter
            showBubble(BubbleTarget.Letter(letter))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onJump(letter)
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val barLeft = size.width - barEndPx - barWidthPx
                        val bubbleLeft = barLeft - bubbleWidthPx
                        val target = bubbleTarget
                        val slotIndex =
                            when (target) {
                                is BubbleTarget.Letter -> leading + IndexLetters.indexOf(target.text)
                                BubbleTarget.Favorite, null -> 0
                            }
                        val top = bubbleTop(slotIndex)
                        val pivot = Offset(barLeft, top + bubbleHeightPx / 2f)
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { alpha = fillAlpha })
                            drawRoundRect(
                                color = shapeColor,
                                topLeft = Offset(barLeft, 0f),
                                size = Size(barWidthPx, size.height),
                                cornerRadius = CornerRadius(barWidthPx / 2f),
                            )
                            if (target != null) {
                                scale(bubble.value, bubble.value, pivot) {
                                    translate(bubbleLeft, top) { drawPath(bubblePath, shapeColor, alpha = bubble.value) }
                                }
                            }
                            canvas.restore()
                        }
                        if (target != null) {
                            scale(bubble.value, bubble.value, pivot) {
                                translate(bubbleLeft, top) {
                                    when (target) {
                                        is BubbleTarget.Letter -> {
                                            val layout = textMeasurer.measure(target.text, bubbleTextStyle)
                                            drawText(
                                                textLayoutResult = layout,
                                                color = bubbleTextColor.copy(alpha = bubble.value),
                                                topLeft =
                                                    Offset(
                                                        (bubbleTextWidthPx - layout.size.width) / 2f,
                                                        (bubbleHeightPx - layout.size.height) / 2f,
                                                    ),
                                            )
                                        }
                                        BubbleTarget.Favorite ->
                                            translate(
                                                (bubbleTextWidthPx - bubbleStarPx) / 2f,
                                                (bubbleHeightPx - bubbleStarPx) / 2f,
                                            ) {
                                                with(starPainter) {
                                                    draw(
                                                        size = Size(bubbleStarPx, bubbleStarPx),
                                                        colorFilter =
                                                            ColorFilter.tint(
                                                                bubbleTextColor.copy(alpha = bubble.value),
                                                            ),
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        }
                    },
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(IndexBarTouchWidth)
                    .pointerInput(available, showFavorite, slotPx, padPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            dragLetter = null
                            favoritePressed = false
                            pick(down.position.y)
                            down.consume()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    pick(change.position.y)
                                    change.consume()
                                }
                            } finally {
                                pressed = false
                                dragLetter = null
                                favoritePressed = false
                                if (showJob?.isActive != true) hideBubble()
                            }
                        }
                    },
        ) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = IndexBarEndMargin)
                        .width(IndexBarWidth)
                        .fillMaxHeight()
                        .padding(vertical = IndexBarPaddingVertical),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showFavorite) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(slot),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Glass.FavoriteOutline,
                            contentDescription = "收藏的学校",
                            modifier = Modifier.size(starSize),
                            tint =
                                if (favoriteActive) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                },
                        )
                    }
                }
                IndexLetters.forEach { letter ->
                    val usable = letter in available
                    val highlight = usable && letter == (dragLetter ?: active)
                    Box(
                        modifier = Modifier.fillMaxWidth().height(slot),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = letter,
                            fontSize = letterSize,
                            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                            color =
                                when {
                                    highlight -> MiuixTheme.colorScheme.primary
                                    usable -> MiuixTheme.colorScheme.onSurfaceVariantActions
                                    else -> MiuixTheme.colorScheme.outline
                                },
                            textAlign = TextAlign.Center,
                            lineHeight = letterSize,
                            maxLines = 1,
                            softWrap = false,
                            style =
                                TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun bubblePath(density: Density) =
    PathParser()
        .parsePathString(BubblePathData)
        .toPath()
        .apply {
            transform(
                Matrix().apply {
                    scale(
                        with(density) { BubbleWidth.toPx() } / 82f,
                        with(density) { BubbleHeight.toPx() } / 63f,
                    )
                },
            )
        }

/** 居中的提示，用于加载中与空列表 */
@Composable
internal fun LoadingHint(
    text: String,
    showProgress: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
