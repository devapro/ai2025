package io.github.devapro.ai.agent

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
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * AI Agent component that handles conversations using OpenAI API
 */

private const val modelName = "openai/gpt-oss-20b"

class AiAgent(
    private val apiKey: String,
    private val fileRepository: FileRepository
) {
    private val logger = LoggerFactory.getLogger(AiAgent::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 520_000 // 60 seconds
        }
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Process user message and generate response
     * @param userId Telegram user ID
     * @param userMessage User's message
     * @return Assistant's response
     */
    suspend fun processMessage(userId: Long, userMessage: String): String {
        // Get system prompt
        val systemPrompt = fileRepository.getSystemPrompt()

        // Get conversation history
        val history = fileRepository.getUserHistory(userId)

        // Build messages list for the API
        val messages = buildList {
            // Add system message
            add(OpenAIMessage(role = "system", content = systemPrompt))

            // Add assistant prompt to guide behavior
//            if (history.isEmpty()) {
//                add(OpenAIMessage(
//                    role = "assistant",
//                    content = fileRepository.getAssistantPrompt()
//                ))
//            }

            // Add conversation history
            history.forEach { msg ->
                add(OpenAIMessage(role = msg.role, content = msg.content))
            }

            // Add current user message
            add(OpenAIMessage(role = "user", content = userMessage))
        }

        // Create request with JSON mode enabled
        val request = OpenAIRequest(
            model = modelName,
            messages = messages,
            temperature = 0.9,
           // responseFormat = ResponseFormat(type = "json_object"),
            stream = false
        )

        // Measure API response time
        val startTime = System.currentTimeMillis()

        // Call OpenAI API
        val response = client.post("http://127.0.0.1:1234/v1/chat/completions") {
         //   header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<OpenAIResponse>()

        val responseTime = System.currentTimeMillis() - startTime

        val rawResponse = response.choices.firstOrNull()?.message?.content
            ?: "Sorry, I couldn't generate a response."

        val usage = response.usage

        logger.info(rawResponse)
        if (usage != null) {
            logger.info("Token usage - Prompt: ${usage.promptTokens}, Completion: ${usage.completionTokens}, Total: ${usage.totalTokens}")
        }
        logger.info("Response time: ${responseTime}ms")

        // Parse JSON response and format it
        val formattedResponse = try {
            parseAndFormatResponse(rawResponse, usage, responseTime)
        } catch (e: Exception) {
            logger.warn("Failed to parse AI response as JSON, using raw response: ${e.message}")
            rawResponse
        }

        // Save messages to history
        fileRepository.saveUserMessage(userId, userMessage)
        fileRepository.saveAssistantMessage(userId, formattedResponse)

        return formattedResponse
    }

    /**
     * Clear conversation history for a user
     */
    fun clearHistory(userId: Long) {
        fileRepository.clearUserHistory(userId)
    }

    /**
     * Parse JSON response from AI and format it for display
     */
    private fun parseAndFormatResponse(rawResponse: String, usage: TokenUsage?, responseTime: Long): String {
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
        return formatResponse(aiResponse, usage, responseTime)
    }

    /**
     * Format AI response with statistics
     */
    private fun formatResponse(aiResponse: AiResponse, usage: TokenUsage?, responseTime: Long): String {
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
            if (usage != null) {
                append("• Tokens used: ${usage.totalTokens} (prompt: ${usage.promptTokens}, completion: ${usage.completionTokens})")
            }
        }.trim()
    }

    fun close() {
        client.close()
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
    val content: String
)

@Serializable
data class OpenAIRequest(
    @SerialName("model")
    val model: String,
    @SerialName("messages")
    val messages: List<OpenAIMessage>,
    @SerialName("temperature")
    val temperature: Double,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
    @SerialName("stream")
    val stream: Boolean = false
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
    val message: OpenAIMessage
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
