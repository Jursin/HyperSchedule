package com.hyperschedule.app.ui.component

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

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

/** 行内嵌套超过此深度时按纯文本处理 */
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

/** 行内渲染结果 */
internal class MarkdownInline(val text: AnnotatedString, val codes: List<String>)

internal fun inlineMarkdown(source: String, look: MarkdownLook): MarkdownInline {
    val codes = mutableListOf<String>()
    val text = buildAnnotatedString { appendInline(source, null, 0, look, codes) }
    return MarkdownInline(text, codes)
}

/** 普通文字连续成段再 append，减少样式副本 */
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

    /** 跳过当前标记，内容按 [style] 重解析，游标推到 [next] */
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
                    // 圆角背景靠内联组件画，alternateText 兜底纯文本
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
                        // 下划线同时写进文本样式，链接样式失效时仍可辨认
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

/** 找不成对的单个标记，跳过成双写法与空内容 */
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
