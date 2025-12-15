package io.github.devapro.ai.mcp

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import org.slf4j.LoggerFactory

/**
 * Component responsible for initializing MCP (Model Context Protocol) tool registries
 *
 * This component:
 * - Parses MCP server configurations
 * - Initializes MCP servers (stdio and SSE transports)
 * - Creates ToolRegistry instances for use with Koog agents
 * - Handles errors gracefully (returns null if initialization fails)
 */
class McpInitializer(
    private val serverConfigs: List<McpServerConfig>
) {
    private val logger = LoggerFactory.getLogger(McpInitializer::class.java)

    /**
     * Initialize MCP tool registry from configured servers
     *
     * @return ToolRegistry with tools from MCP servers, or null if no servers configured or all fail
     */
    suspend fun initialize(): ToolRegistry? {
        if (serverConfigs.isEmpty()) {
            logger.info("No MCP servers configured. Agent will run without external tools.")
            return null
        }

        logger.info("Initializing ${serverConfigs.size} MCP server(s)...")

        return try {
            val registries = serverConfigs.mapNotNull { config ->
                initializeServer(config)
            }

            when {
                registries.isEmpty() -> {
                    logger.warn("No MCP servers could be initialized")
                    null
                }
                registries.size == 1 -> {
                    logger.info("Successfully initialized 1 MCP tool registry")
                    registries.first()
                }
                else -> {
                    logger.info("Successfully initialized ${registries.size} MCP tool registries")
                    // TODO: For multiple servers, implement registry combining
                    // Currently using first registry only
                    logger.warn("Multiple registries found - using first one. Registry combining not yet implemented.")
                    registries.first()
                }
            }
        } catch (e: Exception) {
            logger.error("Error initializing MCP tools: ${e.message}", e)
            null
        }
    }

    /**
     * Initialize a single MCP server and create its tool registry
     */
    private suspend fun initializeServer(config: McpServerConfig): ToolRegistry? {
        return try {
            when (config.type) {
                "stdio" -> initializeStdioServer(config)
                "sse" -> initializeSseServer(config)
                else -> {
                    logger.error("Unknown transport type '${config.type}' for server '${config.name}'")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize MCP server '${config.name}': ${e.message}", e)
            null
        }
    }

    /**
     * Initialize stdio-based MCP server (local process communication)
     */
    private suspend fun initializeStdioServer(config: McpServerConfig): ToolRegistry? {
        logger.info("Starting stdio MCP server: ${config.name}")

        val command = listOf(config.command!!) + config.args
        logger.debug("Command: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command).start()

        // TODO: Fix MCP transport API - method signature unclear in Koog 0.5.4
        // The defaultStdioTransport method doesn't exist or has different signature
        //
        // Expected API (from docs):
        //   val transport = McpToolRegistryProvider.defaultStdioTransport(process)
        //   return McpToolRegistryProvider.fromTransport(
        //       transport = transport,
        //       name = "${config.name}-client",
        //       version = "1.0.0"
        //   )
        //
        // Alternatives to investigate:
        // 1. Check actual agents-mcp-jvm API documentation
        // 2. Use MCP Kotlin SDK directly with StdioClientTransport
        // 3. Examine Koog example code for MCP integration

        logger.error("MCP stdio transport not yet implemented - API investigation needed")
        process.destroy() // Clean up the process
        return null
    }

    /**
     * Initialize SSE-based MCP server (HTTP Server-Sent Events)
     */
    private suspend fun initializeSseServer(config: McpServerConfig): ToolRegistry? {
        logger.info("Connecting to SSE MCP server: ${config.url}")

        // TODO: Fix MCP transport API
        //
        // Expected API (from docs):
        //   val transport = McpToolRegistryProvider.defaultSseTransport(config.url!!)
        //   return McpToolRegistryProvider.fromTransport(
        //       transport = transport,
        //       name = "${config.name}-client",
        //       version = "1.0.0"
        //   )

        logger.error("MCP SSE transport not yet implemented - API investigation needed")
        return null
    }

    /**
     * Clean up MCP resources
     *
     * Note: Koog agents typically handle their own cleanup,
     * but this method is provided for explicit resource management if needed
     */
    fun close() {
        logger.info("Closing MCP initializer")
        // TODO: Add cleanup logic if needed (e.g., stopping processes)
    }
}
