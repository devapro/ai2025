package io.github.devapro.ai.mcp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON-RPC 2.0 Request
 * Base structure for all MCP protocol requests
 */
@Serializable
data class JsonRpcRequest(
    @SerialName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerialName("id")
    val id: String,
    @SerialName("method")
    val method: String,
    @SerialName("params")
    val params: JsonElement? = null
)

/**
 * JSON-RPC 2.0 Response
 * Base structure for all MCP protocol responses
 */
@Serializable
data class JsonRpcResponse(
    @SerialName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerialName("id")
    val id: String?,
    @SerialName("result")
    val result: JsonElement? = null,
    @SerialName("error")
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 Error
 * Standard error response structure
 */
@Serializable
data class JsonRpcError(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
    @SerialName("data")
    val data: JsonElement? = null
)

/**
 * MCP Tool Definition
 * Represents a tool available from an MCP server
 */
@Serializable
data class McpTool(
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("inputSchema")
    val inputSchema: JsonObject
)

/**
 * Response from tools/list method
 */
@Serializable
data class ToolListResponse(
    @SerialName("tools")
    val tools: List<McpTool>
)

/**
 * Request parameters for tools/call method
 */
@Serializable
data class ToolCallParams(
    @SerialName("name")
    val name: String,
    @SerialName("arguments")
    val arguments: JsonObject? = null
)

/**
 * Result from tool execution
 * Contains the output or error information
 */
data class ToolCallResult(
    val content: List<ToolContent>,
    val isError: Boolean = false
)

/**
 * Response from tools/call method
 */
@Serializable
data class ToolCallResponse(
    @SerialName("content")
    val content: List<ToolContent>,
    @SerialName("isError")
    val isError: Boolean? = null
)

/**
 * Tool content - can be text, image, or resource
 * Supports multiple content types as per MCP spec
 */
@Serializable
data class ToolContent(
    @SerialName("type")
    val type: String,  // "text", "image", "resource"
    @SerialName("text")
    val text: String? = null,
    @SerialName("data")
    val data: String? = null,  // Base64 encoded for images
    @SerialName("mimeType")
    val mimeType: String? = null,
    @SerialName("uri")
    val uri: String? = null,
    @SerialName("blob")
    val blob: String? = null
)
