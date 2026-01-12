package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Tool for exploring files and generating AI-powered summaries
 *
 * Reads files and uses LLM to generate short descriptions for each file.
 * Can process either a list of specific files or all files in a folder.
 *
 * Examples:
 * - Explore specific files: {"fileList": ["src/Main.kt", "src/Utils.kt"]}
 * - Explore folder: {"folderPath": "src/main/kotlin"}
 * - Folder with depth: {"folderPath": "src", "recursive": true, "maxDepth": 2}
 */
class ExploringTool(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val workingDirectory: File = File(System.getProperty("user.dir")),
    private val maxFileSize: Long = 50 * 1024, // 50KB per file
    private val maxFilesToProcess: Int = 20
) : Tool {

    private val logger = LoggerFactory.getLogger(ExploringTool::class.java)

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "explore_files",
                description = """
                    Explore files and generate AI-powered summaries for each file.
                    Can process specific files or all files in a folder.
                    Returns a description of what each file contains and its purpose.
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("fileList") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "string")
                            }
                            put("description", "List of specific file paths to explore (relative to working directory)")
                        }
                        putJsonObject("folderPath") {
                            put("type", "string")
                            put("description", "Folder path to explore all files within (alternative to fileList)")
                        }
                        putJsonObject("recursive") {
                            put("type", "boolean")
                            put("description", "If using folderPath, explore subfolders recursively (default: false)")
                            put("default", false)
                        }
                        putJsonObject("maxDepth") {
                            put("type", "integer")
                            put("description", "Maximum depth for recursive exploration (default: 2)")
                            put("default", 2)
                            put("minimum", 1)
                            put("maximum", 5)
                        }
                        putJsonObject("fileExtensions") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "string")
                            }
                            put("description", "Filter by file extensions (e.g., ['kt', 'java', 'md']). If not specified, all files are included.")
                        }
                    }
                    putJsonArray("required") {
                        // Either fileList or folderPath is required, but this will be validated in execute
                    }
                }
            )
        )
    }

    override suspend fun execute(arguments: JsonObject?): String {
        if (arguments == null) {
            throw IllegalArgumentException("Arguments are required")
        }

        // Parse arguments
        val fileListJson = arguments["fileList"]?.jsonArray
        val folderPath = arguments["folderPath"]?.jsonPrimitive?.content
        val recursive = arguments["recursive"]?.jsonPrimitive?.booleanOrNull ?: false
        val maxDepth = arguments["maxDepth"]?.jsonPrimitive?.intOrNull ?: 2
        val fileExtensionsJson = arguments["fileExtensions"]?.jsonArray

        // Parse file extensions filter
        val fileExtensions = fileExtensionsJson?.mapNotNull {
            it.jsonPrimitive.content.trim().lowercase()
        }?.toSet()

        // Collect files to process
        val filesToProcess = mutableListOf<File>()

        when {
            fileListJson != null -> {
                // Process specific file list
                fileListJson.forEach { pathElement ->
                    val path = pathElement.jsonPrimitive.content
                    val file = resolvePath(path)
                    if (file.exists() && file.isFile) {
                        filesToProcess.add(file)
                    } else {
                        logger.warn("File not found or not a file: $path")
                    }
                }
            }
            folderPath != null -> {
                // Process folder
                val folder = resolvePath(folderPath)
                if (!folder.exists()) {
                    return "Error: Folder does not exist: ${folder.absolutePath}"
                }
                if (!folder.isDirectory) {
                    return "Error: Path is not a directory: ${folder.absolutePath}"
                }

                collectFiles(folder, filesToProcess, recursive, 0, maxDepth, fileExtensions)
            }
            else -> {
                return "Error: Either 'fileList' or 'folderPath' must be provided"
            }
        }

        if (filesToProcess.isEmpty()) {
            return "No files found to explore"
        }

        // Limit number of files
        if (filesToProcess.size > maxFilesToProcess) {
            logger.warn("Too many files to process: ${filesToProcess.size}. Limiting to $maxFilesToProcess")
            filesToProcess.subList(maxFilesToProcess, filesToProcess.size).clear()
        }

        logger.info("Exploring ${filesToProcess.size} files")

        // Process each file
        val results = mutableListOf<FileExploration>()
        for (file in filesToProcess) {
            try {
                val relativePath = file.relativeTo(workingDirectory).path
                val description = exploreFile(file)
                results.add(FileExploration(relativePath, description, success = true))
            } catch (e: Exception) {
                logger.error("Error exploring file ${file.path}: ${e.message}", e)
                results.add(FileExploration(
                    file.relativeTo(workingDirectory).path,
                    "Error: ${e.message}",
                    success = false
                ))
            }
        }

        // Format results
        return formatResults(results)
    }

    /**
     * Collect files from folder (recursively if needed)
     */
    private fun collectFiles(
        folder: File,
        output: MutableList<File>,
        recursive: Boolean,
        currentDepth: Int,
        maxDepth: Int,
        fileExtensions: Set<String>?
    ) {
        if (currentDepth >= maxDepth && recursive) {
            return
        }

        val files = folder.listFiles() ?: return

        for (file in files.sortedBy { it.name }) {
            // Skip hidden files
            if (file.name.startsWith(".")) {
                continue
            }

            when {
                file.isFile -> {
                    // Check file extension filter
                    val shouldInclude = if (fileExtensions != null) {
                        val extension = file.extension.lowercase()
                        extension in fileExtensions
                    } else {
                        true
                    }

                    if (shouldInclude && file.length() <= maxFileSize) {
                        output.add(file)
                    }
                }
                file.isDirectory && recursive -> {
                    collectFiles(file, output, recursive, currentDepth + 1, maxDepth, fileExtensions)
                }
            }
        }
    }

    /**
     * Explore a single file using LLM
     */
    private suspend fun exploreFile(file: File): String {
        // Read file content
        val content = file.readText()

        // Limit content length for LLM
        val truncatedContent = if (content.length > 10000) {
            content.substring(0, 10000) + "\n... (truncated)"
        } else {
            content
        }

        // Prepare LLM request
        val request = ExplorationRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                ExplorationMessage(
                    role = "system",
                    content = """
                        You are a code documentation assistant. Analyze the provided file and generate a brief,
                        concise description (1-3 sentences) of what this file contains and its purpose.
                        Focus on the main functionality, key components, or content theme.
                        Be specific and technical when describing code files.
                    """.trimIndent()
                ),
                ExplorationMessage(
                    role = "user",
                    content = """
                        File: ${file.name}

                        Content:
                        ```
                        $truncatedContent
                        ```

                        Provide a brief description of this file.
                    """.trimIndent()
                )
            ),
            temperature = 0.3,
            max_tokens = 150
        )

        // Call OpenAI API
        val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<ExplorationResponse>()

        return response.choices.firstOrNull()?.message?.content?.trim()
            ?: "Could not generate description"
    }

    /**
     * Format exploration results
     */
    private fun formatResults(results: List<FileExploration>): String {
        return buildString {
            appendLine("File Exploration Results:")
            appendLine()

            val successful = results.filter { it.success }
            val failed = results.filter { !it.success }

            successful.forEach { result ->
                appendLine("📄 ${result.filePath}")
                appendLine("   ${result.description}")
                appendLine()
            }

            if (failed.isNotEmpty()) {
                appendLine("Failed to explore ${failed.size} file(s):")
                failed.forEach { result ->
                    appendLine("   ❌ ${result.filePath}: ${result.description}")
                }
                appendLine()
            }

            appendLine("Summary: Explored ${successful.size} file(s) successfully" +
                if (failed.isNotEmpty()) ", ${failed.size} failed" else "")
        }
    }

    /**
     * Resolve path relative to working directory or as absolute
     */
    private fun resolvePath(pathStr: String): File {
        val path = File(pathStr)
        return if (path.isAbsolute) {
            path
        } else {
            File(workingDirectory, pathStr).canonicalFile
        }
    }

    /**
     * Data classes for file exploration results
     */
    private data class FileExploration(
        val filePath: String,
        val description: String,
        val success: Boolean
    )

    /**
     * OpenAI API request/response models for exploration
     */
    @Serializable
    private data class ExplorationRequest(
        val model: String,
        val messages: List<ExplorationMessage>,
        val temperature: Double,
        val max_tokens: Int
    )

    @Serializable
    private data class ExplorationMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ExplorationResponse(
        val choices: List<ExplorationChoice>
    )

    @Serializable
    private data class ExplorationChoice(
        val message: ExplorationMessage
    )
}
