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
 * Implements best practices for RAG text chunking
 */
class TextChunker(
    private val chunkSize: Int = 500,  // characters per chunk
    private val chunkOverlap: Int = 100  // overlap between chunks
) {
    private val logger = LoggerFactory.getLogger(TextChunker::class.java)

    init {
        require(chunkSize > 0) { "Chunk size must be positive" }
        require(chunkOverlap >= 0) { "Chunk overlap must be non-negative" }
        require(chunkOverlap < chunkSize) { "Chunk overlap must be less than chunk size" }
    }

    /**
     * Split text into chunks with overlap
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
        logger.info("Chunking text of length ${cleanedText.length} into chunks of size $chunkSize with overlap $chunkOverlap")

        val chunks = mutableListOf<TextChunk>()
        var startPosition = 0
        var chunkIndex = 0

        while (startPosition < cleanedText.length) {
            // Calculate end position
            val endPosition = minOf(startPosition + chunkSize, cleanedText.length)

            // Extract chunk text
            var chunkText = cleanedText.substring(startPosition, endPosition)

            // Try to end at sentence boundary if not the last chunk
            if (endPosition < cleanedText.length) {
                chunkText = trimToSentenceBoundary(chunkText)
            }

            // Create chunk
            chunks.add(
                TextChunk(
                    text = chunkText.trim(),
                    index = chunkIndex,
                    startPosition = startPosition,
                    endPosition = startPosition + chunkText.length,
                    metadata = metadata
                )
            )

            // Move to next position with overlap
            startPosition += chunkText.length - chunkOverlap
            chunkIndex++

            // Prevent infinite loop if chunk is too small
            if (chunkText.length < chunkOverlap) {
                break
            }
        }

        logger.info("Created ${chunks.size} chunks from text")
        return chunks
    }

    /**
     * Clean text by normalizing whitespace and removing special characters
     */
    private fun cleanText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
            .trim()
    }

    /**
     * Trim text to end at sentence boundary (period, question mark, exclamation mark)
     * Falls back to word boundary if no sentence boundary found
     */
    private fun trimToSentenceBoundary(text: String): String {
        // Try to find sentence ending
        val sentenceEndings = listOf('.', '!', '?')
        for (i in text.length - 1 downTo maxOf(0, text.length - 100)) {
            if (text[i] in sentenceEndings) {
                return text.substring(0, i + 1)
            }
        }

        // Fall back to word boundary
        val lastSpaceIndex = text.lastIndexOf(' ')
        if (lastSpaceIndex > text.length / 2) {
            return text.substring(0, lastSpaceIndex)
        }

        // Return as is if no good boundary found
        return text
    }
}
