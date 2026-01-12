package io.github.devapro.ai.tools.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Component for expanding and rewriting queries to improve RAG retrieval
 * Generates multiple query variations to capture different phrasings
 */
class QueryExpander(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val model: String = "gpt-4o-mini"
) {
    private val logger = LoggerFactory.getLogger(QueryExpander::class.java)

    /**
     * Expand query into multiple variations
     * @param query Original user query
     * @param numVariations Number of alternative phrasings to generate (default: 2)
     * @return List containing original query + variations
     */
    suspend fun expandQuery(query: String, numVariations: Int = 2): List<String> {
        logger.info("Expanding query: '$query' (generating $numVariations variations)")

        try {
            val variations = generateVariations(query, numVariations)

            // Always include original query first
            val allQueries = listOf(query) + variations

            logger.info("Generated ${allQueries.size} total queries (1 original + ${variations.size} variations)")
            variations.forEachIndexed { index, variation ->
                logger.debug("Variation ${index + 1}: $variation")
            }

            return allQueries
        } catch (e: Exception) {
            logger.error("Failed to expand query: ${e.message}", e)
            // Fallback to original query only
            return listOf(query)
        }
    }

    /**
     * Generate query variations using LLM
     */
    private suspend fun generateVariations(query: String, count: Int): List<String> {
        val prompt = """
            Original query: "$query"

            Generate $count alternative ways to phrase this query that would help find the same information.

            Guidelines:
            - Use different words but keep the same meaning
            - Consider technical vs. plain language versions
            - Think about what terms might appear in documentation
            - Make each variation distinct from the original

            Return ONLY the alternative queries, one per line, no numbering or extra text.
        """.trimIndent()

        val response = callLLM(prompt)

        // Parse variations (one per line)
        val variations = response
            .lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                !line.matches(Regex("^\\d+\\.?.*")) && // Remove numbered lines
                line.length > 5 // Avoid too short responses
            }
            .take(count)

        return variations
    }

    /**
     * Generate a more specific/focused version of the query
     * Useful when initial search returns too many irrelevant results
     */
    suspend fun refineQuery(query: String, searchResults: List<String>): String {
        logger.info("Refining query based on search results")

        try {
            val prompt = """
                Original query: "$query"

                The search returned these document snippets:
                ${searchResults.take(3).joinToString("\n---\n") { it.take(200) }}

                These results seem off-target. Rewrite the query to be more specific and find better matches.
                Return ONLY the refined query, nothing else.
            """.trimIndent()

            val refined = callLLM(prompt).trim()

            logger.info("Refined query: '$refined'")

            return refined
        } catch (e: Exception) {
            logger.error("Failed to refine query: ${e.message}", e)
            // Fallback to original query
            return query
        }
    }

    /**
     * Call OpenAI API
     */
    private suspend fun callLLM(prompt: String): String {
        val request = LLMRequest(
            model = model,
            messages = listOf(
                LLMMessage(
                    role = "system",
                    content = "You are a search query expert. Generate effective search queries that help find relevant information."
                ),
                LLMMessage(role = "user", content = prompt)
            ),
            temperature = 0.7, // Some creativity for variations
            max_tokens = 200
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
        val temperature: Double,
        val max_tokens: Int
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
