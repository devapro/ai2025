package io.github.devapro.ai.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.devapro.ai.agent.AiAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Telegram Bot component that handles user interactions
 */
class TelegramBot(
    private val botToken: String,
    private val aiAgent: AiAgent
) {
    private val logger = LoggerFactory.getLogger(TelegramBot::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val MAX_MESSAGE_LENGTH = 4096

        /**
         * Escape special Markdown characters to prevent parsing errors
         * Telegram Markdown special characters: _ * ` [
         */
        private fun escapeMarkdown(text: String): String {
            return text
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`")
                .replace("[", "\\[")
        }
    }

    val bot = bot {
        token = botToken

        dispatch {
            // Handle /start command
            command("start") {
                val chatId = ChatId.fromId(message.chat.id)
                bot.sendMessage(
                    chatId = chatId,
                    text = """
                        👋 *Welcome to your Support Assistant!*

                        I'm your intelligent support assistant, here to help you find answers and manage support tickets.

                        *What I can do:*
                        • 📚 Search our knowledge base for answers
                        • 🎫 View and manage support tickets
                        • 💡 Answer questions about common issues
                        • 🔍 Find solutions to technical problems
                        • ⚡ Provide quick troubleshooting steps

                        *Key Commands:*
                        • /summary - Get today's schedule
                        • /clear - Clear conversation history
                        • /help - Show all available commands

                        *Pro Tips:*
                        • Ask me anything - I'll search our documentation
                        • Request ticket information: "Show me open tickets"
                        • Get help with common issues: "How do I reset my password?"

                        Just tell me how I can help you! 🚀
                    """.trimIndent(),
                    parseMode = ParseMode.MARKDOWN
                )
            }

            // Handle /help command
            command("help") {
                val chatId = ChatId.fromId(message.chat.id)
                bot.sendMessage(
                    chatId = chatId,
                    text = """
                        *Available commands:*

                        /start - Show welcome message
                        /summary - Get today's schedule and events
                        /clear - Clear conversation history
                        /help - Show this help message

                        *What I can do:*
                        • 📚 Search documentation: "How do I reset my password?"
                        • 🎫 View tickets: "Show me all open tickets"
                        • 🔍 Find tickets: "Are there any tickets about login issues?"
                        • 📊 Ticket details: "What's the status of TICKET-001?"
                        • 💡 Troubleshooting: "What are common login issues?"
                        • ⚙️ Features: "How do I enable two-factor authentication?"

                        *Example questions:*
                        • "How much does the service cost?"
                        • "Show me all critical tickets"
                        • "What's on my calendar today?"
                        • "How do I cancel my subscription?"

                        *Tips:*
                        • Use natural language - I'll understand!
                        • I automatically search our knowledge base
                        • Ask about specific tickets by ID
                        • Use /summary for a quick daily overview

                        Just tell me what you need!
                    """.trimIndent(),
                    parseMode = ParseMode.MARKDOWN
                )
            }

            // Handle /clear command
            command("clear") {
                val chatId = message.chat.id
                aiAgent.clearHistory(chatId)
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "✅ *Conversation history cleared!*",
                    parseMode = ParseMode.MARKDOWN
                )
            }

            // Handle /summary command
            command("summary") {
                val chatId = message.chat.id
                logger.info("Summary command received from user $chatId")

                // Send typing indicator
                bot.sendChatAction(ChatId.fromId(chatId), com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                // Process summary request with AI agent in coroutine
                coroutineScope.launch {
                    try {
                        val response = aiAgent.processMessage(
                            chatId,
                            "Please provide a summary of all events and tasks scheduled for today. Include time, title, location, and any important details."
                        )
                        logger.info("Sending summary response to user $chatId")
                        logger.info("Response preview: ${response.take(200)}...")

                        // Send with safe handling
                        sendMessageSafely(chatId, response)
                        logger.info("sendMessageSafely completed for summary")
                    } catch (e: Exception) {
                        logger.error("Error in summary command coroutine: ${e.message}", e)
                        try {
                            sendMessageSafely(chatId, "Sorry, I encountered an error getting your schedule summary. Please try again.")
                        } catch (e2: Exception) {
                            logger.error("Failed to send error message: ${e2.message}", e2)
                        }
                    }
                }
            }

            // Handle regular text messages
            text {
                val chatId = message.chat.id
                val userMessage = text

                // Ignore commands (they are handled by command handlers)
                if (userMessage.startsWith("/")) {
                    return@text
                }

                logger.info("Received message from user $chatId: $userMessage")

                // Send typing indicator
                bot.sendChatAction(ChatId.fromId(chatId), com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                // Process message with AI agent in coroutine
                coroutineScope.launch {
                    try {
                        val response = aiAgent.processMessage(chatId, userMessage)
                        logger.info("Sending response to user $chatId")
                        logger.info("Response preview: ${response.take(200)}...")

                        // Send with safe handling
                        sendMessageSafely(chatId, response)
                        logger.info("sendMessageSafely completed")
                    } catch (e: Exception) {
                        logger.error("Error in message handler coroutine: ${e.message}", e)
                        try {
                            sendMessageSafely(chatId, "Sorry, I encountered an error processing your message. Please try again.")
                        } catch (e2: Exception) {
                            logger.error("Failed to send error message: ${e2.message}", e2)
                        }
                    }
                }
            }
        }
    }

    /**
     * Safely send a message to Telegram, handling long messages and Markdown escaping
     * Tries to send with escaped Markdown first, falls back to plain text on error
     */
    private fun sendMessageSafely(chatId: Long, text: String) {
        logger.info("sendMessageSafely called for user $chatId, message length: ${text.length}")

        val chatIdObj = ChatId.fromId(chatId)

        // Split long messages into chunks
        if (text.length <= MAX_MESSAGE_LENGTH) {
            // Try with escaped Markdown first
            val escapedText = try {
                escapeMarkdown(text)
            } catch (e: Exception) {
                logger.warn("Failed to escape Markdown: ${e.message}, will use plain text")
                text
            }

            logger.info("Sending single message with Markdown (escaped)")
            try {
                bot.sendMessage(
                    chatId = chatIdObj,
                    text = escapedText,
                    parseMode = ParseMode.MARKDOWN
                )
                logger.info("Message sent successfully with Markdown")
            } catch (e: Exception) {
                logger.warn("Failed with Markdown: ${e.message}, retrying as plain text")
                try {
                    bot.sendMessage(
                        chatId = chatIdObj,
                        text = text
                    )
                    logger.info("Message sent successfully as plain text")
                } catch (e2: Exception) {
                    logger.error("Failed to send plain text message: ${e2.message}", e2)
                }
            }
        } else {
            // Split into chunks
            logger.info("Message too long (${text.length} chars), splitting into chunks")
            val chunks = splitMessage(text)
            logger.info("Split into ${chunks.size} chunks")

            chunks.forEachIndexed { index, chunk ->
                logger.info("Sending chunk ${index + 1}/${chunks.size}, length: ${chunk.length}")

                // Try with escaped Markdown first
                val escapedChunk = try {
                    escapeMarkdown(chunk)
                } catch (e: Exception) {
                    logger.warn("Failed to escape chunk $index: ${e.message}")
                    chunk
                }

                try {
                    bot.sendMessage(
                        chatId = chatIdObj,
                        text = escapedChunk,
                        parseMode = ParseMode.MARKDOWN
                    )
                    logger.info("Chunk $index sent successfully with Markdown")
                } catch (e: Exception) {
                    logger.warn("Chunk $index failed with Markdown: ${e.message}, trying plain text")
                    try {
                        bot.sendMessage(
                            chatId = chatIdObj,
                            text = chunk
                        )
                        logger.info("Chunk $index sent successfully as plain text")
                    } catch (e2: Exception) {
                        logger.error("Failed to send chunk $index as plain text: ${e2.message}", e2)
                    }
                }

                // Small delay between chunks to avoid rate limiting
                if (index < chunks.size - 1) {
                    Thread.sleep(100)
                }
            }
            logger.info("All chunks sent")
        }
    }

    /**
     * Split a long message into chunks that fit within Telegram's limit
     */
    private fun splitMessage(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var remainingText = text

        while (remainingText.length > MAX_MESSAGE_LENGTH) {
            // Try to split at a newline near the limit
            var splitIndex = MAX_MESSAGE_LENGTH
            val lastNewline = remainingText.substring(0, MAX_MESSAGE_LENGTH).lastIndexOf('\n')

            if (lastNewline > MAX_MESSAGE_LENGTH / 2) {
                // Found a good newline to split at
                splitIndex = lastNewline + 1
            }

            chunks.add(remainingText.substring(0, splitIndex))
            remainingText = remainingText.substring(splitIndex)
        }

        if (remainingText.isNotEmpty()) {
            chunks.add(remainingText)
        }

        return chunks
    }

    /**
     * Start the bot
     */
    fun start() {
        logger.info("Starting Telegram bot...")
        bot.startPolling()
        logger.info("Telegram bot started successfully!")
    }

    /**
     * Stop the bot
     */
    fun stop() {
        logger.info("Stopping Telegram bot...")
        bot.stopPolling()
        logger.info("Telegram bot stopped")
    }
}
