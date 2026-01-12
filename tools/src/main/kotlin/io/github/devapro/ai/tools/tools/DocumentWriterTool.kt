package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Tool for creating and modifying documentation files in the doc-source folder
 *
 * Allows the AI agent to create new documentation or update existing documentation files.
 * For security, this tool only operates within the doc-source directory.
 *
 * Examples:
 * - Create new doc: {"filePath": "api/authentication.md", "content": "# Auth\n...", "mode": "create"}
 * - Update existing: {"filePath": "guide.md", "content": "Updated content", "mode": "overwrite"}
 * - Append to file: {"filePath": "changelog.md", "content": "## v2.0\n...", "mode": "append"}
 */
class DocumentWriterTool(
    private val workingDirectory: File = File(System.getProperty("user.dir")),
    private val documentRoot: String = "doc-source"
) : Tool {

    private val logger = LoggerFactory.getLogger(DocumentWriterTool::class.java)

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "write_documentation",
                description = """
                    Create or modify documentation files in the doc-source folder.
                    Use this tool to write new documentation, update existing docs, or append to existing files.
                    All file paths are relative to the doc-source directory.
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("filePath") {
                            put("type", "string")
                            put("description", "File path relative to doc-source directory (e.g., 'api/auth.md' or 'guide.md')")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "Content to write to the file")
                        }
                        putJsonObject("mode") {
                            put("type", "string")
                            putJsonObject("enum") {
                                putJsonArray("values") {
                                    add("create")
                                    add("overwrite")
                                    add("append")
                                }
                            }
                            put("description", """
                                Write mode:
                                - 'create': Create new file (fails if exists)
                                - 'overwrite': Replace entire file content (creates if doesn't exist)
                                - 'append': Add content to end of existing file (creates if doesn't exist)
                            """.trimIndent())
                            put("default", "create")
                        }
                        putJsonObject("createDirectories") {
                            put("type", "boolean")
                            put("description", "Create parent directories if they don't exist (default: true)")
                            put("default", true)
                        }
                    }
                    putJsonArray("required") {
                        add("filePath")
                        add("content")
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
        val filePath = arguments["filePath"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("'filePath' argument is required")

        val content = arguments["content"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("'content' argument is required")

        val mode = arguments["mode"]?.jsonPrimitive?.content ?: "create"
        val createDirectories = arguments["createDirectories"]?.jsonPrimitive?.booleanOrNull ?: true

        // Validate mode
        if (mode !in listOf("create", "overwrite", "append")) {
            return "Error: Invalid mode '$mode'. Must be 'create', 'overwrite', or 'append'"
        }

        logger.info("Writing documentation: filePath=$filePath, mode=$mode, createDirs=$createDirectories")

        try {
            // Resolve file path within doc-source directory
            val file = resolveDocPath(filePath)

            // Security check: ensure file is within doc-source directory
            val docRoot = File(workingDirectory, documentRoot).canonicalFile
            if (!file.canonicalPath.startsWith(docRoot.canonicalPath)) {
                return "Error: Access denied. Files can only be written within the '$documentRoot' directory"
            }

            // Check parent directory
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                if (createDirectories) {
                    logger.info("Creating directories: ${parentDir.path}")
                    if (!parentDir.mkdirs()) {
                        return "Error: Failed to create parent directories: ${parentDir.path}"
                    }
                } else {
                    return "Error: Parent directory does not exist: ${parentDir.path}"
                }
            }

            // Execute based on mode
            val result = when (mode) {
                "create" -> {
                    if (file.exists()) {
                        return "Error: File already exists: ${file.relativeTo(docRoot).path}\nUse 'overwrite' mode to replace it or 'append' mode to add content."
                    }
                    file.writeText(content)
                    "Successfully created file: ${file.relativeTo(docRoot).path}\nSize: ${formatSize(content.length.toLong())}"
                }

                "overwrite" -> {
                    val existed = file.exists()
                    file.writeText(content)
                    if (existed) {
                        "Successfully updated file: ${file.relativeTo(docRoot).path}\nSize: ${formatSize(content.length.toLong())}"
                    } else {
                        "Successfully created file: ${file.relativeTo(docRoot).path}\nSize: ${formatSize(content.length.toLong())}"
                    }
                }

                "append" -> {
                    val existed = file.exists()
                    file.appendText(content)
                    val newSize = file.length()
                    if (existed) {
                        "Successfully appended to file: ${file.relativeTo(docRoot).path}\nNew size: ${formatSize(newSize)}"
                    } else {
                        "Successfully created file: ${file.relativeTo(docRoot).path}\nSize: ${formatSize(newSize)}"
                    }
                }

                else -> "Error: Unknown mode: $mode"
            }

            logger.info("Write operation completed: $result")
            return result

        } catch (e: Exception) {
            logger.error("Error writing documentation: ${e.message}", e)
            return "Error writing file: ${e.message}"
        }
    }

    /**
     * Resolve path within doc-source directory
     */
    private fun resolveDocPath(filePath: String): File {
        // Remove any leading slashes or path traversal attempts
        val sanitizedPath = filePath
            .trim()
            .removePrefix("/")
            .removePrefix("\\")

        // Prevent path traversal
        if (sanitizedPath.contains("..")) {
            throw IllegalArgumentException("Path traversal not allowed: $filePath")
        }

        val docRoot = File(workingDirectory, documentRoot)
        return File(docRoot, sanitizedPath).canonicalFile
    }

    /**
     * Format file size in human-readable format
     */
    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size bytes"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}
