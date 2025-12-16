package io.github.devapro.ai.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.utils.MarkdownUtils
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

    private val bot = bot {
        token = botToken

        dispatch {
            // Handle /start command
            command("start") {
                val chatId = ChatId.fromId(message.chat.id)
                bot.sendMessage(
                    chatId = chatId,
                    text = """
                        *Welcome!* I'm your AI assistant.

                        I can help you with:
                        • Answering questions
                        • Explaining complex topics
                        • Providing practical advice
                        • Solving problems
                        • And much more!

                        Commands:
                        • Use /clear to clear conversation history
                        • Use /help to see available commands

                        Just ask me anything!
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

                        /start - Start conversation
                        /help - Show this help message
                        /clear - Clear conversation history

                        *What I can do:*
                        • Answer factual questions
                        • Explain complex topics
                        • Provide how-to guides
                        • Offer recommendations
                        • Help solve problems
                        • And much more!

                        Just ask me any question!
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
                        logger.debug("Response length: ${response.length} characters")

                        // Truncate if needed (Telegram limit is 4096 characters)
                        val truncatedResponse = MarkdownUtils.truncateIfNeeded(response)

                        // Send as plain text (no markdown) to avoid parsing issues with LLM responses
                        // LLM responses may contain asterisks, underscores, etc. that break markdown
                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = truncatedResponse
                        )

                        logger.info("Message sent successfully to user $chatId")
                    } catch (e: Exception) {
                        logger.error("Error processing message: ${e.message}", e)
                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = "Sorry, I encountered an error processing your message. Please try again."
                        )
                    }
                }
            }
        }
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
