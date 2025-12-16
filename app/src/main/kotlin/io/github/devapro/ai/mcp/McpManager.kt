package io.github.devapro.ai.mcp

import io.github.devapro.ai.mcp.client.McpClient
import io.github.devapro.ai.mcp.config.McpConfig
import io.github.devapro.ai.mcp.model.McpTool
import io.github.devapro.ai.mcp.model.ToolCallResult
import io.github.devapro.ai.mcp.transport.SseTransport
import io.github.devapro.ai.mcp.transport.StdioTransport
import io.ktor.client.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Manager for multiple MCP servers
 *
 * Orchestrates initialization, tool discovery, and tool execution
 * across all configured MCP servers.
 *
 * @param config MCP configuration with server definitions
 * @param httpClient Shared Ktor HTTP client for HTTP transports
 */
class McpManager(
    private val config: McpConfig,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(McpManager::class.java)
    private val clients = mutableMapOf<String, McpClient>()
    private val toolToServer = mutableMapOf<String, String>()

    /**
     * Initialize all enabled MCP servers in parallel
     *
     * Servers that fail to initialize are skipped.
     * The manager continues with successfully initialized servers.
     */
    suspend fun initialize() = coroutineScope {
        logger.info("Initializing MCP Manager with ${config.mcpServers.size} server(s)")

        if (config.mcpServers.isEmpty()) {
            logger.info("No MCP servers configured")
            return@coroutineScope
        }

        // Filter enabled servers
        val enabledServers = config.mcpServers.filter { it.enabled }
        logger.info("Found ${enabledServers.size} enabled server(s)")

        if (enabledServers.isEmpty()) {
            logger.warn("All MCP servers are disabled")
            return@coroutineScope
        }

        // Initialize servers in parallel
        val initJobs = enabledServers.map { serverConfig ->
            async {
                try {
                    logger.info("Creating client for server: ${serverConfig.name}")

                    // Create appropriate transport
                    val transport = when (serverConfig.type) {
                        "stdio" -> {
                            StdioTransport(
                                command = serverConfig.command!!,
                                args = serverConfig.args ?: emptyList(),
                                env = serverConfig.env ?: emptyMap(),
                                timeout = serverConfig.timeout ?: 30_000
                            )
                        }
                        "http" -> {
                            SseTransport(
                                baseUrl = serverConfig.url!!,
                               // headers = serverConfig.headers ?: emptyMap(),
                                httpClient = httpClient,
                               // timeout = serverConfig.timeout ?: 30_000
                            )
                        }
                        else -> {
                            logger.error("Unknown transport type: ${serverConfig.type} for server ${serverConfig.name}")
                            return@async null
                        }
                    }

                    // Create client
                    val client = McpClient(
                        serverName = serverConfig.name,
                        transport = transport
                    )

                    // Initialize client
                    val success = client.initialize()
                    if (success) {
                        logger.info("Server ${serverConfig.name} initialized successfully")
                        serverConfig.name to client
                    } else {
                        logger.error("Failed to initialize server: ${serverConfig.name}")
                        null
                    }
                } catch (e: Exception) {
                    logger.error("Error initializing server ${serverConfig.name}: ${e.message}", e)
                    null
                }
            }
        }

        // Wait for all initialization to complete
        val results = initJobs.awaitAll()

        // Store successfully initialized clients
        results.filterNotNull().forEach { (name, client) ->
            clients[name] = client
        }

        logger.info("Successfully initialized ${clients.size} MCP server(s)")

        // Fetch tools from all servers
        if (clients.isNotEmpty()) {
            fetchAllTools()
        }
    }

    /**
     * Fetch tools from all servers and build tool routing map
     */
    private suspend fun fetchAllTools() = coroutineScope {
        logger.info("Fetching tools from all servers")

        // Fetch tools in parallel
        val toolFetchJobs = clients.map { (serverName, client) ->
            async {
                try {
                    val tools = client.listTools()
                    serverName to tools
                } catch (e: Exception) {
                    logger.error("Error fetching tools from $serverName: ${e.message}", e)
                    serverName to emptyList()
                }
            }
        }

        // Wait for all tool fetches to complete
        val results = toolFetchJobs.awaitAll()

        // Build tool routing map
        results.forEach { (serverName, tools) ->
            tools.forEach { tool ->
                if (toolToServer.containsKey(tool.name)) {
                    logger.warn("Tool name conflict: '${tool.name}' exists in multiple servers. Using first occurrence.")
                } else {
                    toolToServer[tool.name] = serverName
                }
            }
        }

        logger.info("Total tools available: ${toolToServer.size}")
    }

    /**
     * Get all available tools from all servers
     *
     * @return List of all tools
     */
    suspend fun getAllTools(): List<McpTool> = coroutineScope {
        logger.debug("Getting all tools from ${clients.size} server(s)")

        val toolFetchJobs = clients.values.map { client ->
            async {
                try {
                    client.listTools()
                } catch (e: Exception) {
                    logger.error("Error fetching tools: ${e.message}", e)
                    emptyList()
                }
            }
        }

        toolFetchJobs.awaitAll().flatten()
    }

    /**
     * Call a tool by name
     *
     * Automatically routes the call to the correct server.
     *
     * @param name Tool name
     * @param arguments Tool arguments
     * @return Tool execution result
     */
    suspend fun callTool(name: String, arguments: JsonObject?): ToolCallResult {
        // Find which server has this tool
        val serverName = toolToServer[name]
        if (serverName == null) {
            logger.error("Tool not found: $name")
            return ToolCallResult(
                content = listOf(
                    io.github.devapro.ai.mcp.model.ToolContent(
                        type = "text",
                        text = "Error: Tool '$name' not found"
                    )
                ),
                isError = true
            )
        }

        // Get the client for this server
        val client = clients[serverName]
        if (client == null) {
            logger.error("Client not found for server: $serverName")
            return ToolCallResult(
                content = listOf(
                    io.github.devapro.ai.mcp.model.ToolContent(
                        type = "text",
                        text = "Error: Server '$serverName' not available"
                    )
                ),
                isError = true
            )
        }

        // Call the tool
        logger.info("Routing tool '$name' to server: $serverName")
        return client.callTool(name, arguments)
    }

    /**
     * Check if any MCP servers are available
     *
     * @return true if at least one server is connected
     */
    fun isAvailable(): Boolean {
        return clients.isNotEmpty()
    }

    /**
     * Close all MCP clients and cleanup resources
     */
    suspend fun close() = coroutineScope {
        logger.info("Closing MCP Manager")

        val closeJobs = clients.values.map { client ->
            async {
                try {
                    client.close()
                } catch (e: Exception) {
                    logger.error("Error closing client: ${e.message}", e)
                }
            }
        }

        closeJobs.awaitAll()

        clients.clear()
        toolToServer.clear()

        logger.info("MCP Manager closed")
    }
}
