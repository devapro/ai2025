package io.github.devapro.ai.mcp

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.io.asSource
import kotlinx.io.asSink
import kotlinx.io.buffered
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

        return try {
            val command = listOf(config.command!!) + config.args
            logger.debug("Command: ${command.joinToString(" ")}")

            // Start the MCP server process
            val process = ProcessBuilder(command).start()

            // Create MCP client
            val mcpClient = Client(
                clientInfo = Implementation(
                    name = "${config.name}-client",
                    version = "1.0.0"
                )
            )

            // Create stdio transport from process streams
            // This transport communicates with the MCP server via stdin/stdout
            // Convert Java streams to kotlinx-io Source/Sink for the MCP SDK
            val transport = StdioClientTransport(
                input = process.inputStream.asSource().buffered(),
                output = process.outputStream.asSink().buffered()
            )

            // Connect the client to the MCP server
            mcpClient.connect(transport)
            logger.debug("Connected to MCP server '${config.name}'")

            // Create tool registry from the MCP client
            // The registry will discover available tools from the MCP server
            val toolRegistry = McpToolRegistryProvider.fromClient(
                mcpClient = mcpClient
            )

            logger.info("Successfully initialized stdio MCP server '${config.name}'")
            logger.debug("Available tools: ${toolRegistry.tools.map { it.name }}")

            toolRegistry
        } catch (e: Exception) {
            logger.error("Failed to initialize stdio MCP server '${config.name}': ${e.message}", e)
            null
        }
    }

    /**
     * Initialize SSE-based MCP server (HTTP Server-Sent Events)
     */
    private suspend fun initializeSseServer(config: McpServerConfig): ToolRegistry? {
        logger.info("Connecting to SSE MCP server: ${config.url}")

        return try {
            // TODO: Implement SSE transport
            // SSE transport requires different setup - need to investigate the proper API
            // For now, SSE is not implemented
            logger.warn("SSE transport not yet fully implemented for '${config.name}'")
            logger.info("To implement SSE: create Client, connect with SseClientTransport, use fromClient()")
            null
        } catch (e: Exception) {
            logger.error("Failed to connect to SSE MCP server '${config.name}': ${e.message}", e)
            null
        }
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
