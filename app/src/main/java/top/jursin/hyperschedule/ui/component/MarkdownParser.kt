package top.jursin.hyperschedule.ui.component

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Paragraph(val text: String) : MarkdownBlock

    data class Bullet(val depth: Int, val text: String) : MarkdownBlock

    /** GitHub 风格提示块，按类型着色 */
    data class Alert(val kind: AlertKind, val text: String) : MarkdownBlock
}

internal enum class AlertKind { Important, Warning, Caution }

private val HeadingPattern = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BulletPattern = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val AlertPattern =
    Regex("""^\s{0,3}>\s*\[!(important|warning|caution)]\s*$""", RegexOption.IGNORE_CASE)
private val QuotePattern = Regex("""^\s{0,3}>\s?(.*)$""")

/** 列表最多缩进三层 */
private const val MaxListDepth = 3

internal fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var paragraph = ""
    var alertKind: AlertKind? = null
    var alertText = ""
    var alertBlankLines = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) blocks += MarkdownBlock.Paragraph(paragraph)
        paragraph = ""
    }

    fun flushAlert() {
        alertKind?.let { blocks += MarkdownBlock.Alert(it, alertText.trim()) }
        alertKind = null
        alertText = ""
        alertBlankLines = 0
    }

    for (line in lines) {
        val alertStart = AlertPattern.find(line)
        if (alertStart != null) {
            flushParagraph()
            flushAlert()
            alertKind = alertKindOf(alertStart.groupValues[1])
            continue
        }

        if (alertKind != null) {
            val quoted = QuotePattern.find(line)
            if (quoted != null) {
                val content = quoted.groupValues[1].trim()
                if (content.isEmpty()) {
                    if (alertText.isNotEmpty()) alertBlankLines++
                } else {
                    if (alertText.isNotEmpty()) {
                        alertText +=
                            when {
                                alertBlankLines >= 2 -> "\n\n"
                                alertBlankLines == 1 -> "\n"
                                else -> " "
                            }
                    }
                    alertText += content
                    alertBlankLines = 0
                }
                continue
            }
            flushAlert()
        }

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
    flushAlert()
    return blocks
}

private fun alertKindOf(name: String): AlertKind =
    when (name.lowercase()) {
        "warning" -> AlertKind.Warning
        "caution" -> AlertKind.Caution
        else -> AlertKind.Important
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
