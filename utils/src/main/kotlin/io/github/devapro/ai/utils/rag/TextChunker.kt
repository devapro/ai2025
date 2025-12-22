package io.github.devapro.ai.utils.rag

import org.slf4j.LoggerFactory

/**
 * Data class representing a text chunk with metadata
 */
data class TextChunk(
    val text: String,
    val index: Int,
    val startPosition: Int,
    val endPosition: Int,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Component responsible for splitting text into chunks with overlap
 * Implements best practices for RAG text chunking with sentence-first strategy
 * Supports Markdown documents with code block preservation
 */
class TextChunker(
    private val chunkSize: Int = 500,  // target characters per chunk
    private val chunkOverlap: Int = 100  // overlap between chunks
) {
    private val logger = LoggerFactory.getLogger(TextChunker::class.java)
    private val markdownParser = MarkdownParser()

    // Sentence ending patterns (includes common abbreviations to avoid)
    private val sentenceEndingPattern = Regex("""[.!?]+(?=\s+[A-Z]|$)""")

    init {
        require(chunkSize > 0) { "Chunk size must be positive" }
        require(chunkOverlap >= 0) { "Chunk overlap must be non-negative" }
        require(chunkOverlap < chunkSize) { "Chunk overlap must be less than chunk size" }
    }

    /**
     * Split text into chunks with overlap using sentence-first strategy
     * @param text The text to split
     * @param metadata Optional metadata to attach to all chunks
     * @return List of text chunks
     */
    fun chunkText(text: String, metadata: Map<String, String> = emptyMap()): List<TextChunk> {
        if (text.isBlank()) {
            logger.warn("Empty text provided for chunking")
            return emptyList()
        }

        // Clean the text
        val cleanedText = cleanText(text)
        logger.info("Chunking text of length ${cleanedText.length} into chunks of ~$chunkSize characters (sentence-based)")

        // Split text into sentences
        val sentences = splitIntoSentences(cleanedText)
        logger.debug("Split text into ${sentences.size} sentences")

        // Group sentences into chunks
        val chunks = createChunksFromSentences(sentences, metadata)

        logger.info("Created ${chunks.size} chunks from text (sentence-based chunking)")
        return chunks
    }

    /**
     * Split Markdown text into chunks preserving structure
     * - Keeps code blocks intact (never splits them)
     * - Groups content by sections (headings)
     * - Preserves document structure
     * - Includes heading context in chunks
     *
     * @param markdown The Markdown text to split
     * @param metadata Optional metadata to attach to all chunks
     * @return List of text chunks
     */
    fun chunkMarkdown(markdown: String, metadata: Map<String, String> = emptyMap()): List<TextChunk> {
        if (markdown.isBlank()) {
            logger.warn("Empty markdown provided for chunking")
            return emptyList()
        }

        logger.info("Chunking markdown of length ${markdown.length} into chunks (structure-aware)")

        // Parse markdown into sections
        val sections = markdownParser.parseIntoSections(markdown)
        logger.debug("Parsed ${sections.size} sections from markdown")

        val chunks = mutableListOf<TextChunk>()
        var chunkIndex = 0

        sections.forEach { section ->
            val sectionChunks = chunkSection(section, chunkIndex, metadata)
            chunks.addAll(sectionChunks)
            chunkIndex += sectionChunks.size
        }

        logger.info("Created ${chunks.size} chunks from markdown (structure-aware chunking)")
        return chunks
    }

    /**
     * Chunk a single section from Markdown
     */
    private fun chunkSection(
        section: MarkdownSection,
        startIndex: Int,
        metadata: Map<String, String>
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        val headingText = section.heading?.text ?: ""
        val headingContext = if (headingText.isNotEmpty()) "$headingText\n\n" else ""

        // Try to fit entire section in one chunk
        val fullSectionText = section.getFullText()
        if (fullSectionText.length <= chunkSize) {
            chunks.add(TextChunk(
                text = fullSectionText,
                index = startIndex,
                startPosition = section.startPosition,
                endPosition = section.endPosition,
                metadata = metadata + ("heading" to headingText)
            ))
            return chunks
        }

        // Section is too large, need to split by elements
        val currentChunk = StringBuilder()
        if (headingContext.isNotEmpty()) {
            currentChunk.append(headingContext)
        }

        var currentStartPos = section.startPosition
        var chunkIdx = startIndex
        val elementsToProcess = section.elements.toMutableList()

        while (elementsToProcess.isNotEmpty()) {
            val element = elementsToProcess.removeAt(0)

            // Special handling for code blocks - never split them
            if (element is MarkdownElement.CodeBlock) {
                // If code block alone exceeds chunk size, make it its own chunk
                if (element.text.length > chunkSize) {
                    // Save current chunk if it has content
                    if (currentChunk.length > headingContext.length) {
                        chunks.add(createChunk(
                            text = currentChunk.toString().trim(),
                            index = chunkIdx++,
                            startPosition = currentStartPos,
                            endPosition = element.startPosition,
                            metadata = metadata + ("heading" to headingText)
                        ))
                        currentChunk.clear()
                        if (headingContext.isNotEmpty()) {
                            currentChunk.append(headingContext)
                        }
                    }

                    // Code block as its own chunk (with heading context)
                    chunks.add(createChunk(
                        text = headingContext + element.text,
                        index = chunkIdx++,
                        startPosition = element.startPosition,
                        endPosition = element.endPosition,
                        metadata = metadata + ("heading" to headingText) + ("type" to "code")
                    ))

                    currentStartPos = element.endPosition
                    continue
                }

                // Check if adding code block would exceed chunk size
                val potentialLength = currentChunk.length + element.text.length + 2
                if (potentialLength > chunkSize && currentChunk.length > headingContext.length) {
                    // Save current chunk
                    chunks.add(createChunk(
                        text = currentChunk.toString().trim(),
                        index = chunkIdx++,
                        startPosition = currentStartPos,
                        endPosition = element.startPosition,
                        metadata = metadata + ("heading" to headingText)
                    ))

                    // Start new chunk with heading and code block
                    currentChunk.clear()
                    if (headingContext.isNotEmpty()) {
                        currentChunk.append(headingContext)
                    }
                    currentChunk.append(element.text).append("\n\n")
                    currentStartPos = element.startPosition
                } else {
                    // Add code block to current chunk
                    currentChunk.append(element.text).append("\n\n")
                }
            }
            // Handle other elements (paragraphs, lists, etc.)
            else {
                val potentialLength = currentChunk.length + element.text.length + 2
                if (potentialLength > chunkSize && currentChunk.length > headingContext.length) {
                    // Save current chunk
                    chunks.add(createChunk(
                        text = currentChunk.toString().trim(),
                        index = chunkIdx++,
                        startPosition = currentStartPos,
                        endPosition = element.startPosition,
                        metadata = metadata + ("heading" to headingText)
                    ))

                    // Start new chunk with heading
                    currentChunk.clear()
                    if (headingContext.isNotEmpty()) {
                        currentChunk.append(headingContext)
                    }
                    currentStartPos = element.startPosition
                }

                // For large paragraphs, split by sentences
                if (element is MarkdownElement.Paragraph && element.text.length > chunkSize) {
                    val sentences = splitIntoSentences(element.text)
                    val sentenceChunks = createChunksFromSentences(sentences, metadata)

                    // Add heading context to first sentence chunk
                    if (sentenceChunks.isNotEmpty()) {
                        val first = sentenceChunks.first()
                        currentChunk.append(first.text).append("\n\n")

                        // Add remaining sentence chunks
                        sentenceChunks.drop(1).forEach { sentChunk ->
                            chunks.add(createChunk(
                                text = headingContext + sentChunk.text,
                                index = chunkIdx++,
                                startPosition = sentChunk.startPosition,
                                endPosition = sentChunk.endPosition,
                                metadata = metadata + ("heading" to headingText)
                            ))
                        }
                    }
                } else {
                    currentChunk.append(element.text).append("\n\n")
                }
            }
        }

        // Save final chunk if it has content
        if (currentChunk.length > headingContext.length) {
            chunks.add(createChunk(
                text = currentChunk.toString().trim(),
                index = chunkIdx,
                startPosition = currentStartPos,
                endPosition = section.endPosition,
                metadata = metadata + ("heading" to headingText)
            ))
        }

        return chunks
    }

    /**
     * Split text into sentences using regex pattern
     * Handles common sentence endings: . ! ?
     */
    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var lastEnd = 0

        sentenceEndingPattern.findAll(text).forEach { match ->
            val sentenceEnd = match.range.last + 1
            val sentence = text.substring(lastEnd, sentenceEnd).trim()
            if (sentence.isNotEmpty()) {
                sentences.add(sentence)
            }
            lastEnd = sentenceEnd
        }

        // Add remaining text as last sentence if any
        if (lastEnd < text.length) {
            val remaining = text.substring(lastEnd).trim()
            if (remaining.isNotEmpty()) {
                sentences.add(remaining)
            }
        }

        // If no sentences were found (no sentence endings), treat entire text as one sentence
        if (sentences.isEmpty() && text.isNotBlank()) {
            sentences.add(text.trim())
        }

        return sentences
    }

    /**
     * Create chunks from sentences, respecting chunk size and overlap
     * Primary strategy: Group sentences together
     * Fallback: Split large sentences by size
     */
    private fun createChunksFromSentences(
        sentences: List<String>,
        metadata: Map<String, String>
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var currentStartPosition = 0
        var absolutePosition = 0
        var chunkIndex = 0

        // Track sentences for overlap calculation
        val sentencesInCurrentChunk = mutableListOf<String>()

        for (sentenceIndex in sentences.indices) {
            val sentence = sentences[sentenceIndex]

            // Check if single sentence exceeds chunk size (needs fallback)
            if (sentence.length > chunkSize) {
                // Save current chunk if it has content
                if (currentChunk.isNotEmpty()) {
                    chunks.add(createChunk(
                        text = currentChunk.toString().trim(),
                        index = chunkIndex++,
                        startPosition = currentStartPosition,
                        endPosition = absolutePosition,
                        metadata = metadata
                    ))
                    currentChunk.clear()
                    sentencesInCurrentChunk.clear()
                }

                // Split large sentence by size (fallback strategy)
                logger.debug("Sentence ${sentenceIndex + 1} exceeds chunk size (${sentence.length} chars), using size-based fallback")
                val largeChunks = splitLargeSentenceBySize(
                    sentence = sentence,
                    startIndex = chunkIndex,
                    startPosition = absolutePosition,
                    metadata = metadata
                )
                chunks.addAll(largeChunks)
                chunkIndex += largeChunks.size

                absolutePosition += sentence.length
                currentStartPosition = absolutePosition
                continue
            }

            // Check if adding this sentence would exceed chunk size
            val potentialLength = currentChunk.length +
                (if (currentChunk.isEmpty()) 0 else 1) + // space separator
                sentence.length

            if (potentialLength > chunkSize && currentChunk.isNotEmpty()) {
                // Current chunk is full, save it
                val chunkText = currentChunk.toString().trim()
                chunks.add(createChunk(
                    text = chunkText,
                    index = chunkIndex++,
                    startPosition = currentStartPosition,
                    endPosition = absolutePosition,
                    metadata = metadata
                ))

                // Calculate overlap: include last N characters/sentences from previous chunk
                val overlapText = calculateOverlap(sentencesInCurrentChunk)
                currentChunk.clear()
                currentChunk.append(overlapText)

                // Update start position to account for overlap
                currentStartPosition = absolutePosition - overlapText.length
                sentencesInCurrentChunk.clear()

                // Add overlap sentences back to tracking
                if (overlapText.isNotEmpty()) {
                    val overlapSentences = splitIntoSentences(overlapText)
                    sentencesInCurrentChunk.addAll(overlapSentences)
                }
            }

            // Add sentence to current chunk
            if (currentChunk.isNotEmpty()) {
                currentChunk.append(" ")
            }
            currentChunk.append(sentence)
            sentencesInCurrentChunk.add(sentence)
            absolutePosition += sentence.length
        }

        // Add final chunk if it has content
        if (currentChunk.isNotEmpty()) {
            chunks.add(createChunk(
                text = currentChunk.toString().trim(),
                index = chunkIndex,
                startPosition = currentStartPosition,
                endPosition = absolutePosition,
                metadata = metadata
            ))
        }

        return chunks
    }

    /**
     * Calculate overlap text from previous chunk
     * Takes last sentences up to overlap size
     */
    private fun calculateOverlap(previousSentences: List<String>): String {
        if (previousSentences.isEmpty() || chunkOverlap == 0) {
            return ""
        }

        // Start from the end and work backwards
        val overlapBuilder = StringBuilder()
        for (i in previousSentences.size - 1 downTo 0) {
            val sentence = previousSentences[i]
            val potentialLength = overlapBuilder.length +
                (if (overlapBuilder.isEmpty()) 0 else 1) +
                sentence.length

            if (potentialLength <= chunkOverlap) {
                // Add sentence at the beginning
                if (overlapBuilder.isEmpty()) {
                    overlapBuilder.append(sentence)
                } else {
                    overlapBuilder.insert(0, "$sentence ")
                }
            } else {
                break
            }
        }

        return overlapBuilder.toString()
    }

    /**
     * Fallback: Split a large sentence by character size when it exceeds chunk size
     */
    private fun splitLargeSentenceBySize(
        sentence: String,
        startIndex: Int,
        startPosition: Int,
        metadata: Map<String, String>
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        var position = 0
        var chunkIdx = startIndex

        while (position < sentence.length) {
            val endPosition = minOf(position + chunkSize, sentence.length)
            var chunkText = sentence.substring(position, endPosition)

            // Try to end at word boundary if not the last piece
            if (endPosition < sentence.length) {
                chunkText = trimToWordBoundary(chunkText)
            }

            chunks.add(createChunk(
                text = chunkText.trim(),
                index = chunkIdx++,
                startPosition = startPosition + position,
                endPosition = startPosition + position + chunkText.length,
                metadata = metadata
            ))

            // Move position with overlap
            position += chunkText.length - chunkOverlap

            // Prevent infinite loop
            if (chunkText.length <= chunkOverlap) {
                position = endPosition
            }
        }

        return chunks
    }

    /**
     * Trim text to end at word boundary
     */
    private fun trimToWordBoundary(text: String): String {
        val lastSpaceIndex = text.lastIndexOf(' ')
        return if (lastSpaceIndex > text.length / 2) {
            text.substring(0, lastSpaceIndex)
        } else {
            text
        }
    }

    /**
     * Helper to create TextChunk instance
     */
    private fun createChunk(
        text: String,
        index: Int,
        startPosition: Int,
        endPosition: Int,
        metadata: Map<String, String>
    ): TextChunk {
        return TextChunk(
            text = text,
            index = index,
            startPosition = startPosition,
            endPosition = endPosition,
            metadata = metadata
        )
    }

    /**
     * Clean text by normalizing whitespace
     */
    private fun cleanText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
            .trim()
    }
}
