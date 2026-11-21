package top.jursin.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 轻量 Markdown 渲染，只处理标题、段落与列表 */
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
            Text(
                text = block.text,
                style = headingStyle(block.level),
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )

        is MarkdownBlock.Paragraph ->
            Text(
                text = block.text,
                style = bodyStyle(),
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

        is MarkdownBlock.Bullet -> MarkdownListItem(depth = block.depth, source = block.text)
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
        Text(
            text = source,
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
