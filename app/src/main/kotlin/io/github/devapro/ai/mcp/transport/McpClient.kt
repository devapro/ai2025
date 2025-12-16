package io.github.devapro.ai.mcp.transport

import io.github.devapro.ai.mcp.model.JsonRpcRequest
import io.github.devapro.ai.mcp.model.JsonRpcResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-level MCP (Model Context Protocol) client
 *
 * Provides methods for interacting with MCP servers through various transports.
 * Handles the MCP protocol details like initialization, tool discovery, and tool invocation.
 *
 * @property transport The transport layer implementation (SSE, stdio, etc.)
 */
class McpClient(
    private val transport: McpTransport
) {
    private val logger = LoggerFactory.getLogger(McpClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val requestIdCounter = AtomicInteger(0)
    private var isInitialized = false

    /**
     * Initialize the MCP connection
     *
     * Must be called before any other operations.
     *
     * @param clientInfo Information about this client
     * @param protocolVersion MCP protocol version (default: "2024-11-05")
     * @return Initialization result with server information
     */
    suspend fun initialize(
        clientInfo: ClientInfo = ClientInfo(name = "mcp-kotlin-client", version = "1.0.0"),
        protocolVersion: String = "2024-11-05"
    ): InitializeResult {
        logger.info("Initializing MCP client: ${clientInfo.name} v${clientInfo.version}")

        // Initialize transport
        if (!transport.initialize()) {
            throw Exception("Failed to initialize transport")
        }

        // Send initialize request
        val request = JsonRpcRequest(
            id = nextRequestId(),
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", protocolVersion)
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", clientInfo.name)
                    put("version", clientInfo.version)
                }
            }
        )

        val response = transport.send(request)
        checkError(response)

        isInitialized = true
        logger.info("MCP client initialized successfully")

        return json.decodeFromJsonElement(response.result!!)
    }

    /**
     * List available tools from the MCP server
     *
     * @return List of tool definitions
     */
    suspend fun listTools(): List<Tool> {
        checkInitialized()
        logger.debug("Listing available tools")

        val request = JsonRpcRequest(
            id = nextRequestId(),
            method = "tools/list",
            params = buildJsonObject {}
        )

        val response = transport.send(request)
        checkError(response)

        val result = response.result!!.jsonObject
        val tools = result["tools"]?.jsonArray ?: emptyList()

        return tools.map { json.decodeFromJsonElement<Tool>(it) }
    }

    /**
     * Call a tool on the MCP server
     *
     * @param toolName Name of the tool to call
     * @param arguments Arguments to pass to the tool (as a map)
     * @return Tool call result
     */
    suspend fun callTool(toolName: String, arguments: Map<String, Any> = emptyMap()): ToolResult {
        checkInitialized()
        logger.debug("Calling tool: $toolName with arguments: $arguments")

        val request = JsonRpcRequest(
            id = nextRequestId(),
            method = "tools/call",
            params = buildJsonObject {
                put("name", toolName)
                putJsonObject("arguments") {
                    arguments.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Number -> put(key, value)
                            is Boolean -> put(key, value)
                            else -> put(key, value.toString())
                        }
                    }
                }
            }
        )

        val response = transport.send(request)
        checkError(response)

        return json.decodeFromJsonElement(response.result!!)
    }

    /**
     * Close the MCP client and cleanup resources
     */
    suspend fun close() {
        logger.info("Closing MCP client")
        transport.close()
        isInitialized = false
    }

    private fun nextRequestId(): String = requestIdCounter.incrementAndGet().toString()

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Client not initialized. Call initialize() first.")
        }
    }

    private fun checkError(response: JsonRpcResponse) {
        response.error?.let { error ->
            throw McpException(error.code, error.message, error.data)
        }
    }
}

/**
 * Client information
 */
@Serializable
data class ClientInfo(
    val name: String,
    val version: String
)

/**
 * Initialization result from the server
 */
@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: JsonObject,
    val serverInfo: ServerInfo
)

/**
 * Server information
 */
@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

/**
 * Tool definition from the server
 */
@Serializable
data class Tool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject? = null
)

/**
 * Tool call result
 */
@Serializable
data class ToolResult(
    val content: List<JsonObject>,
    val isError: Boolean? = null
)

/**
 * Exception thrown when MCP operations fail
 */
class McpException(
    val code: Int,
    override val message: String,
    val data: JsonElement? = null
) : Exception("MCP Error [$code]: $message")
