package io.github.devapro.ai.embeds.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {

    @Test
    fun testSentenceBasedChunking() {
        val chunker = TextChunker(chunkSize = 100, chunkOverlap = 20)

        val text = """
            This is the first sentence. This is the second sentence. This is the third sentence.
            This is the fourth sentence. This is the fifth sentence.
        """.trimIndent()

        val chunks = chunker.chunkText(text)

        // Should have multiple chunks due to sentence grouping
        assertTrue(chunks.isNotEmpty(), "Should create at least one chunk")

        // Each chunk should end with a complete sentence (period)
        chunks.dropLast(1).forEach { chunk ->
            assertTrue(
                chunk.text.endsWith(".") || chunk.text.endsWith("!") || chunk.text.endsWith("?"),
                "Chunk should end with sentence boundary: ${chunk.text}"
            )
        }

        // Chunks should have sequential indices
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.index, "Chunk indices should be sequential")
        }
    }

    @Test
    fun testOverlapBetweenChunks() {
        val chunker = TextChunker(chunkSize = 50, chunkOverlap = 20)

        val text = "Short sentence one. Short sentence two. Short sentence three. Short sentence four."

        val chunks = chunker.chunkText(text)

        // Should have multiple chunks
        assertTrue(chunks.size > 1, "Should create multiple chunks for overlap testing")

        // Check that consecutive chunks have overlap
        for (i in 0 until chunks.size - 1) {
            val currentText = chunks[i].text
            val nextText = chunks[i + 1].text

            // Find common text between chunks
            val hasOverlap = nextText.split(" ").any { word ->
                word.isNotEmpty() && currentText.contains(word)
            }

            assertTrue(hasOverlap || chunks[i].text.length < 20,
                "Consecutive chunks should have overlap: \nChunk $i: $currentText\nChunk ${i+1}: $nextText")
        }
    }

    @Test
    fun testLargeSentenceFallback() {
        val chunker = TextChunker(chunkSize = 50, chunkOverlap = 10)

        // Create a very long sentence without periods
        val longSentence = "word ".repeat(30) + "end"

        val chunks = chunker.chunkText(longSentence)

        // Should create multiple chunks using size-based fallback
        assertTrue(chunks.size > 1, "Should split large sentence into multiple chunks")

        // Each chunk should be around chunk size
        chunks.forEach { chunk ->
            assertTrue(
                chunk.text.length <= 60, // Allow some tolerance
                "Chunk size should be reasonable: ${chunk.text.length}"
            )
        }
    }

    @Test
    fun testEmptyText() {
        val chunker = TextChunker()
        val chunks = chunker.chunkText("")
        assertTrue(chunks.isEmpty(), "Empty text should produce no chunks")
    }

    @Test
    fun testSingleSentence() {
        val chunker = TextChunker(chunkSize = 100, chunkOverlap = 20)
        val text = "This is a single short sentence."

        val chunks = chunker.chunkText(text)

        assertEquals(1, chunks.size, "Single short sentence should create one chunk")
        assertEquals(text, chunks.first().text, "Chunk should contain the entire sentence")
    }

    @Test
    fun testMultipleSentenceEndings() {
        val chunker = TextChunker(chunkSize = 100, chunkOverlap = 20)

        val text = "Question? Exclamation! Statement. Another statement."

        val chunks = chunker.chunkText(text)

        // Should handle different sentence endings
        assertTrue(chunks.isNotEmpty(), "Should create chunks")
        chunks.forEach { chunk ->
            assertTrue(
                chunk.text.contains("?") || chunk.text.contains("!") || chunk.text.contains("."),
                "Chunks should contain sentence endings"
            )
        }
    }

    @Test
    fun testMetadataPreservation() {
        val chunker = TextChunker(chunkSize = 50, chunkOverlap = 10)
        val metadata = mapOf("source" to "test", "author" to "tester")

        val text = "First sentence. Second sentence. Third sentence."

        val chunks = chunker.chunkText(text, metadata)

        // All chunks should have the same metadata
        chunks.forEach { chunk ->
            assertEquals(metadata, chunk.metadata, "Metadata should be preserved")
        }
    }

    @Test
    fun testPositionTracking() {
        val chunker = TextChunker(chunkSize = 50, chunkOverlap = 10)

        val text = "First sentence. Second sentence. Third sentence."

        val chunks = chunker.chunkText(text)

        // Positions should be non-negative and increasing
        var lastEnd = 0
        chunks.forEach { chunk ->
            assertTrue(chunk.startPosition >= 0, "Start position should be non-negative")
            assertTrue(chunk.endPosition > chunk.startPosition, "End should be after start")
            // Allow for overlap in position tracking
            assertTrue(chunk.startPosition >= lastEnd - 15,
                "Positions should progress (with possible overlap)")
            lastEnd = chunk.endPosition
        }
    }
}
