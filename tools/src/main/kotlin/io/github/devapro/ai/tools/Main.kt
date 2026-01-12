package io.github.devapro.ai.tools

import io.github.devapro.ai.tools.tools.FindFileTool
import io.github.devapro.ai.tools.tools.ReadFileTool
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Main entry point for the File Tools MCP Server
 *
 * This server provides file system tools via the Model Context Protocol (MCP).
 * Communication happens over stdin/stdout using JSON-RPC 2.0.
 *
 * Available Tools:
 * - find_file: Search for files in directories with glob patterns
 * - read_file: Read file contents with optional line range
 *
 * Usage:
 * 1. Direct execution:
 *    ./gradlew :tools:run
 *
 * 2. As MCP server (from another application):
 *    Add to mcp-config.json:
 *    {
 *      "name": "file-tools",
 *      "type": "stdio",
 *      "command": "./gradlew",
 *      "args": [":tools:run", "--console=plain", "--quiet"]
 *    }
 *
 * Environment:
 * - Working directory is set to project root (configured in build.gradle.kts)
 * - All file paths are resolved relative to working directory
 */
fun main() {
    val logger = LoggerFactory.getLogger("FileToolsServer")

    try {
        // Get working directory from system property
        val workingDir = File(System.getProperty("user.dir"))
        logger.info("Working directory: ${workingDir.absolutePath}")

        // Initialize tools
        val tools = listOf(
            FindFileTool(workingDirectory = workingDir),
            ReadFileTool(workingDirectory = workingDir)
        )

        // Create and start MCP server
        val server = McpServer(
            tools = tools,
            serverName = "file-tools",
            serverVersion = "1.0.0"
        )

        logger.info("Starting File Tools MCP Server...")
        server.start()

    } catch (e: Exception) {
        logger.error("Failed to start server: ${e.message}", e)
        System.exit(1)
    }
}
