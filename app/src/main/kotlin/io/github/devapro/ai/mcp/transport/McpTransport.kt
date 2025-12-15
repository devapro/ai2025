package io.github.devapro.ai.mcp.transport

import io.github.devapro.ai.mcp.model.JsonRpcRequest
import io.github.devapro.ai.mcp.model.JsonRpcResponse

/**
 * Transport layer interface for MCP protocol communication
 *
 * Implementations handle the low-level communication with MCP servers
 * using different transport mechanisms (stdio, HTTP, SSE, etc.)
 */
interface McpTransport {
    /**
     * Send a JSON-RPC request and receive a response
     *
     * @param request The JSON-RPC request to send
     * @return The JSON-RPC response from the server
     * @throws Exception if communication fails
     */
    suspend fun send(request: JsonRpcRequest): JsonRpcResponse

    /**
     * Initialize the transport connection
     *
     * For stdio: launches the process
     * For HTTP: may perform health check or authentication
     *
     * @return true if initialization succeeded, false otherwise
     */
    suspend fun initialize(): Boolean

    /**
     * Close the transport connection and cleanup resources
     *
     * For stdio: terminates the process
     * For HTTP: closes any persistent connections
     */
    suspend fun close()
}
