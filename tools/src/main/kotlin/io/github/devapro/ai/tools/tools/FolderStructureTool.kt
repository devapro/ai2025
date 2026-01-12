package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Tool for displaying folder structure (files and subdirectories)
 *
 * Shows a tree-like view of the directory contents, helping users understand
 * project organization and locate files quickly.
 *
 * Examples:
 * - Show project root: {"path": "project-source", "maxDepth": 2}
 * - Show specific module: {"path": "project-source/src", "maxDepth": 3}
 * - Show with file details: {"path": ".", "showFiles": true, "showSizes": true}
 */
class FolderStructureTool(
    private val workingDirectory: File = File(System.getProperty("user.dir"))
) : Tool {

    private val logger = LoggerFactory.getLogger(FolderStructureTool::class.java)

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "folder_structure",
                description = """
                    Display the structure of a folder showing files and subdirectories in a tree format.
                    Useful for understanding project organization and locating files.
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Directory path to display (absolute or relative to working directory)")
                        }
                        putJsonObject("maxDepth") {
                            put("type", "integer")
                            put("description", "Maximum depth to traverse (default: 3, use higher values for deeper exploration)")
                            put("default", 3)
                            put("minimum", 1)
                            put("maximum", 10)
                        }
                        putJsonObject("showFiles") {
                            put("type", "boolean")
                            put("description", "Include files in the output (default: true)")
                            put("default", true)
                        }
                        putJsonObject("showHidden") {
                            put("type", "boolean")
                            put("description", "Include hidden files/folders (starting with .) (default: false)")
                            put("default", false)
                        }
                        putJsonObject("showSizes") {
                            put("type", "boolean")
                            put("description", "Show file sizes (default: false)")
                            put("default", false)
                        }
                        putJsonObject("maxItems") {
                            put("type", "integer")
                            put("description", "Maximum number of items to show per directory (default: 100)")
                            put("default", 100)
                            put("minimum", 1)
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

        val maxDepth = arguments["maxDepth"]?.jsonPrimitive?.intOrNull ?: 3
        val showFiles = arguments["showFiles"]?.jsonPrimitive?.booleanOrNull ?: true
        val showHidden = arguments["showHidden"]?.jsonPrimitive?.booleanOrNull ?: false
        val showSizes = arguments["showSizes"]?.jsonPrimitive?.booleanOrNull ?: false
        val maxItems = arguments["maxItems"]?.jsonPrimitive?.intOrNull ?: 100

        logger.info("Displaying folder structure: path=$pathStr, maxDepth=$maxDepth, showFiles=$showFiles")

        // Resolve path
        val folder = resolvePath(pathStr)
        if (!folder.exists()) {
            return "Error: Directory does not exist: ${folder.absolutePath}"
        }

        if (!folder.isDirectory) {
            return "Error: Path is not a directory: ${folder.absolutePath}"
        }

        // Build folder structure
        val stats = FolderStats()
        val result = buildString {
            appendLine("Folder Structure: ${folder.absolutePath}")
            appendLine()

            buildTree(
                dir = folder.toPath(),
                prefix = "",
                isLast = true,
                currentDepth = 0,
                maxDepth = maxDepth,
                showFiles = showFiles,
                showHidden = showHidden,
                showSizes = showSizes,
                maxItems = maxItems,
                output = this,
                stats = stats
            )

            appendLine()
            appendLine("Summary:")
            appendLine("  Directories: ${stats.dirCount}")
            if (showFiles) {
                appendLine("  Files: ${stats.fileCount}")
                if (showSizes) {
                    appendLine("  Total size: ${formatFileSize(stats.totalSize)}")
                }
            }
            if (stats.skippedItems > 0) {
                appendLine("  Skipped: ${stats.skippedItems} items (hidden or limit reached)")
            }
        }

        logger.info("Folder structure generated: ${stats.dirCount} dirs, ${stats.fileCount} files")
        return result
    }

    /**
     * Recursively build tree structure
     */
    private fun buildTree(
        dir: Path,
        prefix: String,
        isLast: Boolean,
        currentDepth: Int,
        maxDepth: Int,
        showFiles: Boolean,
        showHidden: Boolean,
        showSizes: Boolean,
        maxItems: Int,
        output: StringBuilder,
        stats: FolderStats
    ) {
        // Current directory line (except for root)
        if (currentDepth > 0) {
            val connector = if (isLast) "└── " else "├── "
            val dirName = dir.name
            output.appendLine("$prefix$connector$dirName/")
        }

        // Stop if max depth reached
        if (currentDepth >= maxDepth) {
            return
        }

        // Prepare prefix for children
        val childPrefix = if (currentDepth > 0) {
            prefix + (if (isLast) "    " else "│   ")
        } else {
            ""
        }

        try {
            // List directory contents
            val items = Files.list(dir).use { stream ->
                stream
                    .filter { path ->
                        showHidden || !path.name.startsWith(".")
                    }
                    .sorted { p1, p2 ->
                        // Directories first, then files, both alphabetically
                        when {
                            p1.isDirectory() && !p2.isDirectory() -> -1
                            !p1.isDirectory() && p2.isDirectory() -> 1
                            else -> p1.name.compareTo(p2.name, ignoreCase = true)
                        }
                    }
                    .toList()
            }

            if (items.size > maxItems) {
                stats.skippedItems += items.size - maxItems
            }

            val limitedItems = items.take(maxItems)

            val currentStats = stats
            limitedItems.forEachIndexed { index, path ->
                val isLastItem = index == limitedItems.lastIndex

                when {
                    path.isDirectory() -> {
                        currentStats.dirCount++
                        buildTree(
                            dir = path,
                            prefix = childPrefix,
                            isLast = isLastItem,
                            currentDepth = currentDepth + 1,
                            maxDepth = maxDepth,
                            showFiles = showFiles,
                            showHidden = showHidden,
                            showSizes = showSizes,
                            maxItems = maxItems,
                            output = output,
                            stats = currentStats
                        )
                    }
                    path.isRegularFile() && showFiles -> {
                        currentStats.fileCount++
                        val fileSize = try {
                            val size = Files.size(path)
                            currentStats.totalSize += size
                            size
                        } catch (e: Exception) {
                            0L
                        }

                        val connector = if (isLastItem) "└── " else "├── "
                        val fileName = path.name
                        val sizeStr = if (showSizes) " (${formatFileSize(fileSize)})" else ""

                        output.appendLine("$childPrefix$connector$fileName$sizeStr")
                    }
                }
            }

            // Show if there are more items
            if (items.size > maxItems) {
                val remaining = items.size - maxItems
                output.appendLine("$childPrefix... and $remaining more items")
            }

        } catch (e: Exception) {
            logger.warn("Error reading directory ${dir}: ${e.message}")
            output.appendLine("$childPrefix[Error reading directory: ${e.message}]")
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
     * Format file size in human-readable format
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * Statistics collector
     */
    private data class FolderStats(
        var dirCount: Int = 0,
        var fileCount: Int = 0,
        var totalSize: Long = 0L,
        var skippedItems: Int = 0
    )
}
