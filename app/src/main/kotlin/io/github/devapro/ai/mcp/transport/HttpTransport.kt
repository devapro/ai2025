package io.github.devapro.ai.mcp.transport

import io.github.devapro.ai.mcp.model.JsonRpcError
import io.github.devapro.ai.mcp.model.JsonRpcRequest
import io.github.devapro.ai.mcp.model.JsonRpcResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * HTTP-based transport for remote MCP servers
 *
 * Sends JSON-RPC requests via HTTP POST to a configured URL.
 * Suitable for cloud-hosted or remote MCP servers.
 *
 * @param url The base URL of the MCP server
 * @param headers Custom HTTP headers (e.g., authentication)
 * @param httpClient Shared Ktor HTTP client
 * @param timeout Timeout in milliseconds for requests (default: 30 seconds)
 */
class HttpTransport(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val httpClient: HttpClient,
    private val timeout: Long = 30_000
) : McpTransport {

    private val logger = LoggerFactory.getLogger(HttpTransport::class.java)

    override suspend fun initialize(): Boolean {
        try {
            logger.info("Initializing HTTP transport: $url")
            // HTTP is connectionless, no initialization needed
            // Could optionally perform a health check here
            logger.info("HTTP transport initialized successfully")
            return true
        } catch (e: Exception) {
            logger.error("Failed to initialize HTTP transport: ${e.message}", e)
            return false
        }
    }

    override suspend fun send(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            withTimeout(timeout) {
                logger.debug("Sending HTTP request to $url: ${request.method}")

                val response: HttpResponse = httpClient.post(url) {
                    contentType(ContentType.Application.Json)

                    // Add custom headers
                    this@HttpTransport.headers.forEach { (key, value) ->
                        header(key, value)
                    }

                    // Set request body
                    setBody(request)
                }

                // Check HTTP status
                if (!response.status.isSuccess()) {
                    logger.error("HTTP request failed with status: ${response.status}")
                    JsonRpcResponse(
                        id = request.id,
                        error = JsonRpcError(
                            code = -32603,
                            message = "HTTP error: ${response.status}"
                        )
                    )
                } else {
                    // Parse response
                    val jsonRpcResponse = response.body<JsonRpcResponse>()
                    logger.debug("Received HTTP response: ${jsonRpcResponse.id}")
                    jsonRpcResponse
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error("HTTP request timed out after ${timeout}ms")
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32603,
                    message = "Request timed out after ${timeout}ms"
                )
            )
        } catch (e: Exception) {
            logger.error("Error sending HTTP request: ${e.message}", e)
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32603,
                    message = "Internal error: ${e.message}"
                )
            )
        }
    }

    override suspend fun close() {
        // HTTP is connectionless, no cleanup needed
        // Don't close the shared HTTP client
        logger.info("HTTP transport closed (no-op)")
    }
}
