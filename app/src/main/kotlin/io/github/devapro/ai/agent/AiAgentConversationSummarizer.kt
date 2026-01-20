package io.github.devapro.ai.agent

import io.github.devapro.ai.repository.FileRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

/**
 * Component responsible for summarizing conversation history
 * to prevent context overflow and reduce token usage
 */
class AiAgentConversationSummarizer(
    private val apiKey: String,
    private val apiUrl: String,
    private val modelName: String,
    private val fileRepository: FileRepository,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(AiAgentConversationSummarizer::class.java)

    /**
     * Summarize conversation history and replace it with the summary
     * @param userId The user ID whose history should be summarized
     */
    suspend fun summarizeAndReplaceHistory(userId: Long) {
        try {
            // Get current history
            val history = fileRepository.getUserHistory(userId)
            if (history.isEmpty()) return

            // Build conversation text for summarization
            val conversationText = buildString {
                history.forEach { msg ->
                    append("${msg.role.uppercase()}: ${msg.content}\n\n")
                }
            }

            // Create summarization request
            val summaryPrompt = """
                Please create a concise summary of the following conversation.
                Focus on key topics discussed, important information exchanged, and any decisions or conclusions reached.
                Keep the summary brief but informative.

                Conversation:
                $conversationText

                Provide the summary in plain text format (not JSON).
            """.trimIndent()

            val summaryMessages = listOf(
                OpenAIMessage(role = "user", content = summaryPrompt)
            )

            val summaryRequest = OpenAIRequest(
                model = modelName,
                messages = summaryMessages,
                temperature = 0.7,
                stream = false
            )

            // Call API to get summary
            val response = httpClient.post(apiUrl) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(summaryRequest)
            }.body<OpenAIResponse>()

            val summary = response.choices.firstOrNull()?.message?.content?.trim()

            if (!summary.isNullOrBlank()) {
                logger.info("Summary created successfully, replacing history")

                // Clear current history
                fileRepository.clearUserHistory(userId)

                // Save summary as a system message
                fileRepository.saveSystemMessage(userId, "Conversation summary: $summary")

                logger.info("History replaced with summary")
            }
        } catch (e: Exception) {
            logger.error("Failed to summarize history: ${e.message}", e)
            // Continue without summarization on error
        }
    }
}
