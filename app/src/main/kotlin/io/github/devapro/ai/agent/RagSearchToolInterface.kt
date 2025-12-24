package io.github.devapro.ai.agent

import kotlinx.serialization.json.JsonObject

/**
 * Interface for RAG search tools (both basic and enhanced)
 * Allows ToolProvider to work with either implementation
 */
interface RagSearchToolInterface {
    /**
     * Create the OpenAI tool definition
     */
    fun createToolDefinition(): OpenAITool

    /**
     * Execute the search with given arguments
     */
    suspend fun executeSearch(args: JsonObject?): String
}
