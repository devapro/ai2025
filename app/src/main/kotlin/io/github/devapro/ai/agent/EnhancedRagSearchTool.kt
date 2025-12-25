package io.github.devapro.ai.agent

import io.github.devapro.ai.utils.rag.EmbeddingGenerator
import io.github.devapro.ai.utils.rag.SearchResult
import io.github.devapro.ai.utils.rag.VectorDatabase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Enhanced RAG search tool with query expansion, LLM re-ranking, and context compression
 * Significantly improves retrieval quality over basic vector search
 */
class EnhancedRagSearchTool(
    private val vectorDatabase: VectorDatabase,
    private val embeddingGenerator: EmbeddingGenerator,
    private val resultsRefiner: RagResultsRefiner,
    private val queryExpander: QueryExpander,
    private val contextCompressor: ContextCompressor,
    private val ragTopK: Int = 10,                       // Retrieve more initially
    private val ragMinSimilarity: Double = 0.6,          // Lower threshold (filtering happens later)
    private val finalTopK: Int = 3,                      // Return fewer after refinement
    private val enableQueryExpansion: Boolean = true,
    private val enableReranking: Boolean = true,
    private val enableCompression: Boolean = true
) : RagSearchToolInterface {
    private val logger = LoggerFactory.getLogger(EnhancedRagSearchTool::class.java)

    /**
     * Execute enhanced search with all improvements
     */
    override suspend fun executeSearch(args: JsonObject?): String {
        val startTime = System.currentTimeMillis()

        try {
            // Extract query from arguments
            val query = args?.get("query")?.jsonPrimitive?.content
            if (query.isNullOrBlank()) {
                return "Error: Query parameter is required"
            }

            logger.info("=== Enhanced RAG Search ===")
            logger.info("Query: '$query'")
            logger.info("Config: topK=$ragTopK, finalTopK=$finalTopK, expansion=$enableQueryExpansion, reranking=$enableReranking, compression=$enableCompression")

            // Step 1: Query Expansion (optional)
            val queries = if (enableQueryExpansion) {
                logger.info("Step 1: Query Expansion")
                queryExpander.expandQuery(query, numVariations = 2)
            } else {
                logger.info("Step 1: Skipped (query expansion disabled)")
                listOf(query)
            }

            logger.info("Searching with ${queries.size} query variation(s)")

            // Step 2: Vector Search with all query variations
            logger.info("Step 2: Vector Search")
            val allResults = queries.flatMap { q ->
                logger.debug("Searching for: '$q'")
                val embedding = embeddingGenerator.generateEmbedding(q)
                vectorDatabase.search(
                    queryEmbedding = embedding.vector,
                    topK = ragTopK,
                    minSimilarity = ragMinSimilarity
                )
            }.distinctBy { it.text } // Remove duplicates

            logger.info("Found ${allResults.size} unique results from vector search")

            if (allResults.isEmpty()) {
                logger.info("No results found")
                return "No relevant documents found for query: \"$query\""
            }

            // Step 3: LLM Re-ranking (optional)
            val refinedResults = if (enableReranking) {
                logger.info("Step 3: LLM Re-ranking")
                val scored = resultsRefiner.rerankResults(query, allResults, topK = finalTopK)
                logger.info("Re-ranked to ${scored.size} results")

                // Log top results
                scored.take(3).forEachIndexed { idx, result ->
                    logger.info("  ${idx + 1}. Score: ${result.relevanceScore}, Vector sim: ${result.result.similarity}")
                }

                // Convert back to SearchResult
                scored.map { it.result }
            } else {
                logger.info("Step 3: Skipped (re-ranking disabled)")
                allResults.take(finalTopK)
            }

            if (refinedResults.isEmpty()) {
                logger.info("No relevant results after filtering")
                return "No sufficiently relevant documents found for query: \"$query\""
            }

            // Step 4: Context Compression (optional)
            logger.info("Step 4: Context Formatting/Compression")
            val finalContext = if (enableCompression) {
                val chunks = refinedResults.map { it.text }
                val compressed = contextCompressor.smartCompress(query, chunks, targetTokens = 1500)

                // Extract and append sources after compression
                val sources = refinedResults.mapNotNull { result ->
                    result.metadata?.get("file")
                }.distinct()

                buildString {
                    appendLine(compressed)
                    if (sources.isNotEmpty()) {
                        appendLine()
                        appendLine("Sources used:")
                        sources.forEach { source ->
                            appendLine("- $source")
                        }
                    }
                }
            } else {
                formatResults(refinedResults)
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            logger.info("=== Search completed in ${elapsedTime}ms ===")

            return finalContext

        } catch (e: Exception) {
            logger.error("Error executing enhanced search: ${e.message}", e)
            return "Error searching documents: ${e.message}"
        }
    }

    /**
     * Format results as text (used when compression is disabled)
     */
    private fun formatResults(results: List<SearchResult>): String {
        // Extract unique sources from results
        val sources = results.mapNotNull { result ->
            result.metadata?.get("file")
        }.distinct()

        return buildString {
            appendLine("Found ${results.size} relevant document(s):\n")

            results.forEachIndexed { index, result ->
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
    }

    /**
     * Get statistics about the enhanced search configuration
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "ragTopK" to ragTopK,
            "finalTopK" to finalTopK,
            "minSimilarity" to ragMinSimilarity,
            "queryExpansionEnabled" to enableQueryExpansion,
            "rerankingEnabled" to enableReranking,
            "compressionEnabled" to enableCompression
        )
    }
}
