package io.github.devapro.ai.agent

import io.github.devapro.ai.utils.rag.EmbeddingGenerator
import io.github.devapro.ai.utils.rag.VectorDatabase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Component for RAG (Retrieval-Augmented Generation) search tool
 * Handles both tool definition and execution
 */
class RagSearchTool(
    private val vectorDatabase: VectorDatabase,
    private val embeddingGenerator: EmbeddingGenerator,
    private val ragTopK: Int = 5,
    private val ragMinSimilarity: Double = 0.7
) : RagSearchToolInterface {
    private val logger = LoggerFactory.getLogger(RagSearchTool::class.java)

    /**
     * Execute the search_documents tool: query → embedding → similarity search → results
     */
    override suspend fun executeSearch(args: JsonObject?): String {
        try {
            // Extract query from arguments
            val query = args?.get("query")?.jsonPrimitive?.content
            if (query.isNullOrBlank()) {
                return "Error: Query parameter is required"
            }

            logger.info("Searching documents for query: $query")

            // Step 1: Generate embedding for the query
            val queryEmbedding = embeddingGenerator.generateEmbedding(query)
            logger.info("Generated embedding for query (${queryEmbedding.vector.size} dimensions)")

            // Step 2: Search for similar vectors in the database
            val searchResults = vectorDatabase.search(
                queryEmbedding = queryEmbedding.vector,
                topK = ragTopK,
                minSimilarity = ragMinSimilarity
            )

            logger.info("Found ${searchResults.size} results with similarity >= $ragMinSimilarity")

            // Step 3: Format results for LLM
            if (searchResults.isEmpty()) {
                return "No relevant documents found for query: \"$query\""
            }

            // Extract unique sources from results
            val sources = searchResults.mapNotNull { result ->
                result.metadata?.get("file")
            }.distinct()

            val formattedResults = buildString {
                appendLine("Found ${searchResults.size} relevant document(s):\n")

                searchResults.forEachIndexed { index, result ->
                    appendLine("--- Document ${index + 1} (Similarity: ${"%.3f".format(result.similarity)}) ---")

                    // Add source file if available
                    result.metadata?.get("file")?.let { file ->
                        appendLine("Source: $file")
                    }

                    // Add heading context if available
                    result.metadata?.get("heading")?.let { heading ->
                        appendLine("Section: $heading")
                    }

                    appendLine()
                    appendLine(result.text)
                    appendLine()
                }

                appendLine("---")
                appendLine("Use the information from these documents to answer the user's question.")

                // Add sources summary
                if (sources.isNotEmpty()) {
                    appendLine()
                    appendLine("Sources used:")
                    sources.forEach { source ->
                        appendLine("- $source")
                    }
                }
            }

            return formattedResults

        } catch (e: Exception) {
            logger.error("Error executing search_documents: ${e.message}", e)
            return "Error searching documents: ${e.message}"
        }
    }
}
