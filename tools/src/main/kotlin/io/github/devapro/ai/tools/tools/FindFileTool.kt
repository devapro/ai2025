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
import kotlin.io.path.name

/**
 * Tool for finding files in a directory and its subdirectories
 *
 * Supports multiple search criteria:
 * - Name pattern (glob-style: *.txt, test*.java, etc.)
 * - Maximum depth for recursion
 * - Maximum number of results
 *
 * Examples:
 * - Find all Kotlin files: {"path": ".", "pattern": "*.kt"}
 * - Find test files: {"path": "src", "pattern": "*Test.kt", "maxDepth": 5}
 * - Find specific file: {"path": ".", "pattern": "Main.kt", "maxResults": 1}
 */
class FindFileTool(
    private val workingDirectory: File = File(System.getProperty("user.dir"))
) : Tool {

    private val logger = LoggerFactory.getLogger(FindFileTool::class.java)

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "find_file",
                description = """
                    Find files in the project directory and its subdirectories.
                    Supports glob patterns (e.g., *.kt, test*.java, Main.*).
                    Returns relative paths from the search directory.
                    IMPORTANT: All paths are relative to the project root. Use "." to search from root.
                    Do NOT include "project-source/" prefix in paths.
                """.trimIndent(),
                parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Directory path to search in, relative to project root (use '.' for root directory, or subdirectory like 'src' or 'AndroidRepo/features')")
            }
            putJsonObject("pattern") {
                put("type", "string")
                put("description", "File name pattern using glob syntax (e.g., *.kt, Test*.java, config.*)")
            }
            putJsonObject("maxDepth") {
                put("type", "integer")
                put("description", "Maximum depth for recursive search (default: unlimited)")
                put("default", Int.MAX_VALUE)
            }
            putJsonObject("maxResults") {
                put("type", "integer")
                put("description", "Maximum number of results to return (default: 100)")
                put("default", 100)
            }
        }
        putJsonArray("required") {
            add("path")
            add("pattern")
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

        val pattern = arguments["pattern"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("'pattern' argument is required")

        val maxDepth = arguments["maxDepth"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE
        val maxResults = arguments["maxResults"]?.jsonPrimitive?.intOrNull ?: 100

        logger.info("Finding files: path=$pathStr, pattern=$pattern, maxDepth=$maxDepth, maxResults=$maxResults")

        // Resolve path
        val searchPath = resolvePath(pathStr)
        if (!searchPath.exists()) {
            return "Error: Directory does not exist: ${searchPath.absolutePath}"
        }

        if (!searchPath.isDirectory) {
            return "Error: Path is not a directory: ${searchPath.absolutePath}"
        }

        // Convert glob pattern to regex
        val regex = globToRegex(pattern)

        // Search for files
        val results = mutableListOf<Path>()
        searchFiles(searchPath.toPath(), regex, 0, maxDepth, maxResults, results)

        // Format results
        if (results.isEmpty()) {
            return "No files found matching pattern '$pattern' in ${searchPath.absolutePath}"
        }

        val searchPathAbs = searchPath.toPath().toAbsolutePath().normalize()
        val resultText = buildString {
            appendLine("Found ${results.size} file(s) matching pattern '$pattern':")
            appendLine()
            results.forEach { path ->
                val absolutePath = path.toAbsolutePath().normalize()
                val relativePath = try {
                    searchPathAbs.relativize(absolutePath).toString()
                } catch (e: Exception) {
                    absolutePath.toString()
                }
                appendLine("- $relativePath")
            }
        }

        logger.info("Found ${results.size} files")
        return resultText
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
     * Convert glob pattern to regex
     * Supports: * (any chars), ? (single char), . (literal dot)
     */
    private fun globToRegex(glob: String): Regex {
        val regex = buildString {
            append("^")
            glob.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("\\.")
                    '[', ']', '(', ')', '{', '}', '+', '^', '$', '|', '\\' -> {
                        append("\\")
                        append(char)
                    }
                    else -> append(char)
                }
            }
            append("$")
        }
        return Regex(regex, RegexOption.IGNORE_CASE)
    }

    /**
     * Recursively search for files matching the pattern
     */
    private fun searchFiles(
        dir: Path,
        pattern: Regex,
        currentDepth: Int,
        maxDepth: Int,
        maxResults: Int,
        results: MutableList<Path>
    ) {
        if (results.size >= maxResults) return
        if (currentDepth > maxDepth) return

        try {
            Files.list(dir).use { stream ->
                stream.forEach { path ->
                    if (results.size >= maxResults) return@forEach

                    try {
                        when {
                            Files.isRegularFile(path) -> {
                                // Check if filename matches pattern
                                if (pattern.matches(path.name)) {
                                    results.add(path)
                                }
                            }
                            path.isDirectory() -> {
                                // Recurse into subdirectories
                                searchFiles(path, pattern, currentDepth + 1, maxDepth, maxResults, results)
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Skipping path ${path}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error reading directory ${dir}: ${e.message}")
        }
    }
}
