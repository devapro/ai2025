package io.github.devapro.ai.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OpenAIMessage(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: String? = null,  // Nullable for tool call messages
    @SerialName("tool_calls")
    val toolCalls: List<OpenAIToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
data class OpenAIRequest(
    @SerialName("model")
    val model: String,
    @SerialName("messages")
    val messages: List<OpenAIMessage>,
    @SerialName("temperature")
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
    @SerialName("stream")
    val stream: Boolean = false,
    @SerialName("tools")
    val tools: List<OpenAITool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null
)

@Serializable
data class ResponseFormat(
    @SerialName("type")
    val type: String  // "json_object" or "text"
)

@Serializable
data class OpenAIResponse(
    @SerialName("choices")
    val choices: List<OpenAIChoice>,
    @SerialName("usage")
    val usage: TokenUsage? = null
)

@Serializable
data class OpenAIChoice(
    @SerialName("message")
    val message: OpenAIMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

// OpenAI Function Calling Data Structures

@Serializable
data class OpenAITool(
    @SerialName("type")
    val type: String = "function",
    @SerialName("function")
    val function: OpenAIFunction
)

@Serializable
data class OpenAIFunction(
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("parameters")
    val parameters: JsonObject
)

@Serializable
data class OpenAIToolCall(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String = "function",
    @SerialName("function")
    val function: OpenAIFunctionCall
)

@Serializable
data class OpenAIFunctionCall(
    @SerialName("name")
    val name: String,
    @SerialName("arguments")
    val arguments: String  // JSON string
)

@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

// OpenAI Error Response
@Serializable
data class OpenAIErrorResponse(
    @SerialName("error")
    val error: OpenAIError
)

@Serializable
data class OpenAIError(
    @SerialName("message")
    val message: String,
    @SerialName("type")
    val type: String? = null,
    @SerialName("code")
    val code: String? = null
)
