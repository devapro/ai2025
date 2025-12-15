package io.github.devapro.ai.mcp.client

import io.github.devapro.ai.mcp.model.*
import io.github.devapro.ai.mcp.transport.McpTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.*

/**
 * MCP Client for communicating with a single MCP server
 *
 * Handles tool discovery, caching, and execution.
 * Thread-safe for concurrent requests.
 *
 * @param serverName Name of the server (for logging)
 * @param transport Transport layer for communication
 */
class McpClient(
    private val serverName: String,
    private val transport: McpTransport
) {
    private val logger = LoggerFactory.getLogger(McpClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val mutex = Mutex()
    private var toolsCache: List<McpTool>? = null
    private var initialized = false

    /**
     * Initialize the client and connect to the server
     *
     * @return true if initialization succeeded
     */
    suspend fun initialize(): Boolean {
        return mutex.withLock {
            if (initialized) {
                logger.warn("Client already initialized: $serverName")
                return true
            }

            logger.info("Initializing MCP client for server: $serverName")
            val success = transport.initialize()

            if (success) {
                initialized = true
                logger.info("MCP client initialized successfully: $serverName")
            } else {
                logger.error("Failed to initialize MCP client: $serverName")
            }

            success
        }
    }

    /**
     * List all available tools from the server
     *
     * Results are cached after the first call.
     * Use clearCache() to invalidate the cache.
     *
     * @return List of available tools
     */
    suspend fun listTools(): List<McpTool> {
        return mutex.withLock {
            // Return cached tools if available
            toolsCache?.let {
                logger.debug("Returning cached tools for $serverName (${it.size} tools)")
                return it
            }

            logger.info("Fetching tools from server: $serverName")

            // Create tools/list request
            val request = JsonRpcRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/list",
                params = null
            )

            // Send request
            val response = transport.send(request)

            // Handle error
            if (response.error != null) {
                logger.error("Error listing tools from $serverName: ${response.error.message}")
                return emptyList()
            }

            // Parse result
            val result = response.result
            if (result == null) {
                logger.error("No result in tools/list response from $serverName")
                return emptyList()
            }

            try {
                val toolListResponse = json.decodeFromString<ToolListResponse>(result.toString())
                val tools = toolListResponse.tools

                logger.info("Found ${tools.size} tools from $serverName:")
                tools.forEach { tool ->
                    logger.info("  - ${tool.name}: ${tool.description ?: "No description"}")
                }

                // Cache the tools
                toolsCache = tools

                tools
            } catch (e: Exception) {
                logger.error("Failed to parse tools/list response from $serverName: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Call a tool with given arguments
     *
     * @param name Tool name
     * @param arguments Tool arguments as JsonObject
     * @return Tool execution result
     */
    suspend fun callTool(name: String, arguments: JsonObject?): ToolCallResult {
        logger.info("Calling tool '$name' on server $serverName")
        logger.debug("Tool arguments: $arguments")

        // Create tools/call request
        val params = buildJsonObject {
            put("name", name)
            if (arguments != null) {
                put("arguments", arguments)
            }
        }

        val request = JsonRpcRequest(
            id = UUID.randomUUID().toString(),
            method = "tools/call",
            params = params
        )

        // Send request
        val response = transport.send(request)

        // Handle error
        if (response.error != null) {
            logger.error("Error calling tool '$name' on $serverName: ${response.error.message}")
            return ToolCallResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error: ${response.error.message}"
                    )
                ),
                isError = true
            )
        }

        // Parse result
        val result = response.result
        if (result == null) {
            logger.error("No result in tools/call response from $serverName")
            return ToolCallResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error: No result from tool execution"
                    )
                ),
                isError = true
            )
        }

        return try {
            val toolCallResponse = json.decodeFromString<ToolCallResponse>(result.toString())
            logger.info("Tool '$name' executed successfully on $serverName")
            logger.debug("Tool result: ${toolCallResponse.content}")

            ToolCallResult(
                content = toolCallResponse.content,
                isError = toolCallResponse.isError ?: false
            )
        } catch (e: Exception) {
            logger.error("Failed to parse tools/call response from $serverName: ${e.message}", e)
            ToolCallResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error: Failed to parse tool result: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Clear the tools cache
     *
     * Forces a fresh fetch on next listTools() call
     */
    suspend fun clearCache() {
        mutex.withLock {
            logger.info("Clearing tools cache for $serverName")
            toolsCache = null
        }
    }

    /**
     * Close the client and transport
     */
    suspend fun close() {
        mutex.withLock {
            if (!initialized) {
                return
            }

            logger.info("Closing MCP client: $serverName")
            transport.close()
            initialized = false
            toolsCache = null
            logger.info("MCP client closed: $serverName")
        }
    }
}
