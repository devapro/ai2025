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

    private val bot = bot {
        token = botToken

        dispatch {
            // Handle /start command
            command("start") {
                val chatId = ChatId.fromId(message.chat.id)
                bot.sendMessage(
                    chatId = chatId,
                    text = """
                        📅 *Welcome to your AI Calendar Assistant!*

                        I help you manage your schedule and stay organized using connected calendar systems.

                        *What I can do:*
                        • 📝 Create and schedule events
                        • 📋 List your schedule
                        • 🔍 Search for events
                        • ✏️ Update and reschedule events
                        • 🗑️ Delete events
                        • ⏰ Check your availability

                        *Commands:*
                        • /summary - Get today's schedule
                        • /clear - Clear conversation history
                        • /help - Show available commands

                        Just tell me what you need with your calendar!
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
                        • 📝 Create events: "Schedule a meeting tomorrow at 2pm"
                        • 📋 List schedule: "What's on my calendar today?"
                        • 🔍 Search: "Find my meetings with John"
                        • ✏️ Update: "Move my dentist appointment to Friday"
                        • 🗑️ Delete: "Cancel the team lunch"
                        • ⏰ Availability: "When am I free this week?"

                        *Tips:*
                        • Use natural language for dates and times
                        • I'll ask for details if needed
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

                        // Send with Markdown formatting
                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = response,
                            parseMode = ParseMode.MARKDOWN
                        )
                    } catch (e: Exception) {
                        logger.error("Error processing summary command: ${e.message}", e)
                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = "Sorry, I encountered an error getting your schedule summary. Please try again."
                        )
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

                        // Send with Markdown formatting
                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = response,
                            parseMode = ParseMode.MARKDOWN
                        )
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
