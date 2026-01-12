package io.github.devapro.ai.tools.rag

import io.github.devapro.ai.embeds.rag.SearchResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Component for refining RAG search results using LLM
 * Provides both binary filtering and relevance scoring/re-ranking
 */
class RagResultsRefiner(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val model: String = "gpt-4o-mini"
) {
    private val logger = LoggerFactory.getLogger(RagResultsRefiner::class.java)

    data class ScoredResult(
        val result: SearchResult,
        val relevanceScore: Double,
        val reasoning: String
    )

    /**
     * Re-rank results by LLM relevance score
     * @param query The user's query
     * @param results Initial results from vector search
     * @param topK Number of top results to return after re-ranking
     * @return Re-ranked results with LLM scores
     */
    suspend fun rerankResults(
        query: String,
        results: List<SearchResult>,
        topK: Int = 3
    ): List<ScoredResult> {
        if (results.isEmpty()) return emptyList()

        logger.info("Re-ranking ${results.size} results using LLM (model: $model)")

        // Score all results in parallel for efficiency
        val scoredResults = coroutineScope {
            results.map { result ->
                async { scoreRelevance(query, result) }
            }.awaitAll()
        }

        // Filter out failed scores and sort by relevance
        val validResults = scoredResults.filterNotNull()
        logger.info("Successfully scored ${validResults.size}/${results.size} results")

        val ranked = validResults.sortedByDescending { it.relevanceScore }.take(topK)

        logger.info("Top result: score=${ranked.firstOrNull()?.relevanceScore}, reasoning=${ranked.firstOrNull()?.reasoning}")

        return ranked
    }

    /**
     * Filter results by binary relevance (yes/no)
     * Faster and cheaper than scoring, but less precise
     */
    suspend fun filterRelevantResults(
        query: String,
        results: List<SearchResult>
    ): List<SearchResult> {
        if (results.isEmpty()) return emptyList()

        logger.info("Filtering ${results.size} results using binary relevance check")

        // Check relevance in parallel
        val relevanceChecks = coroutineScope {
            results.map { result ->
                async {
                    val relevant = isRelevant(query, result.text)
                    if (relevant) result else null
                }
            }.awaitAll()
        }

        val filtered = relevanceChecks.filterNotNull()
        logger.info("Filtered to ${filtered.size}/${results.size} relevant results")

        return filtered
    }

    /**
     * Score individual result for relevance
     */
    private suspend fun scoreRelevance(
        query: String,
        result: SearchResult
    ): ScoredResult? {
        try {
            val prompt = """
                Rate how relevant this document chunk is for answering the query.

                Query: "$query"

                Document chunk:
                "${result.text.take(500)}" ${if (result.text.length > 500) "..." else ""}

                Respond in this exact format:
                SCORE: [0.0 to 1.0]
                REASONING: [brief explanation]

                Be strict - only high scores (>0.7) for directly relevant content.
            """.trimIndent()

            val response = callLLM(prompt, temperature = 0.0) // Deterministic
            val lines = response.lines().filter { it.isNotBlank() }

            // Parse response
            val scoreLine = lines.find { it.startsWith("SCORE:", ignoreCase = true) }
            val reasoningLine = lines.find { it.startsWith("REASONING:", ignoreCase = true) }

            val score = scoreLine
                ?.substringAfter(":", "")
                ?.trim()
                ?.toDoubleOrNull() ?: 0.0

            val reasoning = reasoningLine
                ?.substringAfter(":", "")
                ?.trim() ?: "No reasoning provided"

            return ScoredResult(
                result = result,
                relevanceScore = score,
                reasoning = reasoning
            )
        } catch (e: Exception) {
            logger.error("Failed to score result: ${e.message}", e)
            return null
        }
    }

    /**
     * Binary relevance check
     */
    private suspend fun isRelevant(query: String, chunk: String): Boolean {
        try {
            val prompt = """
                Query: "$query"

                Document chunk: "${chunk.take(500)}" ${if (chunk.length > 500) "..." else ""}

                Is this document chunk relevant for answering the query?
                Answer with ONLY "yes" or "no" - nothing else.
            """.trimIndent()

            val response = callLLM(prompt, temperature = 0.0)
            val answer = response.trim().lowercase()

            return answer.startsWith("yes")
        } catch (e: Exception) {
            logger.error("Failed to check relevance: ${e.message}")
            // On error, default to including the result (fail open)
            return true
        }
    }

    /**
     * Call OpenAI API
     */
    private suspend fun callLLM(prompt: String, temperature: Double = 0.0): String {
        val request = LLMRequest(
            model = model,
            messages = listOf(
                LLMMessage(role = "system", content = "You are a relevance evaluator. Follow instructions exactly."),
                LLMMessage(role = "user", content = prompt)
            ),
            temperature = temperature,
            max_tokens = 150
        )

        val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<LLMResponse>()

        return response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("No response from LLM")
    }

    @Serializable
    private data class LLMRequest(
        val model: String,
        val messages: List<LLMMessage>,
        val temperature: Double = 0.0,
        val max_tokens: Int = 150
    )

    @Serializable
    private data class LLMMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class LLMResponse(
        val choices: List<LLMChoice>
    )

    @Serializable
    private data class LLMChoice(
        val message: LLMMessage
    )
}
