package io.github.devapro.ai.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.agents.core.tools.ToolRegistry
import io.github.devapro.ai.history.HistoryManager
import io.github.devapro.ai.mcp.McpInitializer
import io.github.devapro.ai.repository.FileRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * AI Agent component that handles conversations using OpenAI API with MCP tool support via Koog
 *
 * This component:
 * - Manages AI conversations with context from history
 * - Integrates with MCP tools for extended capabilities
 * - Delegates history lifecycle management to HistoryManager
 * - Formats responses with performance metrics
 */
class AiAgent(
    private val apiKey: String,
    private val fileRepository: FileRepository,
    private val historyManager: HistoryManager,
    private val mcpInitializer: McpInitializer
) {
    private val logger = LoggerFactory.getLogger(AiAgent::class.java)

    // Get system prompt once during initialization
    private val systemPrompt = fileRepository.getSystemPrompt()

    // Tool registry - initialized in init block
    // Note: Agent is created per message (Koog agents are single-use)
    private val toolRegistry: ToolRegistry?

    init {
        logger.info("Initializing AI Agent...")

        // Initialize MCP tool registry (blocking call for suspend function)
        toolRegistry = runBlocking {
            logger.debug("Initializing MCP tool registry...")
            mcpInitializer.initialize()
        }

        if (toolRegistry != null) {
            logger.info("MCP tools initialized successfully")
        } else {
            logger.info("No MCP tools available")
        }

        logger.info("AI Agent initialized successfully")
    }

    /**
     * Create a new Koog agent for processing a message
     * Note: Koog agents are single-use and must be created per request
     */
    private fun createAgent(): AIAgent<String, String> {
        return if (toolRegistry != null) {
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

            // Create a new agent for this message (Koog agents are single-use)
            val agent = createAgent()

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

            // Check if we need to summarize history (delegated to HistoryManager)
            val newHistoryLength = history.size + 2
            historyManager.checkAndSummarizeIfNeeded(userId, newHistoryLength)

            return formattedResponse

        } catch (e: Exception) {
            logger.error("Error processing message: ${e.message}", e)
            return "Sorry, I encountered an error processing your message. Please try again."
        }
    }

    /**
     * Clear conversation history for a user (delegated to HistoryManager)
     */
    fun clearHistory(userId: Long) {
        historyManager.clearHistory(userId)
    }
}
