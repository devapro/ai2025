package io.github.devapro.ai.mcp

import io.github.cdimascio.dotenv.Dotenv
import org.slf4j.LoggerFactory

/**
 * Component responsible for loading MCP (Model Context Protocol) server configurations
 * from environment variables.
 *
 * This component:
 * - Reads MCP configuration from .env file via Dotenv
 * - Supports multiple MCP servers (stdio and SSE types)
 * - Validates configurations
 * - Provides clear logging of configuration status
 *
 * Environment variables format:
 * ```
 * # Stdio server (local process)
 * MCP_FILESYSTEM_COMMAND=npx
 * MCP_FILESYSTEM_ARGS=-y,@modelcontextprotocol/server-filesystem,/path/to/dir
 *
 * # SSE server (HTTP)
 * MCP_HTTP_URL=http://localhost:8931
 * ```
 */
class McpConfigProvider(
    private val dotenv: Dotenv
) {
    private val logger = LoggerFactory.getLogger(McpConfigProvider::class.java)

    /**
     * Load all MCP server configurations from environment variables
     *
     * @return List of valid MCP server configurations (empty if none configured)
     */
    fun loadConfigurations(): List<McpServerConfig> {
        logger.info("Loading MCP server configurations from environment...")

        val servers = mutableListOf<McpServerConfig>()

        // Load stdio-based servers
        loadStdioServer()?.let { servers.add(it) }

        // Load SSE-based servers
        loadSseServer()?.let { servers.add(it) }

        // TODO: Support multiple servers with indexed env vars
        // e.g., MCP_STDIO_1_COMMAND, MCP_STDIO_2_COMMAND, etc.

        return when {
            servers.isEmpty() -> {
                logger.info("No MCP servers configured. Agent will run without external tools.")
                logger.debug("To configure MCP servers, set environment variables like:")
                logger.debug("  MCP_FILESYSTEM_COMMAND=npx")
                logger.debug("  MCP_FILESYSTEM_ARGS=-y,@modelcontextprotocol/server-filesystem,/path")
                logger.debug("  MCP_HTTP_URL=http://localhost:8931")
                emptyList()
            }
            else -> {
                logger.info("Loaded ${servers.size} MCP server configuration(s):")
                servers.forEach { config ->
                    logger.info("  - ${config.describe()}")
                }
                servers.filter { it.validate() }.also { validServers ->
                    val invalidCount = servers.size - validServers.size
                    if (invalidCount > 0) {
                        logger.warn("Filtered out $invalidCount invalid configuration(s)")
                    }
                }
            }
        }
    }

    /**
     * Load stdio server configuration
     *
     * Reads from environment variables:
     * - MCP_FILESYSTEM_COMMAND: Command to execute (e.g., "npx", "python")
     * - MCP_FILESYSTEM_ARGS: Comma-separated arguments
     */
    private fun loadStdioServer(): McpServerConfig? {
        val command = dotenv["MCP_FILESYSTEM_COMMAND"]
        if (command.isNullOrBlank()) {
            logger.debug("MCP_FILESYSTEM_COMMAND not set - skipping stdio server")
            return null
        }

        val argsString = dotenv["MCP_FILESYSTEM_ARGS"]
        val args = if (!argsString.isNullOrBlank()) {
            argsString.split(",").map { it.trim() }
        } else {
            emptyList()
        }

        logger.debug("Found stdio server config: command='$command', args=$args")

        return McpServerConfig(
            name = "stdio-server",
            type = "stdio",
            command = command,
            args = args
        )
    }

    /**
     * Load SSE (Server-Sent Events) server configuration
     *
     * Reads from environment variable:
     * - MCP_HTTP_URL: Server URL (e.g., "http://localhost:8931")
     */
    private fun loadSseServer(): McpServerConfig? {
        val url = dotenv["MCP_HTTP_URL"]
        if (url.isNullOrBlank()) {
            logger.debug("MCP_HTTP_URL not set - skipping SSE server")
            return null
        }

        logger.debug("Found SSE server config: url='$url'")

        return McpServerConfig(
            name = "http-server",
            type = "sse",
            url = url
        )
    }

    /**
     * Check if any MCP servers are configured
     */
    fun hasConfigurations(): Boolean {
        return dotenv["MCP_FILESYSTEM_COMMAND"] != null || dotenv["MCP_HTTP_URL"] != null
    }

    /**
     * Get configuration summary for logging
     */
    fun getSummary(): String {
        val configs = loadConfigurations()
        return when {
            configs.isEmpty() -> "No MCP servers configured"
            configs.size == 1 -> "1 MCP server configured: ${configs[0].name}"
            else -> "${configs.size} MCP servers configured: ${configs.joinToString(", ") { it.name }}"
        }
    }
}
