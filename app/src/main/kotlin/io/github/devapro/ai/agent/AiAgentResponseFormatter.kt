package io.github.devapro.ai.agent

/**
 * Component responsible for formatting AI responses
 * Appends statistics and metadata to plain text responses
 */
class AiAgentResponseFormatter(
    private val modelName: String = "gpt-4o-mini"
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(AiAgentResponseFormatter::class.java)

    /**
     * Format plain text response with statistics
     */
    fun formatResponse(
        rawResponse: String,
        usage: TokenUsage?,
        responseTime: Long,
        estimatedTokens: Int,
        historyLength: Int
    ): String {
        logger.info("formatResponse called: responseTime=${responseTime}ms, tokens=${usage?.totalTokens}")
        logger.debug("Raw response length: ${rawResponse.length} chars")

        // Check if response already contains statistics (potential double-formatting bug)
        if (rawResponse.contains("\n\n---\n\n") && rawResponse.contains("📊")) {
            logger.warn("⚠️  Raw response already contains statistics block! This indicates double-formatting bug.")
            logger.warn("Response preview: ${rawResponse.takeLast(200)}")
        }

        // Handle empty response
        if (rawResponse.isBlank()) {
            return "I'm here to help! Please ask me a question and I'll do my best to provide a helpful answer."
        }

        // SAFETY CHECK: If response already has statistics, don't add more
        // This prevents double-formatting bug
        if (rawResponse.contains("\n\n---\n\n") && rawResponse.contains("📊")) {
            logger.error("Preventing double-formatting! Returning raw response as-is.")
            return rawResponse
        }

        return buildString {
            // Main answer text
            append(rawResponse.trim())
            append("\n\n")

            // Statistics separator
            append("---\n\n")

            // Statistics
            append("📊 *${modelName}:*\n")
            append("• Response time: ${responseTime}ms\n")
            append("• History messages: ${historyLength}\n")
            if (usage != null) {
                append("• Estimated incoming tokens: ${estimatedTokens}\n")
                append("• Tokens used: ${usage.totalTokens} (prompt: ${usage.promptTokens}, completion: ${usage.completionTokens})")
            }
        }.trim()
    }
}
