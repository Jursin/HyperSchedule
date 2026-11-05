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
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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

/** 列表项，缩进按层级走，标记与正文首行同基线；圆点用同号字形，和浏览器里的列表一致 */
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

// 解析

/** Markdown 里的一个块级元素 */
internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Paragraph(val text: String) : MarkdownBlock

    data class Bullet(val depth: Int, val text: String) : MarkdownBlock

    data class Numbered(val depth: Int, val number: String, val text: String) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data class Code(val text: String) : MarkdownBlock

    data object Rule : MarkdownBlock
}

private val HeadingPattern = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BulletPattern = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NumberedPattern = Regex("""^(\s*)(\d{1,9})[.)]\s+(.*)$""")
private val RulePattern = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
private val QuotePattern = Regex("""^\s{0,3}>\s?(.*)$""")
private val LinkPattern = Regex("""\[([^]]*)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""")

/** 解析 <u> 这一段原始 HTML */
private val UnderlinePattern = Regex("""<u>(.*?)</u>""", RegexOption.IGNORE_CASE)

/** 列表最多缩进三层 */
private const val MaxListDepth = 3

/** 行内嵌套过深时直接当纯文本，防止异常输入把递归撑爆 */
private const val MaxInlineDepth = 8

internal fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var paragraph = ""

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) blocks += MarkdownBlock.Paragraph(paragraph)
        paragraph = ""
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        val fence =
            when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }
        when {
            trimmed.isEmpty() -> {
                flushParagraph()
                index++
            }

            fence != null -> {
                flushParagraph()
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith(fence)) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[index])
                    index++
                }
                // 跳过结束围栏，源码里没闭合时 index 已经越界
                index++
                blocks += MarkdownBlock.Code(code.toString())
            }

            RulePattern.matches(line) -> {
                flushParagraph()
                blocks += MarkdownBlock.Rule
                index++
            }

            else -> {
                val heading = HeadingPattern.find(line)
                val quote = QuotePattern.find(line)
                val bullet = BulletPattern.find(line)
                val numbered = NumberedPattern.find(line)
                when {
                    heading != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                        index++
                    }

                    quote != null -> {
                        flushParagraph()
                        // 连续的引用行合成一段，行间保留换行
                        val text = StringBuilder(quote.groupValues[1])
                        index++
                        while (index < lines.size) {
                            val next = QuotePattern.find(lines[index]) ?: break
                            text.append('\n').append(next.groupValues[1])
                            index++
                        }
                        blocks += MarkdownBlock.Quote(text.toString())
                    }

                    bullet != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock.Bullet(listDepth(bullet.groupValues[1]), bullet.groupValues[2].trim())
                        index++
                    }

                    numbered != null -> {
                        flushParagraph()
                        blocks +=
                            MarkdownBlock.Numbered(
                                depth = listDepth(numbered.groupValues[1]),
                                number = numbered.groupValues[2],
                                text = numbered.groupValues[3].trim(),
                            )
                        index++
                    }

                    else -> {
                        // Markdown 的软换行按空格接起来，中文之间不补空格
                        paragraph = appendSoftLine(paragraph, trimmed)
                        index++
                    }
                }
            }
        }
    }
    flushParagraph()
    return blocks
}

private fun listDepth(indent: String): Int = (indent.length / 2).coerceIn(0, MaxListDepth)

private fun appendSoftLine(current: String, next: String): String =
    when {
        current.isEmpty() -> next
        isWide(current.last()) && isWide(next.first()) -> current + next
        else -> "$current $next"
    }

/** 中日韩文字与全角标点 */
private fun isWide(char: Char): Boolean =
    char.code in 0x2E80..0x9FFF || char.code in 0xFF00..0xFF60 || char.code in 0x3000..0x303F

// 行内

/** 行内渲染结果 */
internal class MarkdownInline(val text: AnnotatedString, val codes: List<String>)

internal fun inlineMarkdown(source: String, look: MarkdownLook): MarkdownInline {
    val codes = mutableListOf<String>()
    val text = buildAnnotatedString { appendInline(source, null, 0, look, codes) }
    return MarkdownInline(text, codes)
}

/** 普通文字连续成段再 append，避免每个字符各留一份样式 */
private fun AnnotatedString.Builder.appendInline(
    text: String,
    style: SpanStyle?,
    depth: Int,
    look: MarkdownLook,
    codes: MutableList<String>,
) {
    if (style == null) {
        appendInlineBody(text, depth, look, codes)
    } else {
        withStyle(style) { appendInlineBody(text, depth, look, codes) }
    }
}

private fun AnnotatedString.Builder.appendInlineBody(
    text: String,
    depth: Int,
    look: MarkdownLook,
    codes: MutableList<String>,
) {
    if (depth >= MaxInlineDepth) {
        append(text)
        return
    }
    var index = 0
    var runStart = 0

    fun flushRun(end: Int) {
        if (end > runStart) append(text.substring(runStart, end))
    }

    /** 跳过当前标记，把 [from, to) 里的内容按 [style] 再解析一遍，然后把游标推到 [next] */
    fun markup(from: Int, to: Int, style: SpanStyle, next: Int) {
        flushRun(index)
        appendInline(text.substring(from, to), style, depth + 1, look, codes)
        index = next
        runStart = next
    }

    while (index < text.length) {
        val char = text[index]
        when {
            char == '`' -> {
                val end = text.indexOf('`', index + 1)
                // 没闭合就把它当普通字符留在文段里
                if (end <= index + 1) {
                    index++
                } else {
                    flushRun(index)
                    val code = text.substring(index + 1, end)
                    // 圆角背景只能靠内联组件画，alternateText 同时兜底纯文本显示
                    appendInlineContent("code${codes.size}", code)
                    codes += code
                    index = end + 1
                    runStart = index
                }
            }

            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val marker = text.substring(index, index + 2)
                val end = text.indexOf(marker, index + 2).takeIf { it > index + 2 }
                if (end == null) {
                    index += 2
                } else {
                    markup(index + 2, end, SpanStyle(fontWeight = FontWeight.Bold), end + 2)
                }
            }

            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2).takeIf { it > index + 2 }
                if (end == null) {
                    index += 2
                } else {
                    markup(index + 2, end, SpanStyle(textDecoration = TextDecoration.LineThrough), end + 2)
                }
            }

            char == '[' -> {
                val link = LinkPattern.matchAt(text, index)
                if (link == null) {
                    index++
                } else {
                    flushRun(index)
                    val span = SpanStyle(color = look.linkColor, textDecoration = TextDecoration.Underline)
                    val listener = LinkInteractionListener { annotation ->
                        (annotation as? LinkAnnotation.Url)?.url?.let(look.openLink)
                    }
                    withLink(LinkAnnotation.Url(link.groupValues[2], TextLinkStyles(style = span), listener)) {
                        // 下划线同时写进文本样式，链接样式万一没生效也看得出是链接
                        withStyle(span) { append(link.groupValues[1]) }
                    }
                    index = link.range.last + 1
                    runStart = index
                }
            }

            char == '<' -> {
                val tag = UnderlinePattern.matchAt(text, index)
                val inner = tag?.groups?.get(1)?.range
                if (tag == null || inner == null) {
                    index++
                } else {
                    markup(inner.first, inner.last + 1, SpanStyle(textDecoration = TextDecoration.Underline), tag.range.last + 1)
                }
            }

            char == '*' || char == '_' -> {
                val end = findSingleMarker(text, char, index + 1)
                if (end < 0) {
                    index++
                } else {
                    markup(index + 1, end, SpanStyle(fontStyle = FontStyle.Italic), end + 1)
                }
            }

            else -> index++
        }
    }
    flushRun(text.length)
}

/** 找不成对的单个标记，跳过 `**` 这类成双的写法，空内容不算配对 */
private fun findSingleMarker(text: String, marker: Char, from: Int): Int {
    var index = from
    while (index < text.length) {
        if (text[index] == marker) {
            if (index + 1 < text.length && text[index + 1] == marker) {
                index += 2
                continue
            }
            return if (index == from) -1 else index
        }
        index++
    }
    return -1
}
