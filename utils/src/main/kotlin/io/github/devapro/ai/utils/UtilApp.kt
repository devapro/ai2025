package io.github.devapro.ai.utils

import io.github.cdimascio.dotenv.dotenv
import io.github.devapro.ai.utils.rag.EmbeddingGenerator
import io.github.devapro.ai.utils.rag.TextChunker
import io.github.devapro.ai.utils.rag.VectorDatabase
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Main application for creating embeddings from text and storing in vector database
 * Implements RAG (Retrieval-Augmented Generation)
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("UtilApp")

    logger.info("Starting Embeddings Utility Application")

    try {
        // Load environment variables
        val dotenv = dotenv {
            ignoreIfMissing = true
        }

        val openAiApiKey = dotenv["OPENAI_API_KEY"]
            ?: throw IllegalStateException("OPENAI_API_KEY environment variable is required")

        // Configuration
        val inputFilePath = args.getOrNull(0) ?: "embeddings.md"
        val dbPath = args.getOrNull(1) ?: "embeddings.db"
        val chunkSize = args.getOrNull(2)?.toIntOrNull() ?: 500
        val chunkOverlap = args.getOrNull(3)?.toIntOrNull() ?: 100

        logger.info("Configuration:")
        logger.info("  Input file: $inputFilePath")
        logger.info("  Database: $dbPath")
        logger.info("  Chunk size: $chunkSize")
        logger.info("  Chunk overlap: $chunkOverlap")

        // Check if input file exists
        val inputFile = File(inputFilePath)
        if (!inputFile.exists()) {
            logger.error("Input file not found: $inputFilePath")
            exitProcess(1)
        }

        // Read input text
        logger.info("Reading input file...")
        val text = inputFile.readText()
        logger.info("Read ${text.length} characters from $inputFilePath")

        // Initialize HTTP client
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
        }

        // Initialize components
        val textChunker = TextChunker(chunkSize = chunkSize, chunkOverlap = chunkOverlap)
        val embeddingGenerator = EmbeddingGenerator(
            apiKey = openAiApiKey,
            httpClient = httpClient
        )
        val vectorDb = VectorDatabase(dbPath = dbPath)

        runBlocking {
            try {
                // Step 1: Chunk the text
                logger.info("\n=== Step 1: Chunking text ===")
                val chunks = textChunker.chunkText(text)
                logger.info("Created ${chunks.size} chunks")

                // Display chunk information
                chunks.take(3).forEachIndexed { index, chunk ->
                    logger.info("Chunk $index preview: ${chunk.text.take(100)}...")
                }

                // Step 2: Generate embeddings
                logger.info("\n=== Step 2: Generating embeddings ===")
                val embeddings = embeddingGenerator.generateEmbeddings(chunks)
                logger.info("Generated ${embeddings.size} embeddings")
                logger.info("Embedding dimension: ${embeddings.firstOrNull()?.vector?.size ?: 0}")

                // Step 3: Store in vector database
                logger.info("\n=== Step 3: Storing embeddings in database ===")
                vectorDb.storeEmbeddings(embeddings)
                val totalCount = vectorDb.getCount()
                logger.info("Total embeddings in database: $totalCount")

                // Step 4: Test search functionality
                logger.info("\n=== Step 4: Testing search functionality ===")
                if (embeddings.isNotEmpty()) {
                    // Search with the first chunk's embedding as a test
                    val testQuery = chunks.first().text
                    logger.info("Test query: ${testQuery.take(100)}...")

                    val testEmbedding = embeddingGenerator.generateEmbedding(testQuery)
                    val searchResults = vectorDb.search(testEmbedding.vector, topK = 3)

                    logger.info("Search results:")
                    searchResults.forEachIndexed { index, result ->
                        logger.info("  ${index + 1}. Similarity: ${String.format("%.4f", result.similarity)}")
                        logger.info("     Text: ${result.text.take(100)}...")
                    }
                }

                logger.info("\n=== Process completed successfully ===")
                logger.info("Embeddings are ready for RAG queries!")
                logger.info("Database location: $dbPath")

            } catch (e: Exception) {
                logger.error("Error during processing: ${e.message}", e)
                throw e
            } finally {
                // Cleanup
                httpClient.close()
                vectorDb.close()
            }
        }

        logger.info("Application finished successfully")

    } catch (e: Exception) {
        logger.error("Fatal error: ${e.message}", e)
        exitProcess(1)
    }
}
