package io.github.devapro.ai.tools

import io.github.devapro.ai.tools.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * MCP Server implementation using stdio transport (JSON-RPC 2.0 over stdin/stdout)
 *
 * This server implements the Model Context Protocol (MCP) and provides tools
 * that can be called by AI agents. Communication happens via newline-delimited
 * JSON messages over stdin/stdout.
 */
class McpServer(
    private val tools: List<Tool>,
    private val serverName: String = "file-tools",
    private val serverVersion: String = "1.0.0"
) {
    private val logger = LoggerFactory.getLogger(McpServer::class.java)
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val input = BufferedReader(InputStreamReader(System.`in`))
    private val output = PrintWriter(System.out, true)

    private var initialized = false

    /**
     * Start the MCP server and listen for requests on stdin
     */
    fun start() {
        logger.info("Starting MCP Server: $serverName v$serverVersion")
        logger.info("Registered tools: ${tools.joinToString(", ") { it.createToolDefinition().function.name }}")

        try {
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) continue

                logger.debug("Received: $line")

                try {
                    val request = json.decodeFromString<JsonRpcRequest>(line)
                    handleRequest(request)
                } catch (e: Exception) {
                    logger.error("Failed to parse request: ${e.message}", e)
                    sendError(null, ErrorCodes.PARSE_ERROR, "Parse error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.error("Server error: ${e.message}", e)
        } finally {
            logger.info("MCP Server stopped")
        }
    }

    /**
     * Handle incoming JSON-RPC request
     */
    private fun handleRequest(request: JsonRpcRequest) {
        try {
            when (request.method) {
                "initialize" -> handleInitialize(request)
                "initialized" -> handleInitialized(request)
                "tools/list" -> handleToolsList(request)
                "tools/call" -> handleToolCall(request)
                else -> {
                    logger.warn("Unknown method: ${request.method}")
                    sendError(request.id, ErrorCodes.METHOD_NOT_FOUND, "Method not found: ${request.method}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling request: ${e.message}", e)
            sendError(request.id, ErrorCodes.INTERNAL_ERROR, "Internal error: ${e.message}")
        }
    }

    /**
     * Handle 'initialize' request
     */
    private fun handleInitialize(request: JsonRpcRequest) {
        logger.info("Handling initialize request")

        if (request.params == null) {
            sendError(request.id, ErrorCodes.INVALID_PARAMS, "Missing params for initialize")
            return
        }

        try {
            val params = json.decodeFromJsonElement<InitializeParams>(request.params)
            logger.info("Client: ${params.clientInfo.name} v${params.clientInfo.version}")
            logger.info("Protocol version: ${params.protocolVersion}")

            val result = InitializeResult(
                protocolVersion = "2024-11-05",
                capabilities = ServerCapabilities(
                    tools = ToolsCapability(listChanged = false)
                ),
                serverInfo = ServerInfo(
                    name = serverName,
                    version = serverVersion
                )
            )

            sendSuccess(request.id, json.encodeToJsonElement(result))
            initialized = true
        } catch (e: Exception) {
            logger.error("Failed to handle initialize: ${e.message}", e)
            sendError(request.id, ErrorCodes.INVALID_PARAMS, "Invalid initialize params: ${e.message}")
        }
    }

    /**
     * Handle 'initialized' notification (no response expected)
     */
    private fun handleInitialized(request: JsonRpcRequest) {
        logger.info("Client sent initialized notification")
        // This is a notification, no response needed
    }

    /**
     * Handle 'tools/list' request
     */
    private fun handleToolsList(request: JsonRpcRequest) {
        logger.info("Handling tools/list request")

        if (!initialized) {
            sendError(request.id, ErrorCodes.INTERNAL_ERROR, "Server not initialized")
            return
        }

        val mcpTools = tools.map { tool ->
            val toolDef = tool.createToolDefinition()
            McpTool(
                name = toolDef.function.name,
                description = toolDef.function.description ?: "",
                inputSchema = toolDef.function.parameters
            )
        }

        val result = ToolsListResult(tools = mcpTools)
        sendSuccess(request.id, json.encodeToJsonElement(result))
    }

    /**
     * Handle 'tools/call' request
     */
    private fun handleToolCall(request: JsonRpcRequest) {
        if (!initialized) {
            sendError(request.id, ErrorCodes.INTERNAL_ERROR, "Server not initialized")
            return
        }

        if (request.params == null) {
            sendError(request.id, ErrorCodes.INVALID_PARAMS, "Missing params for tools/call")
            return
        }

        try {
            val params = json.decodeFromJsonElement<ToolCallParams>(request.params)
            logger.info("Calling tool: ${params.name}")

            val tool = tools.find { it.createToolDefinition().function.name == params.name }
            if (tool == null) {
                sendError(request.id, ErrorCodes.INVALID_PARAMS, "Tool not found: ${params.name}")
                return
            }

            // Execute tool (blocking call in runBlocking since we're already in main thread)
            val resultText = runBlocking {
                try {
                    tool.execute(params.arguments)
                } catch (e: IllegalArgumentException) {
                    logger.error("Invalid arguments for ${params.name}: ${e.message}")
                    throw e
                } catch (e: Exception) {
                    logger.error("Tool execution failed: ${e.message}", e)
                    throw e
                }
            }

            val result = ToolCallResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = resultText
                    )
                ),
                isError = false
            )

            sendSuccess(request.id, json.encodeToJsonElement(result))
        } catch (e: IllegalArgumentException) {
            sendError(request.id, ErrorCodes.INVALID_PARAMS, "Invalid arguments: ${e.message}")
        } catch (e: Exception) {
            sendError(request.id, ErrorCodes.INTERNAL_ERROR, "Tool execution error: ${e.message}")
        }
    }

    /**
     * Send successful JSON-RPC response
     */
    private fun sendSuccess(id: JsonElement?, result: JsonElement) {
        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = id,
            result = result
        )
        val responseJson = json.encodeToString(response)
        logger.debug("Sending response: $responseJson")
        output.println(responseJson)
        output.flush()
    }

    /**
     * Send error JSON-RPC response
     */
    private fun sendError(id: JsonElement?, code: Int, message: String, data: JsonElement? = null) {
        val response = JsonRpcErrorResponse(
            jsonrpc = "2.0",
            id = id,
            error = JsonRpcError(
                code = code,
                message = message,
                data = data
            )
        )
        val responseJson = json.encodeToString(response)
        logger.debug("Sending error: $responseJson")
        output.println(responseJson)
        output.flush()
    }
}
