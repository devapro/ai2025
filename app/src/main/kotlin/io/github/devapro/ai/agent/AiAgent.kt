package io.github.devapro.ai.agent

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import io.github.devapro.ai.mcp.McpManager
import io.github.devapro.ai.mcp.model.ToolContent
import io.github.devapro.ai.repository.FileRepository
import io.github.devapro.ai.utils.rag.EmbeddingGenerator
import io.github.devapro.ai.utils.rag.VectorDatabase
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
private const val MAX_TOOL_ITERATIONS = 20
private const val MAX_MESSAGES_IN_CONTEXT = 10
private const val ragEnabled = false

class AiAgent(
    private val apiKey: String,
    private val fileRepository: FileRepository,
    private val mcpManager: McpManager,
    private val httpClient: HttpClient,
    private val conversationSummarizer: AiAgentConversationSummarizer,
    private val responseFormatter: AiAgentResponseFormatter,
    private val vectorDatabase: VectorDatabase,
    private val embeddingGenerator: EmbeddingGenerator,
    private val ragTopK: Int = 5,
    private val ragMinSimilarity: Double = 0.7
) {
    private val logger = LoggerFactory.getLogger(AiAgent::class.java)

    // Token counting with jtokkit
    private val encodingRegistry = Encodings.newDefaultEncodingRegistry()
    private val encoding = encodingRegistry.getEncoding(EncodingType.O200K_BASE)

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
        val availableTools = getAvailableTools()

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

                        // Execute tool: built-in or MCP
                        val resultText = when (toolName) {
                            "search_documents" -> {
                                // Built-in RAG search tool
                                executeSearchDocuments(argsObject)
                            }
                            else -> {
                                // MCP tool
                                val toolResult = mcpManager.callTool(toolName, argsObject)

                                // Format tool result as text content
                                toolResult.content.joinToString("\n\n") { content ->
                                    when (content.type) {
                                        "text" -> content.text ?: ""
                                        "image" -> "[Image: ${content.mimeType}]"
                                        "resource" -> "[Resource: ${content.uri}]"
                                        else -> "[Unknown content type: ${content.type}]"
                                    }
                                }
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
                        responseFormatter.parseAndFormatResponse(rawResponse, usage, responseTime, estimatedTokens, historyLength)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse AI response as JSON, using raw response: ${e.message}")
                        rawResponse
                    }

                    // Save messages to history
                    fileRepository.saveUserMessage(userId, userMessage)
                    fileRepository.saveAssistantMessage(userId, formattedResponse)

                    // Check if we need to summarize history (every 5 messages)
                    val newHistoryLength = historyLength + 2
                    if (newHistoryLength >= MAX_MESSAGES_IN_CONTEXT && newHistoryLength % MAX_MESSAGES_IN_CONTEXT == 0) {
                        logger.info("History length reached $newHistoryLength, creating summary...")
                        conversationSummarizer.summarizeAndReplaceHistory(userId)
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
     * Get available tools from MCP manager and built-in tools (like RAG), convert to OpenAI format
     */
    private suspend fun getAvailableTools(): List<OpenAITool> {
        val allTools = mutableListOf<OpenAITool>()

        // Add MCP tools
        if (mcpManager.isAvailable()) {
            try {
                val mcpTools = mcpManager.getAllTools()
                logger.info("Found ${mcpTools.size} MCP tools available")
                allTools.addAll(mcpTools.map { mcpTool ->
                    OpenAITool(
                        function = OpenAIFunction(
                            name = mcpTool.name,
                            description = mcpTool.description,
                            parameters = mcpTool.inputSchema
                        )
                    )
                })
            } catch (e: Exception) {
                logger.error("Error fetching MCP tools: ${e.message}", e)
            }
        } else {
            logger.debug("No MCP tools available")
        }

        // Add built-in RAG tool if enabled
        if (ragEnabled) {
            logger.info("Adding built-in RAG search_documents tool")
            allTools.add(createSearchDocumentsTool())
        }

        return allTools
    }

    /**
     * Create the search_documents tool definition for OpenAI
     */
    private fun createSearchDocumentsTool(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "search_documents",
                description = "Search through indexed documents using semantic similarity. " +
                        "Use this tool when you need to find relevant information from the knowledge base. " +
                        "The search returns the most relevant text chunks that match the query semantically.",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The search query to find relevant documents. " +
                                    "Be specific and detailed in your query for better results."))
                        }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("query"))
                    }
                }
            )
        )
    }

    /**
     * Execute the search_documents tool: query → embedding → similarity search → results
     */
    private suspend fun executeSearchDocuments(args: JsonObject?): String {
        if (vectorDatabase == null || embeddingGenerator == null) {
            return "Error: RAG is not properly configured"
        }

        try {
            // Extract query from arguments
            val query = args?.get("query")?.jsonPrimitive?.content
            if (query.isNullOrBlank()) {
                return "Error: Query parameter is required"
            }

            logger.info("Searching documents for query: $query")

            // Step 1: Generate embedding for the query
            val queryEmbedding = embeddingGenerator.generateEmbedding(query)
            logger.info("Generated embedding for query (${queryEmbedding.vector.size} dimensions)")

            // Step 2: Search for similar vectors in the database
            val searchResults = vectorDatabase.search(
                queryEmbedding = queryEmbedding.vector,
                topK = ragTopK,
                minSimilarity = ragMinSimilarity
            )

            logger.info("Found ${searchResults.size} results with similarity >= $ragMinSimilarity")

            // Step 3: Format results for LLM
            if (searchResults.isEmpty()) {
                return "No relevant documents found for query: \"$query\""
            }

            val formattedResults = buildString {
                appendLine("Found ${searchResults.size} relevant document(s):\n")

                searchResults.forEachIndexed { index, result ->
                    appendLine("--- Document ${index + 1} (Similarity: ${"%.3f".format(result.similarity)}) ---")

                    // Add heading context if available
                    result.metadata?.get("heading")?.let { heading ->
                        appendLine("Section: $heading")
                    }

                    appendLine()
                    appendLine(result.text)
                    appendLine()
                }

                appendLine("---")
                appendLine("Use the information from these documents to answer the user's question.")
            }

            return formattedResults

        } catch (e: Exception) {
            logger.error("Error executing search_documents: ${e.message}", e)
            return "Error searching documents: ${e.message}"
        }
    }

    fun close() {
        // HTTP client is shared and managed by DI
        // Don't close it here
    }
}