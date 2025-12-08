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
            if (history.isEmpty()) {
                add(OpenAIMessage(
                    role = "assistant",
                    content = fileRepository.getAssistantPrompt()
                ))
            }

            // Add conversation history
            history.forEach { msg ->
                add(OpenAIMessage(role = msg.role, content = msg.content))
            }

            // Add current user message
            add(OpenAIMessage(role = "user", content = userMessage))
        }

        // Create request with JSON mode enabled
        val request = OpenAIRequest(
            model = "gpt-4o-mini",
            messages = messages,
            temperature = 0.7,
            responseFormat = ResponseFormat(type = "json_object")
        )

        // Call OpenAI API
        val response = client.post("https://api.openai.com/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<OpenAIResponse>()

        val rawResponse = response.choices.firstOrNull()?.message?.content
            ?: "Sorry, I couldn't generate a response."

        logger.info(rawResponse)

        // Parse JSON response and format it
        val formattedResponse = try {
            parseAndFormatResponse(rawResponse)
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
    private fun parseAndFormatResponse(rawResponse: String): String {
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
            return "I don't have enough information to answer that question properly."
        }

        // Format the response based on type
        return when (aiResponse.type) {
            "question" -> formatQuestionResponse(aiResponse)
            "plan" -> formatPlanResponse(aiResponse)
            else -> formatStandardResponse(aiResponse)
        }
    }

    /**
     * Format standard answer response
     */
    private fun formatStandardResponse(aiResponse: AiResponse): String {
        return aiResponse.text ?: ""
    }

    /**
     * Format question response (gathering requirements)
     */
    private fun formatQuestionResponse(aiResponse: AiResponse): String {
        return buildString {
            append("❓ *Gathering Information*\n\n")
            append(aiResponse.text ?: "")
            if (aiResponse.questionsAsked != null && aiResponse.questionsAsked > 0) {
                append("\n\n_Please answer the question above so I can create a comprehensive plan for you._")
            }
        }.trim()
    }

    /**
     * Format development plan response
     */
    private fun formatPlanResponse(aiResponse: AiResponse): String {
        return buildString {
            append("📋 *Development Plan*\n\n")
            append("---\n\n")
            append(aiResponse.text ?: "")
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
    val type: String? = "answer",  // "answer", "question", or "plan"
    @SerialName("text")
    val text: String? = null,
    @SerialName("questionsAsked")
    val questionsAsked: Int? = null
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
    val responseFormat: ResponseFormat? = null
)

@Serializable
data class ResponseFormat(
    @SerialName("type")
    val type: String  // "json_object" or "text"
)

@Serializable
data class OpenAIResponse(
    @SerialName("choices")
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    @SerialName("message")
    val message: OpenAIMessage
)
