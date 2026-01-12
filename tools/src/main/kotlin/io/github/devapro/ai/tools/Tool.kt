package io.github.devapro.ai.tools

import io.github.devapro.ai.tools.model.OpenAITool
import kotlinx.serialization.json.JsonObject

/**
 * Interface that all tools must implement
 * Tools can be integrated directly in code or exposed via MCP server
 */
interface Tool {
    /**
     * Create the OpenAI tool definition for function calling
     * This defines how the AI sees and calls this tool
     *
     * @return OpenAITool with function name, description, and parameter schema
     */
    fun createToolDefinition(): OpenAITool

    /**
     * Execute the tool with given arguments
     *
     * @param arguments The input arguments as a JSON object (or null if no args)
     * @return The result of the tool execution as a string
     * @throws IllegalArgumentException if arguments are invalid
     * @throws Exception if tool execution fails
     */
    suspend fun execute(arguments: JsonObject?): String
}
