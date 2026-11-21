package top.jursin.hyperschedule.ui.component

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Paragraph(val text: String) : MarkdownBlock

    data class Bullet(val depth: Int, val text: String) : MarkdownBlock
}

private val HeadingPattern = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BulletPattern = Regex("""^(\s*)[-*+]\s+(.*)$""")

/** 列表最多缩进三层 */
private const val MaxListDepth = 3

internal fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var paragraph = ""

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) blocks += MarkdownBlock.Paragraph(paragraph)
        paragraph = ""
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            flushParagraph()
            continue
        }

        val heading = HeadingPattern.find(line)
        if (heading != null) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            continue
        }

        val bullet = BulletPattern.find(line)
        if (bullet != null) {
            flushParagraph()
            blocks += MarkdownBlock.Bullet(listDepth(bullet.groupValues[1]), bullet.groupValues[2].trim())
            continue
        }

        // Markdown 的软换行按空格接起来，中文之间不补空格
        paragraph = appendSoftLine(paragraph, trimmed)
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
