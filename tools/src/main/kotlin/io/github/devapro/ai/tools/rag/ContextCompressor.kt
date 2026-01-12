package io.github.devapro.ai.tools.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Component for compressing retrieved context to save tokens
 * Extracts only query-relevant information from document chunks
 */
class ContextCompressor(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val tokenCounter: TokenCounter,
    private val model: String = "gpt-4o-mini"
) {
    private val logger = LoggerFactory.getLogger(ContextCompressor::class.java)

    /**
     * Compress multiple document chunks into focused, relevant content
     * @param query The user's query
     * @param chunks Document chunks to compress
     * @param maxTokens Maximum tokens for compressed output (default: 1500)
     * @return Compressed context containing only relevant information
     */
    suspend fun compressContext(
        query: String,
        chunks: List<String>,
        maxTokens: Int = 1500
    ): String {
        if (chunks.isEmpty()) return ""

        // Calculate input size
        val totalInputChars = chunks.sumOf { it.length }
        logger.info("Compressing ${chunks.size} chunks ($totalInputChars chars) for query: '$query'")

        try {
            val combined = chunks.joinToString("\n\n---\n\n")

            val prompt = """
                Query: "$query"

                I have ${chunks.size} document chunks that might contain relevant information. Extract and synthesize ONLY the information that helps answer the query.

                Document chunks:
                $combined

                Instructions:
                - Extract only facts, code examples, and explanations relevant to the query
                - Remove redundant information from overlapping chunks
                - Preserve important details like parameter names, return types, examples
                - Maintain technical accuracy
                - Keep response under $maxTokens tokens
                - If chunks aren't relevant, say "No relevant information found"

                Provide a focused summary that directly addresses the query.
            """.trimIndent()

            val compressed = callLLM(prompt, maxTokens = maxTokens)

            // Estimate compression ratio
            val outputChars = compressed.length
            val compressionRatio = if (totalInputChars > 0) {
                (outputChars.toDouble() / totalInputChars.toDouble() * 100).toInt()
            } else 0

            logger.info("Compressed to $outputChars chars (${compressionRatio}% of original)")

            return compressed
        } catch (e: Exception) {
            logger.error("Failed to compress context: ${e.message}", e)
            // Fallback: return chunks as-is but truncated
            return chunks.take(3).joinToString("\n\n---\n\n")
        }
    }

    /**
     * Compress a single chunk to extract only relevant excerpts
     * Faster than full compression, useful for per-chunk processing
     */
    suspend fun extractRelevantExcerpts(
        query: String,
        chunk: String,
        maxTokens: Int = 300
    ): String {
        try {
            val prompt = """
                Query: "$query"

                Document: "$chunk"

                Extract ONLY the sentences or code snippets that are relevant to the query.
                Keep the extracted text verbatim (don't paraphrase).
                If nothing is relevant, return "Not relevant".
                Maximum $maxTokens tokens.
            """.trimIndent()

            return callLLM(prompt, maxTokens = maxTokens)
        } catch (e: Exception) {
            logger.error("Failed to extract excerpts: ${e.message}")
            // Fallback: return truncated chunk
            return chunk.take(500)
        }
    }

    /**
     * Smart chunking: split if too long, compress if needed
     */
    suspend fun smartCompress(
        query: String,
        chunks: List<String>,
        targetTokens: Int = 1000
    ): String {
        // Count current tokens
        val messages = chunks.map { SimpleMessage(role = "user", content = it) }
        val currentTokens = tokenCounter.countTokens(messages)

        logger.info("Input: $currentTokens tokens, target: $targetTokens tokens")

        return if (currentTokens <= targetTokens) {
            // Already under limit, no compression needed
            logger.info("No compression needed")
            chunks.joinToString("\n\n---\n\n")
        } else {
            // Compress to fit target
            logger.info("Compressing from $currentTokens to ~$targetTokens tokens")
            compressContext(query, chunks, maxTokens = targetTokens)
        }
    }

    /**
     * Call OpenAI API
     */
    private suspend fun callLLM(prompt: String, maxTokens: Int): String {
        val request = LLMRequest(
            model = model,
            messages = listOf(
                LLMMessage(
                    role = "system",
                    content = "You are an expert at extracting and condensing relevant information. Be concise and accurate."
                ),
                LLMMessage(role = "user", content = prompt)
            ),
            temperature = 0.0, // Deterministic for compression
            max_tokens = maxTokens
        )

        val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<LLMResponse>()

        return response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("No response from LLM")
    }

    @Serializable
    private data class LLMRequest(
        val model: String,
        val messages: List<LLMMessage>,
        val temperature: Double,
        val max_tokens: Int
    )

    @Serializable
    private data class LLMMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class LLMResponse(
        val choices: List<LLMChoice>
    )

    @Serializable
    private data class LLMChoice(
        val message: LLMMessage
    )
}
