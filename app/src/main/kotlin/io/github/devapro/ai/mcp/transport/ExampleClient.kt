package io.github.devapro.ai.mcp.transport

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Example MCP client application
 *
 * Demonstrates how to use the MCP client to connect to a server
 * and interact with its tools.
 *
 * Usage:
 * - Start the MCP server: `./gradlew run`
 * - In another terminal, run this client
 */
fun main() = runBlocking {
    val logger = LoggerFactory.getLogger("ExampleClient")

    // Create HTTP client with SSE support
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(SSE)
    }

    // Create SSE transport
    val transport = SseTransport(
        httpClient = httpClient,
        baseUrl = "http://localhost:8080"
    )

    // Create MCP client
    val mcpClient = McpClient(transport)

    try {
        logger.info("=== MCP Client Example ===")

        // Initialize connection
        logger.info("\n1. Initializing connection...")
        val initResult = mcpClient.initialize(
            clientInfo = ClientInfo(name = "example-client", version = "1.0.0")
        )
        logger.info("Connected to: ${initResult.serverInfo.name} v${initResult.serverInfo.version}")
        logger.info("Protocol version: ${initResult.protocolVersion}")

        // List available tools
        logger.info("\n2. Listing available tools...")
        val tools = mcpClient.listTools()
        tools.forEach { tool ->
            logger.info("  - ${tool.name}: ${tool.description}")
        }

        // Call get_exchange_rates tool
        logger.info("\n3. Getting exchange rates...")
        val ratesResult = mcpClient.callTool("get_exchange_rates")
        logger.info("Result: ${ratesResult.content}")

        // Call get_stock_price tool for GAZP
        logger.info("\n4. Getting stock price for GAZP...")
        val gazpResult = mcpClient.callTool(
            toolName = "get_stock_price",
            arguments = mapOf("ticker" to "GAZP")
        )
        logger.info("Result: ${gazpResult.content}")

        // Call get_stock_price tool for SBER
        logger.info("\n5. Getting stock price for SBER...")
        val sberResult = mcpClient.callTool(
            toolName = "get_stock_price",
            arguments = mapOf("ticker" to "SBER")
        )
        logger.info("Result: ${sberResult.content}")

        logger.info("\n=== Example completed successfully ===")

    } catch (e: Exception) {
        logger.error("Error during MCP operations", e)
    } finally {
        // Close client and cleanup
        mcpClient.close()
        httpClient.close()
    }
}
