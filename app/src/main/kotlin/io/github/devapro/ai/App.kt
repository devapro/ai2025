package io.github.devapro.ai

import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.di.allModules
import io.github.devapro.ai.mcp.McpManager
import io.github.devapro.ai.scheduler.DailySummaryScheduler
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
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

        val mcpManager = koin.get<McpManager>()
        val telegramBot = koin.get<TelegramBot>()
        val aiAgent = koin.get<AiAgent>()
        val httpClient = koin.get<HttpClient>()
        val dailySummaryScheduler = koin.get<DailySummaryScheduler>()

        logger.info("All components initialized via DI")

        // Initialize MCP servers
        logger.info("Initializing MCP servers...")
        runBlocking {
            mcpManager.initialize()
        }

        if (mcpManager.isAvailable()) {
            logger.info("MCP tools are available")
        } else {
            logger.info("No MCP servers available - running without tools")
        }

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down...")
            dailySummaryScheduler.stop()
            runBlocking {
                mcpManager.close()
            }
            telegramBot.stop()
            aiAgent.close()
            httpClient.close()
            stopKoin()
            logger.info("Application stopped")
        })

        // Start the bot
        telegramBot.start()

        // Start the daily summary scheduler
   //     dailySummaryScheduler.start()

        // For testing
//        runBlocking {
//            dailySummaryScheduler.triggerManualSummary()
//        }

        logger.info("Application started successfully!")

        // Keep the application running
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Fatal error: ${e.message}", e)
        stopKoin()
        throw e
    }
}
