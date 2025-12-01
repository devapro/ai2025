package io.github.devapro.ai.agent

import io.github.devapro.ai.repository.ConversationMessage
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

/**
 * AI Agent component that handles conversations using OpenAI API
 */
class AiAgent(
    private val apiKey: String,
    private val fileRepository: FileRepository
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
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

            // Add conversation history
            history.forEach { msg ->
                add(OpenAIMessage(role = msg.role, content = msg.content))
            }

            // Add current user message
            add(OpenAIMessage(role = "user", content = userMessage))
        }

        // Create request
        val request = OpenAIRequest(
            model = "gpt-4o-mini",
            messages = messages,
            temperature = 0.7
        )

        // Call OpenAI API
        val response = client.post("https://api.openai.com/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<OpenAIResponse>()

        val assistantResponse = response.choices.firstOrNull()?.message?.content
            ?: "Sorry, I couldn't generate a response."

        // Save messages to history
        fileRepository.saveUserMessage(userId, userMessage)
        fileRepository.saveAssistantMessage(userId, assistantResponse)

        return assistantResponse
    }

    /**
     * Clear conversation history for a user
     */
    fun clearHistory(userId: Long) {
        fileRepository.clearUserHistory(userId)
    }

    fun close() {
        client.close()
    }
}

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
    val temperature: Double
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
