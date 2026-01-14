package io.github.devapro.ai.tools

import io.github.devapro.ai.tools.tools.TicketTool
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Main entry point for the Support Tools MCP Server
 *
 * This server provides support tools via the Model Context Protocol (MCP).
 * Communication happens over stdin/stdout using JSON-RPC 2.0.
 *
 * Available Tools:
 * - manage_tickets: View and manage support tickets
 *
 * Usage:
 * 1. Direct execution:
 *    ./gradlew :tools:run
 *
 * 2. As MCP server (from another application):
 *    Add to mcp-config.json:
 *    {
 *      "name": "support-tools",
 *      "type": "stdio",
 *      "command": "./gradlew",
 *      "args": [":tools:run", "--console=plain", "--quiet"]
 *    }
 *
 * Environment:
 * - Working directory is set to project root (configured in build.gradle.kts)
 * - Tickets are loaded from tickets.json in the working directory
 */
fun main() {
    val logger = LoggerFactory.getLogger("SupportToolsServer")

    try {
        // Get working directory from system property
        val workingDir = File(System.getProperty("user.dir"))
        logger.info("Working directory: ${workingDir.absolutePath}")

        // Initialize tools
        val tools = listOf(
            TicketTool(ticketsFilePath = "tickets.json")
        )

        // Create and start MCP server
        val server = McpServer(
            tools = tools,
            serverName = "support-tools",
            serverVersion = "1.0.0"
        )

        logger.info("Starting Support Tools MCP Server...")
        server.start()

    } catch (e: Exception) {
        logger.error("Failed to start server: ${e.message}", e)
        System.exit(1)
    }
}
