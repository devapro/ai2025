package io.github.devapro.ai

import io.github.devapro.ai.cli.CliInterface
import io.github.devapro.ai.di.allModules
import io.github.devapro.ai.mcp.McpManager
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.logger.slf4jLogger
import org.koin.mp.KoinPlatformTools
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main() {
    val logger = LoggerFactory.getLogger("App")

    logger.info("Starting AI Assistant CLI application...")

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
        val cliInterface = koin.get<CliInterface>()
        val shutdownManager = koin.get<AppShutDownManager>()

        logger.info("All components initialized via DI")

        // Initialize MCP servers
        logger.info("Initializing MCP servers...")
        runBlocking {
            mcpManager.initialize()
        }

        if (mcpManager.isAvailable()) {
            logger.info("MCP tools are available")
        } else {
            logger.warn("No MCP servers available - running without external tools")
        }

        // Register shutdown hook
        shutdownManager.registerShutdownHook()

        // Start the CLI interface (blocks until user exits)
        logger.info("Starting CLI interface...")
        cliInterface.start()

        logger.info("CLI interface stopped")
    } catch (e: Exception) {
        logger.error("Fatal error: ${e.message}", e)
        stopKoin()
        exitProcess(1)
    }
}
