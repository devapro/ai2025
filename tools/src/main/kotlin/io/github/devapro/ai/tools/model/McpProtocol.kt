package io.github.devapro.ai.tools.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON-RPC 2.0 Request
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject? = null
)

/**
 * JSON-RPC 2.0 Response (success)
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null
)

/**
 * JSON-RPC 2.0 Error Response
 */
@Serializable
data class JsonRpcErrorResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val error: JsonRpcError
)

/**
 * JSON-RPC 2.0 Error object
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * MCP Initialize Request Parameters
 */
@Serializable
data class InitializeParams(
    val protocolVersion: String,
    val capabilities: ClientCapabilities,
    val clientInfo: ClientInfo
)

/**
 * MCP Client Capabilities
 */
@Serializable
data class ClientCapabilities(
    val roots: RootsCapability? = null,
    val sampling: SamplingCapability? = null
)

@Serializable
data class RootsCapability(
    val listChanged: Boolean = false
)

@Serializable
data class SamplingCapability(
    val supported: Boolean = false
)

/**
 * MCP Client Info
 */
@Serializable
data class ClientInfo(
    val name: String,
    val version: String
)

/**
 * MCP Initialize Response Result
 */
@Serializable
data class InitializeResult(
    val protocolVersion: String = "2024-11-05",
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

/**
 * MCP Server Capabilities
 */
@Serializable
data class ServerCapabilities(
    val tools: ToolsCapability? = null
)

@Serializable
data class ToolsCapability(
    val listChanged: Boolean = false
)

/**
 * MCP Server Info
 */
@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

/**
 * MCP Tool Definition
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

/**
 * Tools List Response Result
 */
@Serializable
data class ToolsListResult(
    val tools: List<McpTool>
)

/**
 * Tool Call Parameters
 */
@Serializable
data class ToolCallParams(
    val name: String,
    val arguments: JsonObject? = null
)

/**
 * Tool Call Result
 */
@Serializable
data class ToolCallResult(
    val content: List<ToolContent>,
    val isError: Boolean = false
)

/**
 * Tool Content (text, image, resource, etc.)
 */
@Serializable
data class ToolContent(
    val type: String,
    val text: String? = null
)

/**
 * Standard JSON-RPC Error Codes
 */
object ErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}
