package io.github.devapro.ai.tools.rag

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Interface for RAG (Retrieval-Augmented Generation) search tools
 * Allows different RAG implementations (basic, enhanced, etc.) while maintaining common interface
 */
interface RagSearchToolInterface : Tool {
    /**
     * Create the OpenAI tool definition for search_documents
     * Default implementation that works for all RAG implementations
     */
    override fun createToolDefinition(): OpenAITool {
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
                            put(
                                "description", JsonPrimitive(
                                    "The search query to find relevant documents. " +
                                            "Be specific and detailed for better results."
                                )
                            )
                        }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("query"))
                    }
                }
            )
        )
    }
}