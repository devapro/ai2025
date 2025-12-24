package io.github.devapro.ai.utils

import io.github.cdimascio.dotenv.dotenv
import io.github.devapro.ai.utils.rag.EmbeddingGenerator
import io.github.devapro.ai.utils.rag.TextChunk
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
 * Supports both single file and folder processing
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

        val embeddingApiUrl = dotenv["RAG_EMBEDDING_API_URL"]
            ?: "http://127.0.0.1:1234/v1/embeddings"

        val embeddingModel = dotenv["RAG_EMBEDDING_MODEL"]
            ?: "text-embedding-nomic-embed-text-v1.5"

        // Configuration
        val inputPath = args.getOrNull(0) ?: "and-doc"
        val dbPath = args.getOrNull(1) ?: "embeddings.db"
        val chunkSize = args.getOrNull(2)?.toIntOrNull() ?: 500
        val chunkOverlap = args.getOrNull(3)?.toIntOrNull() ?: 100
        val fileExtensions = args.getOrNull(4)?.split(",") ?: listOf("md", "txt")

        logger.info("Configuration:")
        logger.info("  Input path: $inputPath")
        logger.info("  Database: $dbPath")
        logger.info("  Chunk size: $chunkSize")
        logger.info("  Chunk overlap: $chunkOverlap")
        logger.info("  File extensions: ${fileExtensions.joinToString(", ")}")
        logger.info("  Embedding API URL: $embeddingApiUrl")
        logger.info("  Embedding model: $embeddingModel")

        // Check if input exists
        val input = File(inputPath)
        if (!input.exists()) {
            logger.error("Input path not found: $inputPath")
            exitProcess(1)
        }

        // Collect files to process
        val filesToProcess = if (input.isDirectory) {
            logger.info("Input is a directory, scanning for files...")
            collectFiles(input, fileExtensions)
        } else {
            logger.info("Input is a file")
            listOf(input)
        }

        if (filesToProcess.isEmpty()) {
            logger.warn("No files found to process")
            exitProcess(0)
        }

        logger.info("Found ${filesToProcess.size} file(s) to process:")
        filesToProcess.forEach { file ->
            logger.info("  - ${file.path} (${file.length()} bytes)")
        }

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
            httpClient = httpClient,
            model = embeddingModel,
            apiUrl = embeddingApiUrl
        )
        val vectorDb = VectorDatabase(dbPath = dbPath)

        runBlocking {
            try {
                // Step 1: Process all files and create chunks
                logger.info("\n=== Step 1: Processing files and creating chunks ===")
                val allChunks = mutableListOf<TextChunk>()

                filesToProcess.forEachIndexed { fileIndex, file ->
                    logger.info("\nProcessing file ${fileIndex + 1}/${filesToProcess.size}: ${file.name}")

                    try {
                        // Read file content
                        val text = file.readText()
                        logger.info("  Read ${text.length} characters")

                        // Add file metadata
                        val fileMetadata = mapOf(
                            "file" to file.name,
                            "path" to file.path,
                            "size" to file.length().toString()
                        )

                        // Chunk based on file type
                        val isMarkdown = file.extension.equals("md", ignoreCase = true)
                        val chunks = if (isMarkdown) {
                            logger.info("  Using Markdown-aware chunking")
                            textChunker.chunkMarkdown(text, fileMetadata)
                        } else {
                            logger.info("  Using sentence-based chunking")
                            textChunker.chunkText(text, fileMetadata)
                        }

                        logger.info("  Created ${chunks.size} chunks from ${file.name}")

                        // Preview first chunk
                        if (chunks.isNotEmpty()) {
                            logger.info("  First chunk preview: ${chunks.first().text.take(80)}...")
                        }

                        allChunks.addAll(chunks)

                    } catch (e: Exception) {
                        logger.error("  Error processing ${file.name}: ${e.message}", e)
                        // Continue with other files
                    }
                }

                logger.info("\n=== Summary ===")
                logger.info("Total chunks created: ${allChunks.size}")
                logger.info("Files processed: ${filesToProcess.size}")

                // Group chunks by file for statistics
                val chunksByFile = allChunks.groupBy { it.metadata["file"] }
                logger.info("\nChunks per file:")
                chunksByFile.forEach { (file, chunks) ->
                    logger.info("  $file: ${chunks.size} chunks")
                }

                if (allChunks.isEmpty()) {
                    logger.warn("No chunks created, exiting")
                    return@runBlocking
                }

                // Step 2: Generate embeddings
                logger.info("\n=== Step 2: Generating embeddings ===")
                logger.info("Processing ${allChunks.size} chunks...")
                val embeddings = embeddingGenerator.generateEmbeddings(allChunks)
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
                    val testQuery = allChunks.first().text
                    logger.info("Test query from ${allChunks.first().metadata["file"]}: ${testQuery.take(100)}...")

                    val testEmbedding = embeddingGenerator.generateEmbedding(testQuery)
                    val searchResults = vectorDb.search(testEmbedding.vector, topK = 5)

                    logger.info("Search results:")
                    searchResults.forEachIndexed { index, result ->
                        logger.info("  ${index + 1}. Similarity: ${String.format("%.4f", result.similarity)}")
                        logger.info("     File: ${result.metadata?.get("file") ?: "unknown"}")
                        logger.info("     Text: ${result.text.take(80)}...")
                    }
                }

                logger.info("\n=== Process completed successfully ===")
                logger.info("Total files indexed: ${filesToProcess.size}")
                logger.info("Total chunks created: ${allChunks.size}")
                logger.info("Total embeddings stored: ${embeddings.size}")
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

/**
 * Recursively collect all files with specified extensions from a directory
 */
private fun collectFiles(directory: File, extensions: List<String>): List<File> {
    val files = mutableListOf<File>()

    fun scan(dir: File) {
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> scan(file)  // Recursive
                file.isFile && extensions.any { ext ->
                    file.extension.equals(ext, ignoreCase = true)
                } -> files.add(file)
            }
        }
    }

    scan(directory)
    return files.sortedBy { it.path }  // Sort for consistent order
}
