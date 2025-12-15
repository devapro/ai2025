package io.github.devapro.ai.mcp.client

import io.github.devapro.ai.mcp.model.*
import io.github.devapro.ai.mcp.transport.McpTransport
import kotlinx.coroutines.delay
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
        encodeDefaults = true  // Include default values like jsonrpc = "2.0"
        explicitNulls = false  // Omit null fields (critical for JSON-RPC notifications)
    }

    private val mutex = Mutex()
    private var toolsCache: List<McpTool>? = null
    private var initialized = false

    /**
     * Initialize the client and connect to the server
     * Performs the MCP handshake: initialize request -> initialized notification
     *
     * @return true if initialization succeeded
     */
    suspend fun initialize(): Boolean = mutex.withLock {
        if (initialized) {
            logger.warn("Client already initialized: $serverName")
            return@withLock true
        }

        logger.info("Initializing MCP client for server: $serverName")
        logger.info("DEBUG: About to initialize transport for $serverName")

        // Step 1: Initialize transport (start process/connection)
        val transportReady = transport.initialize()
        logger.info("DEBUG: Transport initialize returned: $transportReady")
        if (!transportReady) {
            logger.error("Failed to initialize transport: $serverName")
            return@withLock false
        }

        try {
            // Give the server a moment to start up before sending requests
            kotlinx.coroutines.delay(500)

            // Step 2: Send MCP initialize request
            logger.info("Sending MCP initialize request to $serverName")
            val initRequest = JsonRpcRequest(
                id = UUID.randomUUID().toString(),
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("clientInfo", buildJsonObject {
                        put("name", "ai-telegram-bot")
                        put("version", "1.0.0")
                    })
                    put("capabilities", buildJsonObject {
                        // Empty capabilities for now
                    })
                }
            )

            logger.info("Waiting for initialize response from $serverName (timeout: 30s)...")
            val initResponse = transport.send(initRequest)

            if (initResponse.error != null) {
                logger.error("❌ Initialize request failed: ${initResponse.error.message}")
                logger.error("Error code: ${initResponse.error.code}, data: ${initResponse.error.data}")
                return@withLock false
            }

            logger.info("✅ Initialize response received from $serverName")

            // Step 3: Send initialized notification (fire-and-forget)
            logger.info("Sending initialized notification to $serverName")
            val initializedNotification = JsonRpcRequest(
                id = null,  // No ID = notification (no response expected per JSON-RPC 2.0)
                method = "notifications/initialized",
                params = null
            )

            // Send as notification without waiting for response
            if (transport is io.github.devapro.ai.mcp.transport.StdioTransport) {
                transport.sendNotification(initializedNotification)
            } else {
                // For HTTP transport, just send normally (they handle notifications differently)
                try {
                    transport.send(initializedNotification)
                } catch (e: Exception) {
                    logger.debug("Initialized notification error (ignored): ${e.message}")
                }
            }
            logger.info("Initialized notification sent")

            initialized = true
            logger.info("✅ MCP handshake completed successfully: $serverName")
            true

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error("❌ Timeout during MCP handshake with $serverName (30s exceeded)")
            logger.error("Server may not be responding or doesn't support MCP protocol")
            false
        } catch (e: Exception) {
            logger.error("❌ Failed during MCP handshake with $serverName: ${e.message}", e)
            false
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
            logger.debug("Sending tools/list request to $serverName")
            val response = transport.send(request)

            // Handle error
            if (response.error != null) {
                logger.error("Error listing tools from $serverName: ${response.error.message}")
                logger.error("Error code: ${response.error.code}, data: ${response.error.data}")
                return emptyList()
            }

            // Parse result
            val result = response.result
            if (result == null) {
                logger.error("No result in tools/list response from $serverName")
                logger.error("Full response: $response")
                return emptyList()
            }

            try {
                logger.debug("Parsing tools/list result: $result")
                val toolListResponse = json.decodeFromString<ToolListResponse>(result.toString())
                val tools = toolListResponse.tools

                logger.info("✅ Found ${tools.size} tools from $serverName:")
                tools.forEach { tool ->
                    logger.info("  - ${tool.name}: ${tool.description ?: "No description"}")
                }

                // Cache the tools
                toolsCache = tools

                tools
            } catch (e: Exception) {
                logger.error("Failed to parse tools/list response from $serverName: ${e.message}", e)
                logger.error("Raw result: $result")
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
