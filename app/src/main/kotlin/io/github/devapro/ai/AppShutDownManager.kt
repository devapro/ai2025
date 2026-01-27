package io.github.devapro.ai

import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.mcp.McpManager
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

/**
 * Component responsible for managing application shutdown
 * Registers a JVM shutdown hook to gracefully close all resources
 */
class AppShutDownManager(
    private val mcpManager: McpManager,
    private val aiAgent: AiAgent,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(AppShutDownManager::class.java)

    /**
     * Register shutdown hook with JVM runtime
     */
    fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down...")

            // Close MCP manager
            runBlocking {
                mcpManager.close()
            }

            // Close AI agent
            aiAgent.close()

            // Close HTTP client
            httpClient.close()

            // Stop Koin DI
            stopKoin()

            logger.info("Application stopped")
        })
    }
}
