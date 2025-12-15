package io.github.devapro.ai.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.agents.core.tools.ToolRegistry
import io.github.devapro.ai.repository.FileRepository
import org.slf4j.LoggerFactory

/**
 * AI Agent component that handles conversations using OpenAI API with MCP tool support via Koog
 */
class AiAgent(
    private val apiKey: String,
    private val fileRepository: FileRepository,
    toolRegistry: ToolRegistry?
) {
    private val logger = LoggerFactory.getLogger(AiAgent::class.java)

    // Get system prompt once during initialization
    private val systemPrompt = fileRepository.getSystemPrompt()

    // Koog agent with OpenAI and optional MCP tools
    private val agent = if (toolRegistry != null) {
        AIAgent(
            promptExecutor = simpleOpenAIExecutor(apiKey),
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4o,
            toolRegistry = toolRegistry
        )
    } else {
        AIAgent(
            promptExecutor = simpleOpenAIExecutor(apiKey),
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4o
        )
    }

    /**
     * Process user message and generate response with MCP tool support
     * @param userId Telegram user ID
     * @param userMessage User's message
     * @return Assistant's response
     */
    suspend fun processMessage(userId: Long, userMessage: String): String {
        try {
            // Get conversation history
            val history = fileRepository.getUserHistory(userId)

            // Build conversation context for Koog
            val conversationContext = buildString {
                // Add history context if present
                if (history.isNotEmpty()) {
                    append("Previous conversation:\n")
                    history.forEach { msg ->
                        append("${msg.role}: ${msg.content}\n")
                    }
                    append("\n")
                }
                append("Current question: $userMessage")
            }

            // Measure response time
            val startTime = System.currentTimeMillis()

            // Call Koog agent (handles tool calling, retries, etc. internally)
            val response = agent.run(conversationContext)

            val responseTime = System.currentTimeMillis() - startTime

            logger.info("Response generated in ${responseTime}ms")

            // Format response with statistics
            val formattedResponse = buildString {
                append(response)
                append("\n\n---\n\n")
                append("📊 *Response time:* ${responseTime}ms")
            }

            // Save to history
            fileRepository.saveUserMessage(userId, userMessage)
            fileRepository.saveAssistantMessage(userId, formattedResponse)

            // Check if we need to summarize history (every 10 messages)
            val newHistoryLength = history.size + 2
            if (newHistoryLength >= 10 && newHistoryLength % 10 == 0) {
                logger.info("History length reached $newHistoryLength, creating summary...")
                summarizeAndReplaceHistory(userId)
            }

            return formattedResponse

        } catch (e: Exception) {
            logger.error("Error processing message: ${e.message}", e)
            return "Sorry, I encountered an error processing your message. Please try again."
        }
    }

    /**
     * Clear conversation history for a user
     */
    fun clearHistory(userId: Long) {
        fileRepository.clearUserHistory(userId)
    }

    /**
     * Summarize conversation history and replace it with the summary
     */
    private suspend fun summarizeAndReplaceHistory(userId: Long) {
        try {
            val history = fileRepository.getUserHistory(userId)
            if (history.isEmpty()) return

            // Build conversation text
            val conversationText = buildString {
                history.forEach { msg ->
                    append("${msg.role.uppercase()}: ${msg.content}\n\n")
                }
            }

            // Create summarization prompt
            val summaryPrompt = """
                Please create a concise summary of the following conversation.
                Focus on key topics discussed, important information exchanged, and any decisions or conclusions reached.
                Keep the summary brief but informative.

                Conversation:
                $conversationText
            """.trimIndent()

            // Use separate agent for summarization with custom system prompt
            val summaryAgent = AIAgent(
                promptExecutor = simpleOpenAIExecutor(apiKey),
                systemPrompt = "You are a helpful assistant that summarizes conversations.",
                llmModel = OpenAIModels.Chat.GPT4o
            )

            val summary = summaryAgent.run(summaryPrompt).trim()

            if (summary.isNotBlank()) {
                logger.info("Summary created successfully, replacing history")
                fileRepository.clearUserHistory(userId)
                fileRepository.saveSystemMessage(userId, "Conversation summary: $summary")
                logger.info("History replaced with summary")
            }
        } catch (e: Exception) {
            logger.error("Failed to summarize history: ${e.message}", e)
        }
    }

    fun close() {
        // Koog agents don't require explicit cleanup
    }
}
