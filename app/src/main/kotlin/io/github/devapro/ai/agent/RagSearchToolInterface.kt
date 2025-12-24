package io.github.devapro.ai.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Interface for RAG search tools (both basic and enhanced)
 * Allows ToolProvider to work with either implementation
 */
interface RagSearchToolInterface {
    /**
     * Create the OpenAI tool definition for search_documents
     * Default implementation that works for all RAG implementations
     */
    fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "search_documents",
                description = "Search through indexed documents using semantic similarity. " +
                        "Use this tool when you need to find relevant information from the knowledge base. " +
                        "The search returns the most relevant text chunks that match the query semantically.",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The search query to find relevant documents. " +
                                    "Be specific and detailed for better results."))
                        }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("query"))
                    }
                }
            )
        )
    }

    /**
     * Execute the search with given arguments
     */
    suspend fun executeSearch(args: JsonObject?): String
}
