package io.github.devapro.ai.tools.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI Tool definition for function calling
 * Used to define tools that can be called by the AI
 */
@Serializable
data class OpenAITool(
    @SerialName("type")
    val type: String = "function",
    @SerialName("function")
    val function: OpenAIFunction
)

/**
 * OpenAI Function definition
 * Contains the name, description, and parameter schema for a tool
 */
@Serializable
data class OpenAIFunction(
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("parameters")
    val parameters: JsonObject
)
