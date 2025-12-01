package io.github.devapro.ai

import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.di.allModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.logger.slf4jLogger
import org.koin.mp.KoinPlatformTools
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("App")

    logger.info("Starting AI Telegram Bot application...")

    try {
        // Start Koin DI
        logger.info("Initializing Koin dependency injection...")
        startKoin {
            slf4jLogger()
            modules(allModules)
        }
        logger.info("Koin initialized successfully")

        // Get components from Koin
        val koin = KoinPlatformTools.defaultContext().get()

        val telegramBot = koin.get<TelegramBot>()
        logger.info("All components initialized via DI")

        val aiAgent = koin.get<AiAgent>()

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down...")
            telegramBot.stop()
            aiAgent.close()
            stopKoin()
            logger.info("Application stopped")
        })

        // Start the bot
        telegramBot.start()

        logger.info("Application started successfully!")

        // Keep the application running
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Fatal error: ${e.message}", e)
        stopKoin()
        throw e
    }
}
