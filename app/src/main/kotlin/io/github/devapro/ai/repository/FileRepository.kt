package io.github.devapro.ai.repository

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Repository for managing prompts and conversation history stored in files
 */
class FileRepository(
    private val promptsDir: String = "promts",
    private val historyDir: String = "history"
) {
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
        val historyFile = File(historyDir, "user_${userId}.md")
        if (!historyFile.exists()) {
            return emptyList()
        }

        val messages = mutableListOf<ConversationMessage>()
        val lines = historyFile.readLines()

        var currentRole: String? = null
        val currentContent = StringBuilder()

        for (line in lines) {
            when {
                line.startsWith("## User:") -> {
                    if (currentRole != null) {
                        messages.add(ConversationMessage(currentRole, currentContent.toString().trim()))
                        currentContent.clear()
                    }
                    currentRole = "user"
                }
                line.startsWith("## Assistant:") -> {
                    if (currentRole != null) {
                        messages.add(ConversationMessage(currentRole, currentContent.toString().trim()))
                        currentContent.clear()
                    }
                    currentRole = "assistant"
                }
                line.startsWith("## System:") -> {
                    if (currentRole != null) {
                        messages.add(ConversationMessage(currentRole, currentContent.toString().trim()))
                        currentContent.clear()
                    }
                    currentRole = "system"
                }
                line.startsWith("---") -> {
                    // Separator, continue
                }
                else -> {
                    if (currentRole != null && line.isNotBlank()) {
                        currentContent.append(line).append("\n")
                    }
                }
            }
        }

        // Add last message if exists
        if (currentRole != null && currentContent.isNotEmpty()) {
            messages.add(ConversationMessage(currentRole, currentContent.toString().trim()))
        }

        return messages
    }

    /**
     * Save user message to history
     */
    fun saveUserMessage(userId: Long, message: String) {
        val historyFile = File(historyDir, "user_${userId}.md")
        val timestamp = java.time.LocalDateTime.now().toString()

        val content = buildString {
            append("\n## User:\n")
            append("*${timestamp}*\n\n")
            append(message)
            append("\n\n---\n")
        }

        Files.write(
            Paths.get(historyFile.absolutePath),
            content.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    /**
     * Save assistant response to history
     */
    fun saveAssistantMessage(userId: Long, message: String) {
        val historyFile = File(historyDir, "user_${userId}.md")
        val timestamp = java.time.LocalDateTime.now().toString()

        val content = buildString {
            append("\n## Assistant:\n")
            append("*${timestamp}*\n\n")
            append(message)
            append("\n\n---\n")
        }

        Files.write(
            Paths.get(historyFile.absolutePath),
            content.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    /**
     * Save system message to history (e.g., conversation summary)
     */
    fun saveSystemMessage(userId: Long, message: String) {
        val historyFile = File(historyDir, "user_${userId}.md")
        val timestamp = java.time.LocalDateTime.now().toString()

        val content = buildString {
            append("\n## System:\n")
            append("*${timestamp}*\n\n")
            append(message)
            append("\n\n---\n")
        }

        Files.write(
            Paths.get(historyFile.absolutePath),
            content.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    /**
     * Clear history for a specific user
     */
    fun clearUserHistory(userId: Long) {
        val historyFile = File(historyDir, "user_${userId}.md")
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }
}

/**
 * Represents a single message in a conversation
 */
data class ConversationMessage(
    val role: String,  // "user", "assistant", or "system"
    val content: String
)
