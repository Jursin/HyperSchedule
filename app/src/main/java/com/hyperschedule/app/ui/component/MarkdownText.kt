package com.hyperschedule.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 行间代码块的圆角 */
private val CodeBlockCorner = 8.dp

/** 行内代码的圆角 */
private val InlineCodeCorner = 6.dp

/** 行内代码芯片的内边距 */
private val CodePaddingHorizontal = 4.dp
private val CodePaddingVertical = 1.dp

/** 块与块之间的间距 */
private val BlockSpacing = 8.dp

/** 轻量 Markdown 渲染 */
@Composable
internal fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val look = rememberMarkdownLook()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BlockSpacing)) {
        blocks.forEach { block -> MarkdownBlockItem(block, look) }
    }
}

/** 行内样式用到的颜色与跳转方式，块之间共用一份 */
internal class MarkdownLook(
    val linkColor: Color,
    val openLink: (String) -> Unit,
)

@Composable
private fun rememberMarkdownLook(): MarkdownLook {
    val uriHandler = LocalUriHandler.current
    val linkColor = MiuixTheme.colorScheme.primary
    return remember(uriHandler, linkColor) {
        MarkdownLook(linkColor) { url -> runCatching { uriHandler.openUri(url) } }
    }
}

@Composable
private fun MarkdownBlockItem(block: MarkdownBlock, look: MarkdownLook) {
    val scheme = MiuixTheme.colorScheme
    when (block) {
        is MarkdownBlock.Heading ->
            MarkdownInlineText(
                source = block.text,
                look = look,
                style = headingStyle(block.level),
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )

        is MarkdownBlock.Paragraph ->
            MarkdownInlineText(
                source = block.text,
                look = look,
                style = bodyStyle(),
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

        is MarkdownBlock.Bullet ->
            MarkdownListItem(marker = "•", depth = block.depth, source = block.text, look = look)

        is MarkdownBlock.Numbered ->
            MarkdownListItem(marker = "${block.number}.", depth = block.depth, source = block.text, look = look)

        is MarkdownBlock.Quote ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(scheme.outline),
                )
                MarkdownInlineText(
                    source = block.text,
                    look = look,
                    style = bodyStyle(),
                    color = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
            }

        is MarkdownBlock.Code ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CodeBlockCorner))
                        .background(scheme.surfaceContainerHigh)
                        .padding(10.dp),
            ) {
                // 代码不折行，横向自己滚，避免把长行挤成一堆
                Text(
                    text = AnnotatedString(block.text),
                    style = bodyStyle().copy(fontFamily = FontFamily.Monospace),
                    color = scheme.onSurface,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }

        MarkdownBlock.Rule ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                thickness = 1.dp,
                color = scheme.outline.copy(alpha = 0.4f),
            )
    }
}

/** 带行内样式的文本，行内代码在这里换成圆角芯片 */
@Composable
private fun MarkdownInlineText(
    source: String,
    look: MarkdownLook,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val inline = remember(source, look) { inlineMarkdown(source, look) }
    Text(
        text = inline.text,
        style = style,
        color = color,
        inlineContent = rememberInlineContent(inline.codes, style),
        modifier = modifier,
    )
}

/** 行内代码只能塞一个内联组件占位 */
@Composable
private fun rememberInlineContent(codes: List<String>, style: TextStyle): Map<String, InlineTextContent> {
    if (codes.isEmpty()) return emptyMap()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scheme = MiuixTheme.colorScheme
    val codeStyle = style.copy(fontFamily = FontFamily.Monospace, lineHeight = TextUnit.Unspecified)
    return remember(codes, codeStyle, scheme) {
        codes.withIndex().associate { (index, code) ->
            val measured = measurer.measure(AnnotatedString(code), codeStyle, maxLines = 1, softWrap = false)
            val width = with(density) { (measured.size.width.toDp() + CodePaddingHorizontal * 2).toSp() }
            val height = with(density) { (measured.size.height.toDp() + CodePaddingVertical * 2).toSp() }
            "code$index" to
                InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.TextCenter)) { text ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(InlineCodeCorner))
                                .background(scheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = text,
                            style = codeStyle,
                            color = scheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
        }
    }
}

/** 列表项，缩进按层级走，标记与正文首行同基线 */
@Composable
private fun MarkdownListItem(marker: String, depth: Int, source: String, look: MarkdownLook) {
    val scheme = MiuixTheme.colorScheme
    val style = bodyStyle()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 14).dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = marker,
            style = style,
            color = scheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
        MarkdownInlineText(
            source = source,
            look = look,
            style = style,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
    }
}

@Composable
private fun bodyStyle(): TextStyle =
    MiuixTheme.textStyles.body2.let { it.copy(lineHeight = it.fontSize * 1.35f) }

/** 标题逐级递减，最大只到 body2 加粗 */
@Composable
private fun headingStyle(level: Int): TextStyle {
    val styles = MiuixTheme.textStyles
    val base =
        when {
            level <= 1 -> styles.title3
            level == 2 -> styles.title4
            level == 3 -> styles.body1
            else -> styles.body2
        }
    return base.copy(fontWeight = FontWeight.Medium)
}
