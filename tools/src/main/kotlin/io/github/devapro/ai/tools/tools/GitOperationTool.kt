package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tool for performing git operations on the project repository
 *
 * Supports:
 * - get_diff: Get diff between base and PR branch (handles all branch switching and cleanup)
 * - get_current_branch: Get the name of the current branch
 * - list_branches: List all available branches (local and remote)
 *
 * Features:
 * - Automatic branch restoration (returns to original branch even if errors occur)
 * - Branch existence validation
 * - Detached HEAD state handling
 * - Uncommitted changes detection
 * - Configurable timeout protection
 *
 * Examples:
 * - Get PR diff: {"operation": "get_diff", "prBranch": "feature/new-feature", "baseBranch": "develop"}
 * - Current branch: {"operation": "get_current_branch"}
 * - List branches: {"operation": "list_branches"}
 */
class GitOperationTool(
    private val workingDirectory: File = File(System.getProperty("user.dir")),
    private val timeoutSeconds: Long = 30
) : Tool {

    private val logger = LoggerFactory.getLogger(GitOperationTool::class.java)

    companion object {
        private const val DEFAULT_BASE_BRANCH = "develop"
        private const val MAX_DIFF_SIZE = 50 * 1024 // 50KB limit
    }

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "git_operation",
                description = """
                    Perform git operations on the project repository.

                    Operations:
                    - get_diff: Get diff between base branch and PR branch
                      Automatically handles: save current branch → checkout base → pull → checkout PR → diff → restore original branch
                      Returns formatted diff with file paths and line numbers

                    - get_current_branch: Get the name of the currently checked out branch
                      Useful for determining the active branch before operations

                    - list_branches: List all available branches (local and remote)
                      Helps identify available PR branches

                    All operations ensure the repository is left in its original state.
                    Safe to use even if there are uncommitted changes (will report and abort).
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("operation") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("get_diff")
                                add("get_current_branch")
                                add("list_branches")
                            }
                            put("description", "The git operation to perform")
                        }
                        putJsonObject("prBranch") {
                            put("type", "string")
                            put("description", "PR branch name for diff operation (required for get_diff)")
                        }
                        putJsonObject("baseBranch") {
                            put("type", "string")
                            put("description", "Base branch for diff comparison (default: develop)")
                            put("default", DEFAULT_BASE_BRANCH)
                        }
                    }
                    putJsonArray("required") {
                        add("operation")
                    }
                }
            )
        )
    }

    override suspend fun execute(arguments: JsonObject?): String {
        if (arguments == null) {
            throw IllegalArgumentException("Arguments are required")
        }

        val operation = arguments["operation"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("'operation' argument is required")

        logger.info("Executing git operation: $operation in ${workingDirectory.absolutePath}")

        // Validate working directory
        val validationError = validateWorkingDirectory()
        if (validationError != null) {
            return validationError
        }

        return try {
            when (operation) {
                "get_diff" -> {
                    val prBranch = arguments["prBranch"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("'prBranch' is required for get_diff operation")
                    val baseBranch = arguments["baseBranch"]?.jsonPrimitive?.content ?: DEFAULT_BASE_BRANCH
                    getDiff(prBranch, baseBranch)
                }
                "get_current_branch" -> getCurrentBranch()
                "list_branches" -> listBranches()
                else -> "Error: Unknown operation '$operation'"
            }
        } catch (e: Exception) {
            logger.error("Git operation failed: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Validate that the working directory exists and is a git repository
     */
    private fun validateWorkingDirectory(): String? {
        if (!workingDirectory.exists()) {
            return buildString {
                appendLine("Error: Working directory does not exist: ${workingDirectory.absolutePath}")
                appendLine()
                appendLine("To fix this:")
                appendLine("1. Set PROJECT_SOURCE_DIR in .env to point to your git repository")
                appendLine("2. Example: PROJECT_SOURCE_DIR=/path/to/your/project")
                appendLine()
                appendLine("Current working directory: ${System.getProperty("user.dir")}")
                appendLine("Configured PROJECT_SOURCE_DIR: ${workingDirectory.absolutePath}")
            }
        }

        if (!workingDirectory.isDirectory) {
            return "Error: Working directory is not a directory: ${workingDirectory.absolutePath}"
        }

        // Check if it's a git repository (has .git directory)
        val gitDir = File(workingDirectory, ".git")
        if (!gitDir.exists()) {
            return buildString {
                appendLine("Error: Directory is not a git repository: ${workingDirectory.absolutePath}")
                appendLine()
                appendLine("The directory exists but doesn't contain a .git folder.")
                appendLine()
                appendLine("To fix this:")
                appendLine("1. Initialize git in this directory: cd ${workingDirectory.absolutePath} && git init")
                appendLine("2. Or set PROJECT_SOURCE_DIR to point to an existing git repository")
                appendLine()
                appendLine("Current directory contents:")
                workingDirectory.listFiles()?.take(10)?.forEach { file ->
                    appendLine("  - ${file.name}${if (file.isDirectory) "/" else ""}")
                }
            }
        }

        return null // No validation errors
    }

    /**
     * Get the current branch name
     */
    private fun getCurrentBranch(): String {
        val output = executeGitCommand(listOf("git", "branch", "--show-current"))
        val branch = output.trim()

        return if (branch.isEmpty()) {
            // Detached HEAD state
            val commit = executeGitCommand(listOf("git", "rev-parse", "--short", "HEAD")).trim()
            "Repository is in detached HEAD state (commit: $commit)"
        } else {
            "Current branch: $branch"
        }
    }

    /**
     * List all available branches
     */
    private fun listBranches(): String {
        val output = executeGitCommand(listOf("git", "branch", "-a"))
        val branches = output.lines()
            .filter { it.isNotBlank() }
            .map { it.trim().removePrefix("* ").trim() }
            .filter { !it.contains("HEAD ->") } // Remove HEAD reference

        return buildString {
            appendLine("Available branches:")
            appendLine()
            branches.forEach { branch ->
                appendLine("  • $branch")
            }
        }
    }

    /**
     * Get diff between base and PR branch with automatic cleanup
     */
    private fun getDiff(prBranch: String, baseBranch: String): String {
        logger.info("Getting diff: $baseBranch...$prBranch")

        // Check for uncommitted changes first
        val status = executeGitCommand(listOf("git", "status", "--porcelain"))
        if (status.isNotBlank()) {
            return buildString {
                appendLine("Error: Repository has uncommitted changes:")
                appendLine()
                appendLine(status)
                appendLine()
                appendLine("Please commit or stash changes before reviewing PR:")
                appendLine("  git stash")
                appendLine("  or")
                appendLine("  git commit -am \"WIP\"")
            }
        }

        // Save current branch
        val originalBranch = try {
            executeGitCommand(listOf("git", "branch", "--show-current")).trim()
        } catch (e: Exception) {
            null // Detached HEAD or error
        }

        logger.info("Original branch: ${originalBranch ?: "detached HEAD"}")

        try {
            // Validate branches exist
            if (!branchExists(baseBranch)) {
                return "Error: Base branch '$baseBranch' does not exist.\n\n${listBranches()}"
            }

            if (!branchExists(prBranch)) {
                return "Error: PR branch '$prBranch' does not exist.\n\n${listBranches()}"
            }

            // Checkout base branch and pull
            logger.info("Checking out base branch: $baseBranch")
            executeGitCommand(listOf("git", "checkout", baseBranch))

            logger.info("Pulling latest changes")
            val pullOutput = executeGitCommand(listOf("git", "pull"))
            logger.info("Pull result: ${pullOutput.trim()}")

            // Checkout PR branch
            logger.info("Checking out PR branch: $prBranch")
            executeGitCommand(listOf("git", "checkout", prBranch))

            // Get diff statistics
            val diffStat = executeGitCommand(listOf("git", "diff", "--stat", "$baseBranch...$prBranch"))

            // Get full diff
            val fullDiff = executeGitCommand(listOf("git", "diff", "$baseBranch...$prBranch"))

            // Truncate if too large
            val diff = if (fullDiff.length > MAX_DIFF_SIZE) {
                fullDiff.substring(0, MAX_DIFF_SIZE) + "\n\n... (diff truncated, showing first 50KB)"
            } else {
                fullDiff
            }

            return buildString {
                appendLine("# Git Diff: $baseBranch → $prBranch")
                appendLine()
                appendLine("## Summary")
                appendLine(diffStat)
                appendLine()
                appendLine("## Detailed Changes")
                appendLine(diff)
            }

        } finally {
            // Always restore original branch
            if (originalBranch != null && originalBranch.isNotBlank()) {
                try {
                    logger.info("Restoring original branch: $originalBranch")
                    executeGitCommand(listOf("git", "checkout", originalBranch))
                    logger.info("Successfully restored to $originalBranch")
                } catch (e: Exception) {
                    logger.error("Failed to restore original branch: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Check if a branch exists
     */
    private fun branchExists(branchName: String): Boolean {
        return try {
            executeGitCommand(listOf("git", "rev-parse", "--verify", branchName))
            true
        } catch (e: Exception) {
            // Try with origin prefix for remote branches
            try {
                executeGitCommand(listOf("git", "rev-parse", "--verify", "origin/$branchName"))
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Execute a git command and return output
     */
    private fun executeGitCommand(command: List<String>): String {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(workingDirectory)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw IllegalStateException("Git command timed out after $timeoutSeconds seconds: ${command.joinToString(" ")}")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw IllegalStateException("Git command failed with exit code $exitCode: ${command.joinToString(" ")}\nOutput: $output")
        }

        return output
    }
}
