package io.github.devapro.ai.tools.rag

import io.github.devapro.ai.embeds.rag.EmbeddingGenerator
import io.github.devapro.ai.embeds.rag.SearchResult
import io.github.devapro.ai.embeds.rag.VectorDatabase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Hybrid RAG search combining vector similarity with exact text matching
 *
 * Solves the problem where pure semantic search misses exact keyword matches.
 * Uses a two-stage approach:
 * 1. Vector similarity search (semantic understanding)
 * 2. Exact text matching (keyword fallback)
 *
 * Results from both approaches are merged and deduplicated.
 */
class HybridSearchTool(
    private val vectorDatabase: VectorDatabase,
    private val embeddingGenerator: EmbeddingGenerator,
    private val ragTopK: Int = 10,
    private val ragMinSimilarity: Double = 0.5,  // Lower threshold since we have text fallback
    private val enableTextSearch: Boolean = true
) : RagSearchToolInterface {
    private val logger = LoggerFactory.getLogger(HybridSearchTool::class.java)

    override suspend fun execute(arguments: JsonObject?): String {
        try {
            val query = arguments?.get("query")?.jsonPrimitive?.content
            if (query.isNullOrBlank()) {
                return "Error: Query parameter is required"
            }

            logger.info("=== Hybrid RAG Search ===")
            logger.info("Query: '$query'")

            // Stage 1: Vector similarity search
            logger.info("Stage 1: Vector similarity search (threshold: $ragMinSimilarity)")
            val queryEmbedding = embeddingGenerator.generateEmbedding(query)
            val vectorResults = vectorDatabase.search(
                queryEmbedding = queryEmbedding.vector,
                topK = ragTopK,
                minSimilarity = ragMinSimilarity
            )
            logger.info("Vector search found: ${vectorResults.size} results")

            // Stage 2: Exact text matching (if enabled)
            val textResults = if (enableTextSearch) {
                logger.info("Stage 2: Exact text matching")
                val textMatches = searchByText(query)
                logger.info("Text search found: ${textMatches.size} results")
                textMatches
            } else {
                emptyList()
            }

            // Merge results, prioritizing exact text matches
            val mergedResults = mergeResults(vectorResults, textResults, query)
            logger.info("Total unique results: ${mergedResults.size}")

            if (mergedResults.isEmpty()) {
                return "No relevant documents found for query: \"$query\""
            }

            return formatResults(mergedResults, query, vectorResults.size, textResults.size)

        } catch (e: Exception) {
            logger.error("Error executing hybrid search: ${e.message}", e)
            return "Error searching documents: ${e.message}"
        }
    }

    /**
     * Search for exact text matches in the database
     * Uses case-insensitive substring matching
     */
    private fun searchByText(query: String): List<SearchResult> {
        logger.debug("Searching for text containing: '$query'")

        val allResults = vectorDatabase.getAllEmbeddings()
        val normalizedQuery = query.lowercase().trim()

        // Find chunks containing the query text
        val matches = allResults.filter { result ->
            result.text.lowercase().contains(normalizedQuery)
        }

        // Score by relevance (title matches score higher than body matches)
        return matches.map { result ->
            val score = calculateTextScore(result, normalizedQuery)
            result.copy(similarity = score)
        }
        .sortedByDescending { it.similarity }
        .take(ragTopK)
    }

    /**
     * Calculate relevance score for text matches
     * Higher scores for:
     * - Matches in headings/titles
     * - Multiple occurrences
     * - Matches at the beginning of text
     */
    private fun calculateTextScore(result: SearchResult, query: String): Double {
        val text = result.text.lowercase()
        val heading = result.metadata?.get("heading")?.lowercase() ?: ""

        var score = 0.85 // Base score for text match (higher than typical vector similarity)

        // Bonus for heading match
        if (heading.contains(query)) {
            score += 0.10
        }

        // Bonus for match at beginning
        if (text.startsWith(query)) {
            score += 0.05
        }

        // Bonus for multiple occurrences
        val occurrences = text.windowed(query.length).count { it == query }
        score += (occurrences - 1) * 0.01 // +0.01 for each additional occurrence

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Merge vector and text search results
     * Deduplicates by text content, keeping the result with higher score
     */
    private fun mergeResults(
        vectorResults: List<SearchResult>,
        textResults: List<SearchResult>,
        query: String
    ): List<SearchResult> {
        // Create a map to deduplicate by text content
        val resultMap = mutableMapOf<String, SearchResult>()

        // Add vector results
        vectorResults.forEach { result ->
            resultMap[result.text] = result
        }

        // Add text results (may override vector results if score is higher)
        textResults.forEach { result ->
            val existing = resultMap[result.text]
            if (existing == null || result.similarity > existing.similarity) {
                resultMap[result.text] = result
            }
        }

        // Sort by similarity score and take top K
        return resultMap.values
            .sortedByDescending { it.similarity }
            .take(ragTopK)
    }

    /**
     * Format results for display
     */
    private fun formatResults(
        results: List<SearchResult>,
        query: String,
        vectorCount: Int,
        textCount: Int
    ): String {
        val sources = results.mapNotNull { it.metadata?.get("file") }.distinct()

        return buildString {
            appendLine("Found ${results.size} relevant chunk(s) from documentation:")
            appendLine("(${vectorCount} from vector search, ${textCount} from text search)\n")

            // List source files prominently at the top
            if (sources.isNotEmpty()) {
                appendLine("📁 Source Documents Found:")
                sources.forEach { file ->
                    appendLine("   - $file")
                }
                appendLine()
            }

            // Show chunk previews
            results.forEachIndexed { index, result ->
                appendLine("--- Chunk ${index + 1} (Relevance: ${"%.3f".format(result.similarity)}) ---")

                result.metadata?.get("file")?.let { file ->
                    appendLine("From: $file")
                }

                result.metadata?.get("heading")?.let { heading ->
                    appendLine("Section: $heading")
                }

                appendLine()
                appendLine(result.text)
                appendLine()
            }

            appendLine("---")
            appendLine()
            appendLine("💡 IMPORTANT - These are TEXT CHUNKS from the documents, not complete files.")
            appendLine()
            appendLine("If the chunks above are sufficient to answer the question, use them directly.")
            appendLine()
            appendLine("However, if you need MORE COMPLETE INFORMATION (such as:")
            appendLine("  • Complete lists or tables")
            appendLine("  • Full API reference")
            appendLine("  • Comprehensive configuration details")
            appendLine("  • Multiple related sections")
            appendLine()
            appendLine("Then you MUST fetch the full document(s) using:")
            appendLine("  read_file(path=\"filename.md\", mode=\"document\")")
            appendLine()
            appendLine("For example:")
            sources.forEach { file ->
                appendLine("  read_file(path=\"$file\", mode=\"document\")")
            }
            appendLine()
            appendLine("After reading the full document(s), synthesize a complete answer for the user.")
        }
    }
}
