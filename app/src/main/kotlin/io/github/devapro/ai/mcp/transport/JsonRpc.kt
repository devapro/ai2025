package io.github.devapro.ai.mcp.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Represents a JSON-RPC 2.0 request
 *
 * @property jsonrpc Protocol version (always "2.0")
 * @property id Request identifier for matching responses
 * @property method The RPC method name to invoke
 * @property params Optional parameters for the method
 */
//@Serializable
//data class JsonRpcRequest(
//    val jsonrpc: String = "2.0",
//    val id: Int,
//    val method: String,
//    val params: JsonObject? = null
//)

/**
 * Represents a JSON-RPC 2.0 response
 *
 * @property jsonrpc Protocol version (always "2.0")
 * @property id Request identifier matching the original request
 * @property result Successful result (mutually exclusive with error)
 * @property error Error information (mutually exclusive with result)
 */
//@Serializable
//data class JsonRpcResponse(
//    val jsonrpc: String = "2.0",
//    val id: Int,
//    val result: JsonElement? = null,
//    val error: JsonRpcError? = null
//)

/**
 * Represents a JSON-RPC 2.0 error
 *
 * @property code Error code
 * @property message Human-readable error message
 * @property data Additional error data
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)
