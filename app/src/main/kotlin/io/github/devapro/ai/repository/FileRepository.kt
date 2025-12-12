package io.github.devapro.ai.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.time.LocalDateTime

/**
 * Repository for managing prompts and conversation history stored in files
 */
class FileRepository(
    private val promptsDir: String = "promts",
    private val historyDir: String = "history"
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Ensure directories exist
        File(promptsDir).mkdirs()
        File(historyDir).mkdirs()
    }

    /**
     * Read system prompt from markdown file
     */
    fun getSystemPrompt(): String {
        val promptFile = File(promptsDir, "system.md")
        return if (promptFile.exists()) {
            promptFile.readText()
        } else {
            "You are a helpful AI assistant."
        }
    }

    /**
     * Read assistant prompt from markdown file
     */
    fun getAssistantPrompt(): String {
        val promptFile = File(promptsDir, "assistant.md")
        return if (promptFile.exists()) {
            promptFile.readText()
        } else {
            "I'm ready to assist you! How can I help you today?"
        }
    }

    /**
     * Get conversation history for a specific user
     * @param userId Telegram user ID
     * @return List of conversation messages
     */
    fun getUserHistory(userId: Long): List<ConversationMessage> {
        val historyFile = File(historyDir, "user_${userId}.json")
        if (!historyFile.exists()) {
            return emptyList()
        }

        return try {
            val historyData = json.decodeFromString<ConversationHistory>(historyFile.readText())
            historyData.messages
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save user message to history
     */
    fun saveUserMessage(userId: Long, message: String) {
        val timestamp = LocalDateTime.now().toString()
        val conversationMessage = ConversationMessage(
            role = "user",
            content = message,
            timestamp = timestamp
        )
        addMessageToHistory(userId, conversationMessage)
    }

    /**
     * Save assistant response to history
     */
    fun saveAssistantMessage(userId: Long, message: String) {
        val timestamp = LocalDateTime.now().toString()
        val conversationMessage = ConversationMessage(
            role = "assistant",
            content = message,
            timestamp = timestamp
        )
        addMessageToHistory(userId, conversationMessage)
    }

    /**
     * Save system message to history (e.g., conversation summary)
     */
    fun saveSystemMessage(userId: Long, message: String) {
        val timestamp = LocalDateTime.now().toString()
        val conversationMessage = ConversationMessage(
            role = "system",
            content = message,
            timestamp = timestamp
        )
        addMessageToHistory(userId, conversationMessage)
    }

    /**
     * Add a message to user's conversation history
     */
    private fun addMessageToHistory(userId: Long, message: ConversationMessage) {
        val historyFile = File(historyDir, "user_${userId}.json")

        val history = if (historyFile.exists()) {
            try {
                json.decodeFromString<ConversationHistory>(historyFile.readText())
            } catch (e: Exception) {
                ConversationHistory(messages = emptyList())
            }
        } else {
            ConversationHistory(messages = emptyList())
        }

        val updatedHistory = ConversationHistory(
            messages = history.messages + message
        )

        historyFile.writeText(json.encodeToString(updatedHistory))
    }

    /**
     * Clear history for a specific user
     */
    fun clearUserHistory(userId: Long) {
        val historyFile = File(historyDir, "user_${userId}.json")
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }
}

/**
 * Conversation history container
 */
@Serializable
data class ConversationHistory(
    @SerialName("messages")
    val messages: List<ConversationMessage>
)

/**
 * Represents a single message in a conversation
 */
@Serializable
data class ConversationMessage(
    @SerialName("role")
    val role: String,  // "user", "assistant", or "system"
    @SerialName("content")
    val content: String,
    @SerialName("timestamp")
    val timestamp: String? = null
)
