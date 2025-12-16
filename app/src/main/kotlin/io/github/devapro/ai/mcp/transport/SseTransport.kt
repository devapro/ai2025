package io.github.devapro.ai.mcp.transport

import io.github.devapro.ai.mcp.model.JsonRpcRequest
import io.github.devapro.ai.mcp.model.JsonRpcResponse
import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * SSE (Server-Sent Events) transport implementation for MCP protocol
 *
 * This transport uses two HTTP endpoints:
 * - GET /sse - for receiving server responses via SSE stream
 * - POST /message - for sending client requests (dynamically discovered from SSE)
 *
 * The message endpoint URL is sent by the server as the first SSE event.
 *
 * @property httpClient Ktor HTTP client instance
 * @property baseUrl Base URL of the MCP server (e.g., "http://localhost:8080")
 * @property sseEndpoint SSE endpoint path (default: "/sse")
 */
class SseTransport(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val sseEndpoint: String = "/sse"
) : McpTransport {

    private val logger = LoggerFactory.getLogger(SseTransport::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var sseJob: Job? = null
    private var messageEndpoint: String? = null
    private val isConnected = MutableStateFlow(false)

    // Map to track pending requests and their response channels
    private val pendingRequests = ConcurrentHashMap<String, Channel<JsonRpcResponse>>()

    override suspend fun initialize(): Boolean {
        logger.info("Initializing SSE transport to $baseUrl$sseEndpoint")

        return try {
            // Start SSE connection in background
            sseJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    httpClient.sse(urlString = "$baseUrl$sseEndpoint") {
                        logger.info("SSE connection established")
                        isConnected.value = true

                        incoming.collect { event ->
                            when (event) {
                                is ServerSentEvent -> {
                                    handleSseEvent(event)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("SSE connection error", e)
                    isConnected.value = false
                } finally {
                    logger.info("SSE connection closed")
                    isConnected.value = false
                }
            }

            // Wait for connection and message endpoint (with timeout)
            withTimeoutOrNull(10.seconds) {
                isConnected.first { it } && messageEndpoint != null
            } != null
        } catch (e: Exception) {
            logger.error("Failed to initialize SSE transport", e)
            false
        }
    }

    override suspend fun send(request: JsonRpcRequest): JsonRpcResponse {
        if (!isConnected.value || messageEndpoint == null) {
            throw IllegalStateException("Transport not connected. Call initialize() first.")
        }

        logger.debug("Sending request: id=${request.id}, method=${request.method}")

        // Create response channel for this request
        val responseChannel = Channel<JsonRpcResponse>(1)
        pendingRequests[request.id.orEmpty()] = responseChannel

        try {
            // Send request to messages endpoint (use full URL from SSE)
            val url = "$baseUrl$messageEndpoint"
            logger.debug("Posting to: $url")
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }

            if (!response.status.isSuccess()) {
                throw Exception("HTTP error: ${response.status}")
            }

            // Wait for response from SSE stream (with timeout)
            return withTimeout(30.seconds) {
                responseChannel.receive()
            }
        } catch (e: Exception) {
            logger.error("Failed to send request", e)
            pendingRequests.remove(request.id)
            throw e
        } finally {
            responseChannel.close()
        }
    }

    override suspend fun close() {
        logger.info("Closing SSE transport")
        isConnected.value = false
        sseJob?.cancel()
        pendingRequests.clear()
    }

    /**
     * Handle incoming SSE events from the server
     */
    private suspend fun handleSseEvent(event: ServerSentEvent) {
        try {
            val data = event.data ?: return
            logger.debug("Received SSE event: $data")

            // First event contains the message endpoint URL
            if (messageEndpoint == null && data.startsWith("/")) {
                messageEndpoint = data
                logger.info("Message endpoint received: $messageEndpoint")
                return
            }

            // Check for session ID in event
            if (event.event == "session") {
                logger.info("Session event received: $data")
                return
            }

            // Try to parse as JSON-RPC response
            try {
                val response = json.decodeFromString<JsonRpcResponse>(data)
                logger.debug("Parsed response: id=${response.id}")

                // Send response to the waiting request
                pendingRequests[response.id]?.send(response)
                pendingRequests.remove(response.id)
            } catch (e: Exception) {
                // Not a JSON-RPC response, might be another type of message
                logger.debug("Received non-JSON-RPC message: $data")
            }
        } catch (e: Exception) {
            logger.error("Failed to handle SSE event", e)
        }
    }
}
