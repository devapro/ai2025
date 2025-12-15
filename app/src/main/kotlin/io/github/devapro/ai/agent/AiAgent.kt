package io.github.devapro.ai.agent

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import io.github.devapro.ai.mcp.McpManager
import io.github.devapro.ai.mcp.model.ToolContent
import io.github.devapro.ai.repository.FileRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * AI Agent component that handles conversations using OpenAI API with MCP tool support
 */

private const val modelName = "gpt-4o-mini"
private const val MAX_TOOL_ITERATIONS = 5

class AiAgent(
    private val apiKey: String,
    private val fileRepository: FileRepository,
    private val mcpManager: McpManager,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(AiAgent::class.java)

    // Token counting with jtokkit
    private val encodingRegistry: EncodingRegistry = Encodings.newDefaultEncodingRegistry()
    private val encoding: Encoding = encodingRegistry.getEncoding(EncodingType.O200K_BASE)

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Process user message and generate response with MCP tool support
     * @param userId Telegram user ID
     * @param userMessage User's message
     * @return Assistant's response
     */
    suspend fun processMessage(userId: Long, userMessage: String): String {
        // Get system prompt
        val systemPrompt = fileRepository.getSystemPrompt()

        // Get conversation history
        val history = fileRepository.getUserHistory(userId)
        val historyLength = history.size

        // Build initial messages list
        val messages = mutableListOf<OpenAIMessage>()
        messages.add(OpenAIMessage(role = "system", content = systemPrompt))

        // Add conversation history
        history.forEach { msg ->
            messages.add(OpenAIMessage(role = msg.role, content = msg.content))
        }

        // Add current user message
        messages.add(OpenAIMessage(role = "user", content = userMessage))

        // Get available tools from MCP manager
        val availableTools = if (mcpManager.isAvailable()) {
            try {
                val mcpTools = mcpManager.getAllTools()
                logger.info("Found ${mcpTools.size} MCP tools available")
                mcpTools.map { mcpTool ->
                    OpenAITool(
                        function = OpenAIFunction(
                            name = mcpTool.name,
                            description = mcpTool.description,
                            parameters = mcpTool.inputSchema
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("Error fetching MCP tools: ${e.message}", e)
                emptyList()
            }
        } else {
            logger.debug("No MCP tools available")
            emptyList()
        }

        // Measure total response time
        val startTime = System.currentTimeMillis()
        var iteration = 0

        // Tool calling loop
        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++
            logger.info("Tool calling iteration $iteration")

            // Count estimated tokens
            val estimatedTokens = countTokens(messages)
            logger.info("Estimated prompt tokens: $estimatedTokens")

            // Create request
            val request = OpenAIRequest(
                model = modelName,
                messages = messages,
                temperature = 0.9,
                stream = false,
                tools = availableTools.ifEmpty { null }
            )

            // Call OpenAI API
            val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<OpenAIResponse>()

            val choice = response.choices.firstOrNull()
            if (choice == null) {
                logger.error("No choices in OpenAI response")
                return "Sorry, I couldn't generate a response."
            }

            val message = choice.message
            val finishReason = choice.finishReason
            val usage = response.usage

            logger.info("Finish reason: $finishReason")

            // Handle based on finish reason
            when (finishReason) {
                "tool_calls" -> {
                    // AI wants to call tools
                    val toolCalls = message.toolCalls
                    if (toolCalls.isNullOrEmpty()) {
                        logger.error("Finish reason is tool_calls but no tool calls present")
                        return "Sorry, there was an error processing tool calls."
                    }

                    logger.info("AI requested ${toolCalls.size} tool call(s)")

                    // Add assistant message with tool calls to history
                    messages.add(message)

                    // Execute each tool call
                    toolCalls.forEach { toolCall ->
                        val toolName = toolCall.function.name
                        val toolArgs = toolCall.function.arguments

                        logger.info("Executing tool: $toolName")
                        logger.debug("Tool arguments: $toolArgs")

                        // Parse arguments from JSON string
                        val argsObject = try {
                            if (toolArgs.isBlank()) {
                                null
                            } else {
                                Json.parseToJsonElement(toolArgs).jsonObject
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to parse tool arguments: ${e.message}", e)
                            null
                        }

                        // Call tool via MCP manager
                        val toolResult = mcpManager.callTool(toolName, argsObject)

                        // Format tool result as text content
                        val resultText = toolResult.content.joinToString("\n\n") { content ->
                            when (content.type) {
                                "text" -> content.text ?: ""
                                "image" -> "[Image: ${content.mimeType}]"
                                "resource" -> "[Resource: ${content.uri}]"
                                else -> "[Unknown content type: ${content.type}]"
                            }
                        }

                        logger.info("Tool $toolName result: ${resultText.take(200)}...")

                        // Add tool result message
                        messages.add(OpenAIMessage(
                            role = "tool",
                            content = resultText,
                            toolCallId = toolCall.id
                        ))
                    }

                    // Continue loop to get final response
                    continue
                }

                "stop" -> {
                    // AI has finished, return the response
                    val responseTime = System.currentTimeMillis() - startTime
                    val rawResponse = message.content ?: "Sorry, I couldn't generate a response."

                    logger.info("Final response received after $iteration iteration(s)")
                    logger.info(rawResponse)
                    logger.info("Total response time: ${responseTime}ms")

                    // Parse and format response
                    val formattedResponse = try {
                        parseAndFormatResponse(rawResponse, usage, responseTime, estimatedTokens, historyLength)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse AI response as JSON, using raw response: ${e.message}")
                        rawResponse
                    }

                    // Save messages to history
                    fileRepository.saveUserMessage(userId, userMessage)
                    fileRepository.saveAssistantMessage(userId, formattedResponse)

                    // Check if we need to summarize history (every 5 messages)
                    val newHistoryLength = historyLength + 2
                    if (newHistoryLength >= 5 && newHistoryLength % 5 == 0) {
                        logger.info("History length reached $newHistoryLength, creating summary...")
                        summarizeAndReplaceHistory(userId)
                    }

                    return formattedResponse
                }

                "length" -> {
                    logger.warn("Response truncated due to length limit")
                    return "Sorry, the response was too long. Please try asking a more specific question."
                }

                else -> {
                    logger.warn("Unexpected finish reason: $finishReason")
                    val content = message.content ?: "Sorry, I couldn't generate a response."
                    fileRepository.saveUserMessage(userId, userMessage)
                    fileRepository.saveAssistantMessage(userId, content)
                    return content
                }
            }
        }

        // Max iterations exceeded
        logger.error("Max tool calling iterations ($MAX_TOOL_ITERATIONS) exceeded")
        return "Sorry, I encountered too many tool calls and had to stop. Please try rephrasing your question."
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
            val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
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

    /**
     * Count tokens in messages before sending request
     * Uses jtokkit to estimate token count
     */
    private fun countTokens(messages: List<OpenAIMessage>): Int {
        var totalTokens = 0

        // Count tokens for each message
        messages.forEach { message ->
            // Count tokens for role
            totalTokens += encoding.countTokens(message.role)
            // Count tokens for content (if present)
            message.content?.let {
                totalTokens += encoding.countTokens(it)
            }
            // Add overhead per message (typically 3-4 tokens per message for formatting)
            totalTokens += 4
        }

        // Add overhead for reply priming (typically 3 tokens)
        totalTokens += 3

        return totalTokens
    }

    /**
     * Parse JSON response from AI and format it for display
     */
    private fun parseAndFormatResponse(
        rawResponse: String,
        usage: TokenUsage?,
        responseTime: Long,
        estimatedTokes: Int,
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
        return formatResponse(aiResponse, usage, responseTime, estimatedTokes, historyLength)
    }

    /**
     * Format AI response with statistics
     */
    private fun formatResponse(
        aiResponse: AiResponse,
        usage: TokenUsage?,
        responseTime: Long,
        estimatedTokes: Int,
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
                append("• Estimated incoming tokes: ${estimatedTokes}\n")
                append("• Tokens used: ${usage.totalTokens} (prompt: ${usage.promptTokens}, completion: ${usage.completionTokens})")
            }
        }.trim()
    }

    fun close() {
        // HTTP client is shared and managed by DI
        // Don't close it here
    }
}

/**
 * AI response structure from JSON
 */
@Serializable
data class AiResponse(
    @SerialName("type")
    val type: String? = "answer",
    @SerialName("text")
    val text: String? = null,
    @SerialName("summary")
    val summary: String? = null
)

@Serializable
data class OpenAIMessage(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: String? = null,  // Nullable for tool call messages
    @SerialName("tool_calls")
    val toolCalls: List<OpenAIToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
data class OpenAIRequest(
    @SerialName("model")
    val model: String,
    @SerialName("messages")
    val messages: List<OpenAIMessage>,
    @SerialName("temperature")
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
    @SerialName("stream")
    val stream: Boolean = false,
    @SerialName("tools")
    val tools: List<OpenAITool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null
)

@Serializable
data class ResponseFormat(
    @SerialName("type")
    val type: String  // "json_object" or "text"
)

@Serializable
data class OpenAIResponse(
    @SerialName("choices")
    val choices: List<OpenAIChoice>,
    @SerialName("usage")
    val usage: TokenUsage? = null
)

@Serializable
data class OpenAIChoice(
    @SerialName("message")
    val message: OpenAIMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

// OpenAI Function Calling Data Structures

@Serializable
data class OpenAITool(
    @SerialName("type")
    val type: String = "function",
    @SerialName("function")
    val function: OpenAIFunction
)

@Serializable
data class OpenAIFunction(
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("parameters")
    val parameters: JsonObject
)

@Serializable
data class OpenAIToolCall(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String = "function",
    @SerialName("function")
    val function: OpenAIFunctionCall
)

@Serializable
data class OpenAIFunctionCall(
    @SerialName("name")
    val name: String,
    @SerialName("arguments")
    val arguments: String  // JSON string
)

@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)
