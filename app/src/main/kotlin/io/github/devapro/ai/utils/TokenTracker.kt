package io.github.devapro.ai.utils

import com.aallam.ktoken.Tokenizer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Utility class for tracking token usage across LLM interactions
 *
 * This class:
 * - Counts tokens using ktoken (BPE tokenizer for OpenAI models)
 * - Tracks input and output tokens separately
 * - Accumulates tokens across multiple LLM calls (main conversation + tool calls + summarization)
 * - Provides total token count for cost tracking
 */
class TokenTracker(
    private val modelName: String = "gpt-4o"
) {
    private val logger = LoggerFactory.getLogger(TokenTracker::class.java)

    // Lazy initialization of tokenizer (requires suspend function)
    private val tokenizer: Tokenizer by lazy {
        runBlocking {
            Tokenizer.of(model = modelName)
        }
    }

    private var totalInputTokens: Int = 0
    private var totalOutputTokens: Int = 0

    /**
     * Count tokens in a text string
     */
    fun countTokens(text: String): Int {
        return try {
            tokenizer.encode(text).size
        } catch (e: Exception) {
            logger.error("Error counting tokens: ${e.message}", e)
            // Rough estimation: 1 token ≈ 4 characters for English text
            (text.length / 4).coerceAtLeast(1)
        }
    }

    /**
     * Track input tokens (prompt, history, system message, etc.)
     */
    fun trackInput(text: String) {
        val tokens = countTokens(text)
        totalInputTokens += tokens
        logger.debug("Tracked $tokens input tokens")
    }

    /**
     * Track output tokens (model response)
     */
    fun trackOutput(text: String) {
        val tokens = countTokens(text)
        totalOutputTokens += tokens
        logger.debug("Tracked $tokens output tokens")
    }

    /**
     * Get total input tokens
     */
    fun getInputTokens(): Int = totalInputTokens

    /**
     * Get total output tokens
     */
    fun getOutputTokens(): Int = totalOutputTokens

    /**
     * Get total tokens (input + output)
     */
    fun getTotalTokens(): Int = totalInputTokens + totalOutputTokens

    /**
     * Reset token counters
     */
    fun reset() {
        totalInputTokens = 0
        totalOutputTokens = 0
    }

    /**
     * Get token usage summary
     */
    fun getSummary(): TokenUsage {
        return TokenUsage(
            inputTokens = totalInputTokens,
            outputTokens = totalOutputTokens,
            totalTokens = getTotalTokens()
        )
    }
}

/**
 * Data class for token usage information
 */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
) {
    override fun toString(): String {
        return "Input: $inputTokens | Output: $outputTokens | Total: $totalTokens"
    }
}
