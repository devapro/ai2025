package io.github.devapro.ai

import io.github.cdimascio.dotenv.dotenv
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.repository.FileRepository
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("App")

    logger.info("Starting AI Telegram Bot application...")

    // Load environment variables
    val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val openAiApiKey = dotenv["OPENAI_API_KEY"]
        ?: throw IllegalStateException("OPENAI_API_KEY environment variable is required")

    val telegramBotToken = dotenv["TELEGRAM_BOT_TOKEN"]
        ?: throw IllegalStateException("TELEGRAM_BOT_TOKEN environment variable is required")

    val promptsDir = dotenv["PROMPTS_DIR"] ?: "promts"
    val historyDir = dotenv["HISTORY_DIR"] ?: "history"

    try {
        // Initialize components
        logger.info("Initializing components...")

        val fileRepository = FileRepository(promptsDir, historyDir)
        logger.info("FileRepository initialized")

        val aiAgent = AiAgent(openAiApiKey, fileRepository)
        logger.info("AI Agent initialized")

        val telegramBot = TelegramBot(telegramBotToken, aiAgent)
        logger.info("Telegram Bot initialized")

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down...")
            telegramBot.stop()
        })

        // Start the bot
        telegramBot.start()

        logger.info("Application started successfully!")

        // Keep the application running
        Thread.currentThread().join()

        aiAgent.close()
    } catch (e: Exception) {
        logger.error("Fatal error: ${e.message}", e)
        throw e
    }
}
