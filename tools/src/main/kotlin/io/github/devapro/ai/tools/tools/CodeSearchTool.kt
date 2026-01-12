package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tool for searching code patterns across the project using grep/ripgrep
 *
 * Supports:
 * - Text and regex pattern search
 * - File type filtering (*.kt, *.java, etc.)
 * - Case-sensitive and case-insensitive search
 * - Context lines (show lines before/after match)
 * - Configurable result limits
 * - Uses ripgrep (rg) if available, falls back to grep
 *
 * Examples:
 * - Search for class: {"pattern": "class AuthService", "path": "src"}
 * - Find usages: {"pattern": "processPayment", "ignoreCase": true}
 * - Search in Kotlin files: {"pattern": "suspend fun", "filePattern": "*.kt"}
 * - With context: {"pattern": "TODO", "contextLines": 2}
 */
class CodeSearchTool(
    private val workingDirectory: File = File(System.getProperty("user.dir")),
    private val maxResults: Int = 100,
    private val timeoutSeconds: Long = 30
) : Tool {

    private val logger = LoggerFactory.getLogger(CodeSearchTool::class.java)

    // Check which search tool is available
    private val searchCommand: String by lazy {
        when {
            isCommandAvailable("rg") -> "rg"
            isCommandAvailable("grep") -> "grep"
            else -> throw IllegalStateException("Neither ripgrep (rg) nor grep is available")
        }
    }

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "search_code",
                description = """
                    Search for text patterns across the project source code.
                    Uses ripgrep (rg) or grep for fast searching.

                    Use cases:
                    - Find where classes/functions/variables are used
                    - Search for specific text or code patterns
                    - Find all occurrences of an API call
                    - Search for TODO/FIXME comments
                    - Trace implementations across files

                    The tool searches recursively in the specified directory.
                    Results include file paths, line numbers, and matched content.
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("pattern") {
                            put("type", "string")
                            put("description", "Text or regex pattern to search for (e.g., 'class AuthService', 'processPayment', 'TODO')")
                        }
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Starting directory for search, relative to project root (default: search entire project)")
                        }
                        putJsonObject("filePattern") {
                            put("type", "string")
                            put("description", "Filter by file pattern using glob (e.g., '*.kt', '*.java', '*.{ts,tsx}')")
                        }
                        putJsonObject("ignoreCase") {
                            put("type", "boolean")
                            put("description", "Case-insensitive search (default: false)")
                            put("default", false)
                        }
                        putJsonObject("contextLines") {
                            put("type", "integer")
                            put("description", "Number of lines to show before and after each match (default: 0, max: 5)")
                            put("default", 0)
                            put("minimum", 0)
                            put("maximum", 5)
                        }
                        putJsonObject("maxResults") {
                            put("type", "integer")
                            put("description", "Maximum number of matches to return (default: 50, max: $maxResults)")
                            put("default", 50)
                            put("minimum", 1)
                            put("maximum", maxResults)
                        }
                        putJsonObject("wholeWord") {
                            put("type", "boolean")
                            put("description", "Match whole words only (default: false)")
                            put("default", false)
                        }
                    }
                    putJsonArray("required") {
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
        val pattern = arguments["pattern"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("'pattern' argument is required")

        val pathStr = arguments["path"]?.jsonPrimitive?.content
        val filePattern = arguments["filePattern"]?.jsonPrimitive?.content
        val ignoreCase = arguments["ignoreCase"]?.jsonPrimitive?.booleanOrNull ?: false
        val contextLines = arguments["contextLines"]?.jsonPrimitive?.intOrNull ?: 0
        val maxResultsParam = arguments["maxResults"]?.jsonPrimitive?.intOrNull ?: 50
        val wholeWord = arguments["wholeWord"]?.jsonPrimitive?.booleanOrNull ?: false

        // Validate context lines
        val validContextLines = contextLines.coerceIn(0, 5)

        // Validate max results
        val validMaxResults = maxResultsParam.coerceIn(1, maxResults)

        logger.info("Searching: pattern='$pattern', path=$pathStr, filePattern=$filePattern, ignoreCase=$ignoreCase, contextLines=$validContextLines")

        // Resolve search path
        val searchPath = if (pathStr != null) {
            val path = File(pathStr)
            if (path.isAbsolute) {
                path
            } else {
                File(workingDirectory, pathStr).canonicalFile
            }
        } else {
            workingDirectory
        }

        if (!searchPath.exists()) {
            return "Error: Search path does not exist: ${searchPath.absolutePath}"
        }

        // Verify search command is available
        if (searchCommand != "rg" && searchCommand != "grep") {
            return "Error: No search tool available (neither ripgrep nor grep found)"
        }

        // Perform search
        return try {
            val results: List<SearchMatch> = when (searchCommand) {
                "rg" -> searchWithRipgrep(pattern, searchPath, filePattern, ignoreCase, validContextLines, wholeWord, validMaxResults)
                "grep" -> searchWithGrep(pattern, searchPath, filePattern, ignoreCase, validContextLines, wholeWord, validMaxResults)
                else -> emptyList()
            }

            if (results.isEmpty()) {
                "No matches found for pattern: '$pattern'"
            } else {
                formatResults(pattern, searchPath, results, validMaxResults)
            }
        } catch (e: Exception) {
            logger.error("Search failed: ${e.message}", e)
            "Error during search: ${e.message}"
        }
    }

    /**
     * Search using ripgrep (fastest option)
     */
    private fun searchWithRipgrep(
        pattern: String,
        searchPath: File,
        filePattern: String?,
        ignoreCase: Boolean,
        contextLines: Int,
        wholeWord: Boolean,
        maxResults: Int
    ): List<SearchMatch> {
        val command = mutableListOf(
            "rg",
            "--line-number",           // Show line numbers
            "--no-heading",             // Don't group by file
            "--with-filename",          // Always show filename
            "--max-count", maxResults.toString()  // Limit matches per file
        )

        // Add options
        if (ignoreCase) command.add("--ignore-case")
        if (wholeWord) command.add("--word-regexp")
        if (contextLines > 0) {
            command.add("--context")
            command.add(contextLines.toString())
        }

        // Add file pattern filter
        if (filePattern != null) {
            command.add("--glob")
            command.add(filePattern)
        }

        // Add pattern and path
        command.add(pattern)
        command.add(searchPath.absolutePath)

        return executeSearchCommand(command)
    }

    /**
     * Search using grep (fallback option)
     */
    private fun searchWithGrep(
        pattern: String,
        searchPath: File,
        filePattern: String?,
        ignoreCase: Boolean,
        contextLines: Int,
        wholeWord: Boolean,
        maxResults: Int
    ): List<SearchMatch> {
        val command = mutableListOf(
            "grep",
            "-r",                       // Recursive
            "-n",                       // Line numbers
            "-H"                        // Always show filename
        )

        // Add options
        if (ignoreCase) command.add("-i")
        if (wholeWord) command.add("-w")
        if (contextLines > 0) {
            command.add("-C")
            command.add(contextLines.toString())
        }

        // Add file pattern filter
        if (filePattern != null) {
            command.add("--include=$filePattern")
        }

        // Add pattern and path
        command.add(pattern)
        command.add(searchPath.absolutePath)

        val results = executeSearchCommand(command)

        // Grep doesn't have built-in max results, so we limit manually
        return results.take(maxResults)
    }

    /**
     * Execute search command and parse output
     */
    private fun executeSearchCommand(command: List<String>): List<SearchMatch> {
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw IllegalStateException("Search timed out after $timeoutSeconds seconds")
        }

        // Exit code 1 for grep/rg means no matches found (not an error)
        val exitCode = process.exitValue()
        if (exitCode != 0 && exitCode != 1) {
            logger.warn("Search command failed with exit code $exitCode: $output")
            return emptyList()
        }

        return parseSearchOutput(output)
    }

    /**
     * Parse grep/ripgrep output into structured matches
     */
    private fun parseSearchOutput(output: String): List<SearchMatch> {
        if (output.isBlank()) return emptyList()

        val matches = mutableListOf<SearchMatch>()
        val lines = output.lines()

        for (line in lines) {
            if (line.isBlank()) continue

            // Format: /path/to/file:123:matched content
            // or with context: /path/to/file:123-context line
            val parts = line.split(":", limit = 3)
            if (parts.size >= 3) {
                val file = parts[0]
                val lineNumber = parts[1].toIntOrNull()
                val content = parts[2]

                if (lineNumber != null) {
                    matches.add(SearchMatch(
                        file = file,
                        lineNumber = lineNumber,
                        content = content
                    ))
                }
            } else {
                // Handle context lines (marked with '-' instead of ':')
                val contextParts = line.split("-", limit = 3)
                if (contextParts.size >= 3) {
                    val file = contextParts[0]
                    val lineNumber = contextParts[1].toIntOrNull()
                    val content = contextParts[2]

                    if (lineNumber != null) {
                        matches.add(SearchMatch(
                            file = file,
                            lineNumber = lineNumber,
                            content = content,
                            isContext = true
                        ))
                    }
                }
            }
        }

        return matches
    }

    /**
     * Format search results for display
     */
    private fun formatResults(
        pattern: String,
        searchPath: File,
        matches: List<SearchMatch>,
        maxResults: Int
    ): String {
        val limitedMatches = matches.take(maxResults)
        val wasLimited = matches.size > maxResults

        return buildString {
            appendLine("Search Results for: '$pattern'")
            appendLine("Search Path: ${searchPath.absolutePath}")
            appendLine("Found: ${matches.size} match(es)${if (wasLimited) " (showing first $maxResults)" else ""}")
            appendLine()
            appendLine("─".repeat(80))

            // Group matches by file
            val groupedByFile = limitedMatches.groupBy { it.file }

            groupedByFile.forEach { (file, fileMatches) ->
                appendLine()
                appendLine("📄 $file (${fileMatches.count { !it.isContext }} match(es))")
                appendLine()

                fileMatches.forEach { match ->
                    val prefix = if (match.isContext) "   " else " ➜ "
                    appendLine("$prefix${match.lineNumber.toString().padStart(5)} | ${match.content}")
                }
            }

            appendLine()
            appendLine("─".repeat(80))

            if (wasLimited) {
                appendLine()
                appendLine("⚠️  Results limited to $maxResults matches. Refine your search pattern for more specific results.")
            }
        }
    }

    /**
     * Check if a command is available in PATH
     */
    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val process = ProcessBuilder("which", command).start()
            process.waitFor(5, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Data class representing a search match
     */
    private data class SearchMatch(
        val file: String,
        val lineNumber: Int,
        val content: String,
        val isContext: Boolean = false
    )
}
