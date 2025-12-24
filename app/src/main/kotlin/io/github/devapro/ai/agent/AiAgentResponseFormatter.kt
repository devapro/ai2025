package io.github.devapro.ai.agent

/**
 * Component responsible for formatting AI responses
 * Appends statistics and metadata to plain text responses
 */
class AiAgentResponseFormatter(
    private val modelName: String = "gpt-4o-mini"
) {
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
        // Handle empty response
        if (rawResponse.isBlank()) {
            return "I'm here to help! Please ask me a question and I'll do my best to provide a helpful answer."
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
