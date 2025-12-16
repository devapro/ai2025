package io.github.devapro.ai.mcp.transport

import io.github.devapro.ai.mcp.model.JsonRpcError
import io.github.devapro.ai.mcp.model.JsonRpcRequest
import io.github.devapro.ai.mcp.model.JsonRpcResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

/**
 * MCP HTTP transport implementation using official Kotlin SDK
 *
 * This transport uses StreamableHttpClientTransport from the MCP Kotlin SDK which supports:
 * - Multiple response modes (JSON, SSE inline, SSE separate)
 * - Session management with mcp-session-id header
 * - Resumption tokens for connection recovery
 * - Graceful degradation when SSE is not supported
 *
 * Protocol flow:
 * 1. Client sends JSON-RPC initialize request via POST
 * 2. Server can respond with direct JSON or SSE stream
 * 3. All subsequent requests use the same pattern
 * 4. SDK handles session lifecycle automatically
 *
 * @param url The MCP server endpoint URL (e.g., http://localhost:8080/mcp)
 * @param headers Custom HTTP headers for authentication/authorization
 * @param httpClient Shared Ktor HTTP client instance (must have SSE plugin installed)
 * @param timeout Request timeout in milliseconds (default: 30 seconds)
 */
class HttpTransport(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val httpClient: HttpClient,
    private val timeout: Long = 30_000
) : McpTransport {

    private val logger = LoggerFactory.getLogger(HttpTransport::class.java)

    private lateinit var sdkTransport: StreamableHttpClientTransport
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<JSONRPCMessage>>()
    private var initialized = false
    private var requestCounter = 0

    companion object {
        private const val MCP_PROTOCOL_VERSION = "2024-11-05"
    }

    override suspend fun initialize(): Boolean {
        return try {
            logger.info("=== Initializing HTTP Transport (SDK) ===")
            logger.info("URL: $url")
            logger.info("Protocol: MCP $MCP_PROTOCOL_VERSION")

            // Create SDK transport with custom headers
            sdkTransport = StreamableHttpClientTransport(
                client = httpClient,
                url = url,
                reconnectionTime = null,  // No auto-reconnect for now
                requestBuilder = {
                    // Apply custom headers
                    headers@this@HttpTransport.headers.forEach { (key, value) ->
                        header(key, value)
                    }
                }
            )

            // Setup message handler - receives responses from server
            sdkTransport.onMessage { message ->
                handleSdkMessage(message)
            }

            // Setup error handler
            sdkTransport.onError { error ->
                when (error) {
                    is StreamableHttpError -> {
                        logger.error("SDK Transport HTTP error:")
                        logger.error("  Error type: StreamableHttpError")
                        logger.error("  Message: ${error.message}")
                        logger.error("  Cause: ${error.cause?.message}")
                        logger.error("  Stack trace:", error)
                        error.cause?.let { cause ->
                            logger.error("  Cause stack trace:", cause)
                        }
                    }
                    else -> {
                        logger.error("SDK Transport error: ${error.message}", error)
                    }
                }
                // Complete all pending requests with error
                pendingRequests.values.forEach { deferred ->
                    deferred.completeExceptionally(error)
                }
                pendingRequests.clear()
            }

            // Setup close handler
            sdkTransport.onClose {
                logger.info("SDK Transport connection closed")
            }

            // Start the transport (opens connection if needed)
            sdkTransport.start()

            // Send initialize request
            val initRequest = JsonRpcRequest(
                id = "init-${++requestCounter}",
                method = "initialize",
                params = kotlinx.serialization.json.buildJsonObject {
                    put("protocolVersion", kotlinx.serialization.json.JsonPrimitive(MCP_PROTOCOL_VERSION))
                    put("capabilities", kotlinx.serialization.json.buildJsonObject { })
                    put("clientInfo", kotlinx.serialization.json.buildJsonObject {
                        put("name", kotlinx.serialization.json.JsonPrimitive("kotlin-mcp-client"))
                        put("version", kotlinx.serialization.json.JsonPrimitive("1.0.0"))
                    })
                }
            )

            logger.debug("Sending initialize request...")
            val response = send(initRequest)

            // Check for errors
            if (response.error != null) {
                logger.error("Initialize failed: ${response.error.message}")
                return false
            }

            // Check for result
            if (response.result == null) {
                logger.error("Initialize failed: no result in response")
                return false
            }

            logger.info("Initialize successful")
            logger.debug("Server capabilities: ${response.result}")

            initialized = true
            logger.info("=== HTTP Transport Ready (SDK) ===")
            true

        } catch (e: Exception) {
            logger.error("Failed to initialize HTTP transport: ${e.message}", e)
            false
        }
    }

    override suspend fun send(request: JsonRpcRequest): JsonRpcResponse {
        val requestId = request.id ?: return sendNotification(request)

        return try {
            logger.info(">>> REQUEST: ${request.method} (id=$requestId)")
            logger.debug("Request params: ${request.params}")

            // Create SDK message
            val sdkMessage = request.toSdkMessage()

            // Create deferred for response
            val deferred = CompletableDeferred<JSONRPCMessage>()
            pendingRequests[requestId] = deferred

            // Send message via SDK transport
            withTimeout(timeout) {
                try {
                    logger.debug("Calling SDK transport.send()...")
                    sdkTransport.send(sdkMessage, resumptionToken = null)
                    logger.debug("SDK transport.send() completed")
                } catch (e: StreamableHttpError) {
                    logger.error("StreamableHttpError during send:")
                    logger.error("  URL: $url")
                    logger.error("  Method: ${request.method}")
                    logger.error("  Error message: ${e.message}")
                    logger.error("  Cause: ${e.cause?.message}")
                    e.cause?.let { cause ->
                        logger.error("  Root cause type: ${cause::class.simpleName}")
                        logger.error("  Root cause stack:", cause)
                    }
                    throw e
                }

                // Wait for response
                logger.debug("Waiting for response...")
                val response = deferred.await()
                logger.debug("Response received")
                response.toCustomResponse(requestId)
            }

        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            logger.error("Request timeout (${timeout}ms): ${request.method} (id=$requestId)")
            createErrorResponse(requestId, -32603, "Request timeout after ${timeout}ms")

        } catch (e: StreamableHttpError) {
            pendingRequests.remove(requestId)
            logger.error("StreamableHttpError caught in send(): ${e.message}", e)
            logger.error("  URL was: $url")
            logger.error("  Request method: ${request.method}")
            val causeMessage = e.cause?.message ?: "Unknown cause"
            createErrorResponse(requestId, -32603, "HTTP transport error: $causeMessage")

        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            logger.error("Request failed: ${request.method} (id=$requestId) - ${e.message}", e)
            logger.error("  Exception type: ${e::class.simpleName}")
            logger.error("  URL: $url")
            createErrorResponse(requestId, -32603, "Internal error: ${e.message}")
        }
    }

    override suspend fun close() {
        if (!initialized) return
        logger.info("Closing HTTP transport")

        // Complete all pending requests with error
        pendingRequests.values.forEach { deferred ->
            deferred.completeExceptionally(CancellationException("Transport closed"))
        }
        pendingRequests.clear()

        // Close SDK transport
        sdkTransport.close()
        initialized = false
        logger.info("HTTP transport closed")
    }

    /**
     * Send a notification (fire-and-forget, no response expected)
     */
    private suspend fun sendNotification(request: JsonRpcRequest): JsonRpcResponse {
        logger.info(">>> NOTIFICATION: ${request.method}")
        logger.debug("Notification params: ${request.params}")

        val sdkMessage = JSONRPCNotification(
            method = request.method,
            params = request.params
        )

        sdkTransport.send(sdkMessage, resumptionToken = null)

        // Notifications don't expect responses
        return JsonRpcResponse(
            id = null,
            result = null,
            jsonrpc = "2.0"
        )
    }

    /**
     * Handle incoming messages from SDK transport
     */
    private suspend fun handleSdkMessage(message: JSONRPCMessage) {
        when (message) {
            is JSONRPCResponse -> {
                val requestId = message.id.toString()
                val deferred = pendingRequests.remove(requestId)
                if (deferred != null) {
                    logger.info("<<< RESPONSE: (id=$requestId)")
                    logger.debug("Response result: ${message.result}")
                    deferred.complete(message)
                } else {
                    logger.warn("Received response for unknown request: $requestId")
                }
            }
            is JSONRPCError -> {
                val requestId = message.id?.toString()
                val deferred = requestId?.let { pendingRequests.remove(it) }
                if (deferred != null) {
                    logger.error("<<< ERROR: ${message.error.message} (id=$requestId)")
                    logger.debug("Error details: ${message.error}")
                    deferred.complete(message)
                } else {
                    logger.error("Received error for unknown request: ${message.error.message}")
                }
            }
            is JSONRPCNotification -> {
                logger.info("<<< NOTIFICATION: ${message.method}")
                logger.debug("Notification params: ${message.params}")
                // Handle server notifications if needed in the future
            }
            is JSONRPCRequest -> {
                logger.warn("Received request from server (not supported in MCP): ${message.method}")
                // MCP servers should not send requests to clients
            }
        }
    }

    /**
     * Helper: Creates error response
     */
    private fun createErrorResponse(id: String?, code: Int, message: String): JsonRpcResponse {
        return JsonRpcResponse(
            id = id,
            error = JsonRpcError(code = code, message = message),
            jsonrpc = "2.0"
        )
    }
}

// Extension functions for type conversion between custom types and SDK types

/**
 * Convert custom JsonRpcRequest to SDK JSONRPCMessage
 */
private fun JsonRpcRequest.toSdkMessage(): JSONRPCMessage {
    return if (id == null) {
        // No ID = notification
        JSONRPCNotification(
            method = method,
            params = params
        )
    } else {
        // Has ID = request expecting response
        JSONRPCRequest(
            id = id,
            method = method,
            params = params
        )
    }
}

/**
 * Convert SDK JSONRPCMessage to custom JsonRpcResponse
 */
private fun JSONRPCMessage.toCustomResponse(requestId: String?): JsonRpcResponse {
    return when (this) {
        is JSONRPCResponse -> JsonRpcResponse(
            id = requestId,
            result = result as? JsonElement,
            jsonrpc = jsonrpc
        )
        is JSONRPCError -> JsonRpcResponse(
            id = requestId,
            error = io.github.devapro.ai.mcp.model.JsonRpcError(
                code = error.code,
                message = error.message,
                data = error.data as? JsonElement
            ),
            jsonrpc = jsonrpc
        )
        else -> throw IllegalArgumentException("Unexpected message type: ${this::class.simpleName}")
    }
}
