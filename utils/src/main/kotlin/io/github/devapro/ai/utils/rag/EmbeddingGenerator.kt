package io.github.devapro.ai.utils.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Data class representing an embedding result
 */
data class Embedding(
    val text: String,
    val vector: List<Double>,
    val model: String,
    val chunkIndex: Int
)

/**
 * OpenAI API request/response models
 */
@Serializable
private data class EmbeddingRequest(
    @SerialName("input")
    val input: List<String>,
    @SerialName("model")
    val model: String
)

@Serializable
private data class EmbeddingResponse(
    @SerialName("data")
    val data: List<EmbeddingData>,
    @SerialName("model")
    val model: String,
    @SerialName("usage")
    val usage: Usage
)

@Serializable
private data class EmbeddingData(
    @SerialName("embedding")
    val embedding: List<Double>,
    @SerialName("index")
    val index: Int
)

@Serializable
private data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

/**
 * Component responsible for generating embeddings using OpenAI API
 * Uses text-embedding-3-small model for efficient embeddings
 */
class EmbeddingGenerator(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val model: String = "text-embedding-3-small",
    private val batchSize: Int = 100  // OpenAI allows up to 2048 inputs per request
) {
    private val logger = LoggerFactory.getLogger(EmbeddingGenerator::class.java)
    private val apiUrl = "https://api.openai.com/v1/embeddings"

    /**
     * Generate embeddings for a list of text chunks
     * Processes in batches to optimize API calls
     */
    suspend fun generateEmbeddings(chunks: List<TextChunk>): List<Embedding> {
        if (chunks.isEmpty()) {
            logger.warn("No chunks provided for embedding generation")
            return emptyList()
        }

        logger.info("Generating embeddings for ${chunks.size} chunks using model $model")

        val allEmbeddings = mutableListOf<Embedding>()
        val batches = chunks.chunked(batchSize)

        batches.forEachIndexed { batchIndex, batch ->
            logger.info("Processing batch ${batchIndex + 1}/${batches.size} (${batch.size} chunks)")

            try {
                val embeddings = generateEmbeddingBatch(batch)
                allEmbeddings.addAll(embeddings)
                logger.info("Successfully generated ${embeddings.size} embeddings in batch ${batchIndex + 1}")
            } catch (e: Exception) {
                logger.error("Error generating embeddings for batch ${batchIndex + 1}: ${e.message}", e)
                throw e
            }

            // Small delay between batches to respect rate limits
            if (batchIndex < batches.size - 1) {
                kotlinx.coroutines.delay(100)
            }
        }

        logger.info("Generated ${allEmbeddings.size} embeddings total")
        return allEmbeddings
    }

    /**
     * Generate embeddings for a single batch of chunks
     */
    private suspend fun generateEmbeddingBatch(chunks: List<TextChunk>): List<Embedding> {
        val texts = chunks.map { it.text }

        val request = EmbeddingRequest(
            input = texts,
            model = model
        )

        val response: EmbeddingResponse = httpClient.post(apiUrl) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        logger.debug("API usage: ${response.usage.totalTokens} tokens")

        // Map response to Embedding objects
        return response.data.map { embeddingData ->
            val chunk = chunks[embeddingData.index]
            Embedding(
                text = chunk.text,
                vector = embeddingData.embedding,
                model = response.model,
                chunkIndex = chunk.index
            )
        }
    }

    /**
     * Generate embedding for a single text (useful for queries)
     */
    suspend fun generateEmbedding(text: String): Embedding {
        logger.info("Generating embedding for single text")

        val request = EmbeddingRequest(
            input = listOf(text),
            model = model
        )

        val response: EmbeddingResponse = httpClient.post(apiUrl) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        val embeddingData = response.data.first()
        return Embedding(
            text = text,
            vector = embeddingData.embedding,
            model = response.model,
            chunkIndex = 0
        )
    }
}
