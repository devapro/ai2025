package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Tool for reading file contents from project source code or documentation
 *
 * Supports:
 * - Two modes: source_code (project-source) and document (doc-source)
 * - Reading entire file or specific line range
 * - Automatic encoding detection (defaults to UTF-8)
 * - Line numbering in output
 * - Size limits to prevent reading huge files
 *
 * Examples:
 * - Read source code: {"path": "src/Main.kt", "mode": "source_code"}
 * - Read document: {"path": "User-properties_1558151208.md", "mode": "document"}
 * - Read specific lines: {"path": "README.md", "startLine": 1, "endLine": 10}
 */
class ReadFileTool(
    private val projectSourceDirectory: File = File(System.getProperty("user.dir")),
    private val documentSourceDirectory: File = File(System.getProperty("user.dir"), "doc-source"),
    private val systemDirectory: File = File(System.getProperty("user.dir")),
    private val maxFileSize: Long = 10 * 1024 * 1024 // 10 MB default limit
) : Tool {

    private val logger = LoggerFactory.getLogger(ReadFileTool::class.java)

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "read_file",
                description = """
                    Read contents of a file from project source code, documentation, or system files.
                    Supports three modes:
                    - 'source_code' (default): Read from project-source folder (for source code files like CLAUDE.md)
                    - 'document': Read from doc-source folder (for documentation files found via RAG search)
                    - 'system': Read from bot's working directory (for system files like promts/rules.md)

                    Use 'document' mode when you need to read the full content of documentation files
                    that were mentioned in search_documents results.
                    Use 'system' mode for bot configuration files like promts/rules.md.

                    Maximum file size: ${maxFileSize / (1024 * 1024)} MB.
                    IMPORTANT: Do NOT include folder prefixes (project-source/ or doc-source/) in paths for those modes.
                """.trimIndent(),
                parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "File path relative to the mode's root folder (e.g., 'AndroidRepo/build.gradle.kts' for source_code or 'User-properties_1558151208.md' for document)")
            }
            putJsonObject("mode") {
                put("type", "string")
                putJsonArray("enum") {
                    add("source_code")
                    add("document")
                    add("system")
                }
                put("description", "Read mode: 'source_code' for project files (default), 'document' for documentation files, or 'system' for bot configuration files")
                put("default", "source_code")
            }
            putJsonObject("startLine") {
                put("type", "integer")
                put("description", "Starting line number (1-indexed, optional)")
                put("minimum", 1)
            }
            putJsonObject("endLine") {
                put("type", "integer")
                put("description", "Ending line number (1-indexed, inclusive, optional)")
                put("minimum", 1)
            }
            putJsonObject("includeLineNumbers") {
                put("type", "boolean")
                put("description", "Include line numbers in output (default: true)")
                put("default", true)
            }
        }
        putJsonArray("required") {
            add("path")
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
        val pathStr = arguments["path"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("'path' argument is required")

        val mode = arguments["mode"]?.jsonPrimitive?.content ?: "source_code"
        val startLine = arguments["startLine"]?.jsonPrimitive?.intOrNull
        val endLine = arguments["endLine"]?.jsonPrimitive?.intOrNull
        val includeLineNumbers = arguments["includeLineNumbers"]?.jsonPrimitive?.booleanOrNull ?: true

        // Validate mode
        if (mode !in listOf("source_code", "document", "system")) {
            throw IllegalArgumentException("'mode' must be 'source_code', 'document', or 'system'")
        }

        // Validate line range
        if (startLine != null && startLine < 1) {
            throw IllegalArgumentException("startLine must be >= 1")
        }
        if (endLine != null && endLine < 1) {
            throw IllegalArgumentException("endLine must be >= 1")
        }
        if (startLine != null && endLine != null && endLine < startLine) {
            throw IllegalArgumentException("endLine must be >= startLine")
        }

        logger.info("Reading file: path=$pathStr, mode=$mode, startLine=$startLine, endLine=$endLine")

        // Resolve path based on mode
        val file = resolvePath(pathStr, mode)
        if (!file.exists()) {
            return "Error: File does not exist: ${file.absolutePath}"
        }

        if (!file.isFile) {
            return "Error: Path is not a file: ${file.absolutePath}"
        }

        // Check file size
        val fileSize = file.length()
        if (fileSize > maxFileSize) {
            return "Error: File is too large (${fileSize / (1024 * 1024)} MB). Maximum allowed: ${maxFileSize / (1024 * 1024)} MB"
        }

        // Read file
        return try {
            val lines = file.readLines(StandardCharsets.UTF_8)
            val totalLines = lines.size

            // Determine line range
            val start = (startLine ?: 1) - 1 // Convert to 0-indexed
            val end = (endLine ?: totalLines) // Inclusive end

            // Validate range
            if (start >= totalLines) {
                return "Error: startLine ($startLine) exceeds total lines in file ($totalLines)"
            }

            val actualEnd = minOf(end, totalLines)
            val selectedLines = lines.subList(start, actualEnd)

            // Format output
            val result = buildString {
                appendLine("File: ${file.absolutePath}")
                appendLine("Size: ${formatFileSize(fileSize)}")
                appendLine("Total lines: $totalLines")
                if (startLine != null || endLine != null) {
                    appendLine("Showing lines ${start + 1} to $actualEnd")
                }
                appendLine()
                appendLine("Content:")
                appendLine("─".repeat(80))

                selectedLines.forEachIndexed { index, line ->
                    val lineNumber = start + index + 1
                    if (includeLineNumbers) {
                        appendLine("${lineNumber.toString().padStart(5)} | $line")
                    } else {
                        appendLine(line)
                    }
                }

                appendLine("─".repeat(80))
            }

            logger.info("Successfully read ${selectedLines.size} lines from ${file.name}")
            result
        } catch (e: Exception) {
            logger.error("Error reading file: ${e.message}", e)
            "Error reading file: ${e.message}"
        }
    }

    /**
     * Resolve path relative to the appropriate directory based on mode
     * @param pathStr File path
     * @param mode Either "source_code" (project-source), "document" (doc-source), or "system" (bot working directory)
     */
    private fun resolvePath(pathStr: String, mode: String): File {
        val path = File(pathStr)
        return if (path.isAbsolute) {
            path
        } else {
            val baseDirectory = when (mode) {
                "document" -> documentSourceDirectory
                "system" -> systemDirectory
                else -> projectSourceDirectory
            }
            File(baseDirectory, pathStr).canonicalFile
        }
    }

    /**
     * Format file size in human-readable format
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}
