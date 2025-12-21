package io.github.devapro.ai.agent

import kotlinx.serialization.json.Json

/**
 * Component responsible for parsing and formatting AI responses
 * for display to users with statistics and markdown formatting
 */
class AiAgentResponseFormatter(
    private val modelName: String = "gpt-4o-mini"
) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse JSON response from AI and format it for display
     */
    fun parseAndFormatResponse(
        rawResponse: String,
        usage: TokenUsage?,
        responseTime: Long,
        estimatedTokens: Int,
        historyLength: Int
    ): String {
        // Try to extract JSON from potential markdown code block
        val jsonContent = if (rawResponse.contains("```json")) {
            rawResponse
                .substringAfter("```json")
                .substringBefore("```")
                .trim()
        } else if (rawResponse.contains("```")) {
            rawResponse
                .substringAfter("```")
                .substringBefore("```")
                .trim()
        } else {
            rawResponse.trim()
        }

        // Parse the JSON
        val aiResponse = jsonParser.decodeFromString<AiResponse>(jsonContent)

        // Handle empty response
        if (aiResponse.text.isNullOrBlank()) {
            return "I'm here to help! Please ask me a question and I'll do my best to provide a helpful answer."
        }

        // Format the response
        return formatResponse(aiResponse, usage, responseTime, estimatedTokens, historyLength)
    }

    /**
     * Format AI response with statistics
     */
    private fun formatResponse(
        aiResponse: AiResponse,
        usage: TokenUsage?,
        responseTime: Long,
        estimatedTokens: Int,
        historyLength: Int
    ): String {
        return buildString {
            // Main answer text
            append(aiResponse.text ?: "")
            append("\n\n")

            // Summary if present
            if (!aiResponse.summary.isNullOrBlank()) {
                append("💡 _${aiResponse.summary}_\n\n")
            }

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
