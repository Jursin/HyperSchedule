package top.jursin.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.theme.CardCornerRadius
import top.jursin.hyperschedule.theme.ContainerTintAlpha
import top.jursin.hyperschedule.theme.accentBlueColor
import top.jursin.hyperschedule.theme.dangerColor
import top.jursin.hyperschedule.theme.warningColor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 行内代码 */
private val InlineCodePattern = Regex("`([^`]+)`")

/** 轻量 Markdown 渲染，处理标题、段落、列表、提示块与行内代码 */
@Composable
internal fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> MarkdownBlockItem(block) }
    }
}

@Composable
private fun MarkdownBlockItem(block: MarkdownBlock) {
    val scheme = MiuixTheme.colorScheme
    when (block) {
        is MarkdownBlock.Heading ->
            MarkdownInlineText(
                source = block.text,
                style = headingStyle(block.level),
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )

        is MarkdownBlock.Paragraph ->
            MarkdownInlineText(
                source = block.text,
                style = bodyStyle(),
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

        is MarkdownBlock.Bullet -> MarkdownListItem(depth = block.depth, source = block.text)

        is MarkdownBlock.Alert -> MarkdownAlertCard(kind = block.kind, text = block.text)
    }
}

private fun parseInlineCode(source: String): Pair<AnnotatedString, List<String>> {
    val codes = mutableListOf<String>()
    val text =
        buildAnnotatedString {
            var cursor = 0
            InlineCodePattern.findAll(source).forEach { match ->
                append(source.substring(cursor, match.range.first))
                val code = match.groupValues[1]
                appendInlineContent("code${codes.size}", code)
                codes += code
                cursor = match.range.last + 1
            }
            append(source.substring(cursor))
        }
    return text to codes
}

@Composable
private fun MarkdownInlineText(
    source: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val (text, codes) = remember(source) { parseInlineCode(source) }
    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = rememberInlineContent(codes, style, color),
        modifier = modifier,
    )
}

@Composable
private fun rememberInlineContent(
    codes: List<String>,
    style: TextStyle,
    color: Color,
): Map<String, InlineTextContent> {
    if (codes.isEmpty()) return emptyMap()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val background = MiuixTheme.colorScheme.surfaceContainerHigh
    val codeStyle = style.copy(fontFamily = FontFamily.Monospace, lineHeight = TextUnit.Unspecified)
    return remember(codes, codeStyle, background, color) {
        codes.withIndex().associate { (index, code) ->
            val measured = measurer.measure(AnnotatedString(code), codeStyle, maxLines = 1, softWrap = false)
            val width = with(density) { (measured.size.width.toDp() + 8.dp).toSp() }
            val height = with(density) { (measured.size.height.toDp() + 2.dp).toSp() }
            "code$index" to
                InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.TextCenter)) { text ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = text, style = codeStyle, color = color, maxLines = 1, softWrap = false)
                    }
                }
        }
    }
}

@Composable
private fun MarkdownAlertCard(kind: AlertKind, text: String) {
    val tint =
        when (kind) {
            AlertKind.Important -> accentBlueColor()
            AlertKind.Warning -> warningColor()
            AlertKind.Caution -> dangerColor()
        }
    val colors =
        CardDefaults.defaultColors(
            color = tint.copy(alpha = ContainerTintAlpha),
            contentColor = tint,
        )
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = colors,
    ) {
        MarkdownInlineText(
            source = text,
            style = bodyStyle().copy(fontWeight = FontWeight.SemiBold),
            color = colors.contentColor,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun MarkdownListItem(depth: Int, source: String) {
    val scheme = MiuixTheme.colorScheme
    val style = bodyStyle()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 14).dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "•",
            style = style,
            color = scheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
        MarkdownInlineText(
            source = source,
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
