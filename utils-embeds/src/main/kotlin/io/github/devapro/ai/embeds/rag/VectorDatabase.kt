package io.github.devapro.ai.embeds.rag

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Database table for storing embeddings
 */
object EmbeddingsTable : IntIdTable("embeddings") {
    val text = text("text")
    val vector = text("vector")  // Store as JSON array
    val model = varchar("model", 100)
    val chunkIndex = integer("chunk_index")
    val metadata = text("metadata").nullable()
    val createdAt = long("created_at")
}

/**
 * Data class for search results
 */
data class SearchResult(
    val id: Int,
    val text: String,
    val similarity: Double,
    val chunkIndex: Int,
    val metadata: Map<String, String>?
)

/**
 * Component responsible for storing and retrieving embeddings in SQLite
 * Implements cosine similarity search for RAG
 */
class VectorDatabase(
    private val dbPath: String = "embeddings.db"
) {
    private val logger = LoggerFactory.getLogger(VectorDatabase::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Initialize database connection
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")

        // Create table if not exists
        transaction {
            SchemaUtils.create(EmbeddingsTable)
        }

        logger.info("Vector database initialized at $dbPath")
    }

    /**
     * Store embeddings in the database
     */
    fun storeEmbeddings(embeddings: List<Embedding>) {
        logger.info("Storing ${embeddings.size} embeddings in database")

        transaction {
            embeddings.forEach { embedding ->
                EmbeddingsTable.insert {
                    it[text] = embedding.text
                    it[vector] = json.encodeToString(embedding.vector)
                    it[model] = embedding.model
                    it[chunkIndex] = embedding.chunkIndex
                    it[metadata] = if (embedding.metadata.isNotEmpty()) {
                        json.encodeToString(embedding.metadata)
                    } else {
                        null
                    }
                    it[createdAt] = System.currentTimeMillis()
                }
            }
        }

        logger.info("Successfully stored ${embeddings.size} embeddings")
    }

    /**
     * Search for similar embeddings using cosine similarity
     * @param queryEmbedding The query embedding vector
     * @param topK Number of top results to return
     * @param minSimilarity Minimum similarity threshold (0.0 to 1.0)
     * @return List of search results sorted by similarity
     */
    fun search(queryEmbedding: List<Double>, topK: Int = 5, minSimilarity: Double = 0.0): List<SearchResult> {
        logger.info("Searching for top $topK similar embeddings (min similarity: $minSimilarity)")

        val results = transaction {
            EmbeddingsTable.selectAll().map { row ->
                val storedVector = json.decodeFromString<List<Double>>(row[EmbeddingsTable.vector])
                val similarity = cosineSimilarity(queryEmbedding, storedVector)

                SearchResult(
                    id = row[EmbeddingsTable.id].value,
                    text = row[EmbeddingsTable.text],
                    similarity = similarity,
                    chunkIndex = row[EmbeddingsTable.chunkIndex],
                    metadata = row[EmbeddingsTable.metadata]?.let {
                        json.decodeFromString<Map<String, String>>(it)
                    }
                )
            }
                .filter { it.similarity >= minSimilarity }
                .sortedByDescending { it.similarity }
                .take(topK)
        }

        logger.info("Found ${results.size} results")
        return results
    }

    /**
     * Get total count of embeddings in database
     */
    fun getCount(): Long {
        return transaction {
            EmbeddingsTable.selectAll().count()
        }
    }

    /**
     * Clear all embeddings from database
     */
    fun clear() {
        logger.warn("Clearing all embeddings from database")
        transaction {
            EmbeddingsTable.deleteAll()
        }
    }

    /**
     * Get all embeddings (useful for debugging)
     */
    fun getAllEmbeddings(): List<SearchResult> {
        return transaction {
            EmbeddingsTable.selectAll().map { row ->
                SearchResult(
                    id = row[EmbeddingsTable.id].value,
                    text = row[EmbeddingsTable.text],
                    similarity = 0.0,
                    chunkIndex = row[EmbeddingsTable.chunkIndex],
                    metadata = row[EmbeddingsTable.metadata]?.let {
                        json.decodeFromString<Map<String, String>>(it)
                    }
                )
            }
        }
    }

    /**
     * Calculate cosine similarity between two vectors
     * Returns value between -1 and 1, where 1 means identical direction
     */
    private fun cosineSimilarity(vec1: List<Double>, vec2: List<Double>): Double {
        require(vec1.size == vec2.size) { "Vectors must have same dimension" }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        return if (norm1 == 0.0 || norm2 == 0.0) {
            0.0
        } else {
            dotProduct / (sqrt(norm1) * sqrt(norm2))
        }
    }

    /**
     * Close database connection
     */
    fun close() {
        logger.info("Closing vector database")
        // Exposed doesn't require explicit close for SQLite
    }
}
