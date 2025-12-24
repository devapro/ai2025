package io.github.devapro.ai.utils.rag

import org.slf4j.LoggerFactory

/**
 * Represents different types of Markdown elements
 */
internal sealed class MarkdownElement {
    abstract val text: String
    abstract val startPosition: Int
    abstract val endPosition: Int

    data class Heading(
        override val text: String,
        override val startPosition: Int,
        override val endPosition: Int,
        val level: Int  // 1-6 for # to ######
    ) : MarkdownElement()

    data class CodeBlock(
        override val text: String,
        override val startPosition: Int,
        override val endPosition: Int,
        val language: String?
    ) : MarkdownElement()

    data class List(
        override val text: String,
        override val startPosition: Int,
        override val endPosition: Int,
        val ordered: Boolean
    ) : MarkdownElement()

    data class Paragraph(
        override val text: String,
        override val startPosition: Int,
        override val endPosition: Int
    ) : MarkdownElement()

    data class HorizontalRule(
        override val text: String,
        override val startPosition: Int,
        override val endPosition: Int
    ) : MarkdownElement()
}

/**
 * Represents a section with a heading and its content
 */
internal data class MarkdownSection(
    val heading: MarkdownElement.Heading?,
    val elements: List<MarkdownElement>,
    val startPosition: Int,
    val endPosition: Int
) {
    fun getFullText(): String {
        val builder = StringBuilder()
        if (heading != null) {
            builder.append(heading.text).append("\n\n")
        }
        elements.forEach { element ->
            builder.append(element.text).append("\n\n")
        }
        return builder.toString().trim()
    }
}

/**
 * Parser for Markdown documents
 * Identifies structural elements like headings, code blocks, lists, and paragraphs
 */
internal class MarkdownParser {
    private val logger = LoggerFactory.getLogger(MarkdownParser::class.java)

    /**
     * Parse Markdown text into structured elements
     */
    fun parse(text: String): List<MarkdownElement> {
        if (text.isBlank()) {
            return emptyList()
        }

        val elements = mutableListOf<MarkdownElement>()
        val lines = text.lines()
        var currentPosition = 0
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val lineStart = currentPosition

            when {
                // Code block start (```)
                line.trimStart().startsWith("```") -> {
                    val codeBlock = parseCodeBlock(lines, i, lineStart)
                    elements.add(codeBlock)
                    i += codeBlock.text.lines().size
                    currentPosition = codeBlock.endPosition
                }

                // Heading (# ## ### etc.)
                line.trimStart().startsWith("#") -> {
                    val heading = parseHeading(line, lineStart, lineStart + line.length)
                    elements.add(heading)
                    i++
                    currentPosition += line.length + 1  // +1 for newline
                }

                // Horizontal rule (---, ***, ___)
                isHorizontalRule(line) -> {
                    elements.add(MarkdownElement.HorizontalRule(
                        text = line,
                        startPosition = lineStart,
                        endPosition = lineStart + line.length
                    ))
                    i++
                    currentPosition += line.length + 1
                }

                // List item (-, *, +, or 1.)
                isListItem(line) -> {
                    val list = parseList(lines, i, lineStart)
                    elements.add(list)
                    i += list.text.lines().size
                    currentPosition = list.endPosition
                }

                // Empty line - skip
                line.isBlank() -> {
                    i++
                    currentPosition += line.length + 1
                }

                // Paragraph (default)
                else -> {
                    val paragraph = parseParagraph(lines, i, lineStart)
                    elements.add(paragraph)
                    i += paragraph.text.lines().size
                    currentPosition = paragraph.endPosition
                }
            }
        }

        logger.debug("Parsed ${elements.size} Markdown elements")
        return elements
    }

    /**
     * Group elements into sections based on headings
     */
    fun parseIntoSections(text: String): List<MarkdownSection> {
        val elements = parse(text)
        val sections = mutableListOf<MarkdownSection>()

        var currentHeading: MarkdownElement.Heading? = null
        var currentElements = mutableListOf<MarkdownElement>()
        var sectionStart = 0

        elements.forEach { element ->
            if (element is MarkdownElement.Heading) {
                // Save previous section if it has content
                if (currentElements.isNotEmpty() || currentHeading != null) {
                    sections.add(MarkdownSection(
                        heading = currentHeading,
                        elements = currentElements.toList(),
                        startPosition = sectionStart,
                        endPosition = element.startPosition
                    ))
                }

                // Start new section
                currentHeading = element
                currentElements = mutableListOf()
                sectionStart = element.startPosition
            } else {
                currentElements.add(element)
            }
        }

        // Add final section
        if (currentElements.isNotEmpty() || currentHeading != null) {
            val endPosition = currentElements.lastOrNull()?.endPosition
                ?: currentHeading?.endPosition
                ?: text.length
            sections.add(MarkdownSection(
                heading = currentHeading,
                elements = currentElements,
                startPosition = sectionStart,
                endPosition = endPosition
            ))
        }

        logger.debug("Created ${sections.size} sections from elements")
        return sections
    }

    private fun parseCodeBlock(lines: List<String>, startIndex: Int, startPosition: Int): MarkdownElement.CodeBlock {
        val firstLine = lines[startIndex].trimStart()
        val language = firstLine.substring(3).trim().takeIf { it.isNotEmpty() }

        val codeLines = mutableListOf(lines[startIndex])
        var endIndex = startIndex + 1

        // Find closing ```
        while (endIndex < lines.size) {
            codeLines.add(lines[endIndex])
            if (lines[endIndex].trimStart().startsWith("```")) {
                break
            }
            endIndex++
        }

        val text = codeLines.joinToString("\n")
        return MarkdownElement.CodeBlock(
            text = text,
            startPosition = startPosition,
            endPosition = startPosition + text.length,
            language = language
        )
    }

    private fun parseHeading(line: String, startPosition: Int, endPosition: Int): MarkdownElement.Heading {
        val trimmed = line.trimStart()
        var level = 0
        for (char in trimmed) {
            if (char == '#') level++ else break
        }
        level = level.coerceIn(1, 6)

        return MarkdownElement.Heading(
            text = line,
            startPosition = startPosition,
            endPosition = endPosition,
            level = level
        )
    }

    private fun parseList(lines: List<String>, startIndex: Int, startPosition: Int): MarkdownElement.List {
        val listLines = mutableListOf<String>()
        var i = startIndex
        val ordered = lines[startIndex].trimStart().matches(Regex("^\\d+\\.\\s.*"))

        // Collect consecutive list items
        while (i < lines.size && (isListItem(lines[i]) || lines[i].startsWith("  ") || lines[i].startsWith("\t"))) {
            if (lines[i].isBlank()) break
            listLines.add(lines[i])
            i++
        }

        val text = listLines.joinToString("\n")
        return MarkdownElement.List(
            text = text,
            startPosition = startPosition,
            endPosition = startPosition + text.length,
            ordered = ordered
        )
    }

    private fun parseParagraph(lines: List<String>, startIndex: Int, startPosition: Int): MarkdownElement.Paragraph {
        val paragraphLines = mutableListOf<String>()
        var i = startIndex

        // Collect consecutive non-empty lines that aren't other elements
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank() ||
                line.trimStart().startsWith("#") ||
                line.trimStart().startsWith("```") ||
                isListItem(line) ||
                isHorizontalRule(line)) {
                break
            }
            paragraphLines.add(line)
            i++
        }

        val text = paragraphLines.joinToString("\n")
        return MarkdownElement.Paragraph(
            text = text,
            startPosition = startPosition,
            endPosition = startPosition + text.length
        )
    }

    private fun isListItem(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.matches(Regex("^[-*+]\\s.*")) ||  // Unordered
               trimmed.matches(Regex("^\\d+\\.\\s.*"))   // Ordered
    }

    private fun isHorizontalRule(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.matches(Regex("^[-*_]{3,}$"))
    }
}
