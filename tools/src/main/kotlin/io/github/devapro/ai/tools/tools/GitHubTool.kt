package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Tool for fetching GitHub repository and PR information via GitHub REST API
 *
 * Supports:
 * - Fetching PR details (title, description, branch, author, state, etc.)
 * - Parsing GitHub PR URLs
 * - Works with both public and private repositories (with token)
 * - Handles rate limiting and error responses
 *
 * Authentication:
 * - Public repos: Works without token (60 requests/hour)
 * - Private repos: Requires GitHub Personal Access Token (5000 requests/hour)
 *
 * Examples:
 * - Get PR info: {"operation": "get_pr", "url": "https://github.com/owner/repo/pull/123"}
 * - Get PR info: {"operation": "get_pr", "owner": "owner", "repo": "repo", "prNumber": 123}
 */
class GitHubTool(
    private val httpClient: HttpClient,
    private val githubToken: String? = null
) : Tool {

    private val logger = LoggerFactory.getLogger(GitHubTool::class.java)

    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private val PR_URL_REGEX = Regex("""https?://github\.com/([^/]+)/([^/]+)/pull/(\d+)""")
    }

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "github_api",
                description = """
                    Fetch GitHub repository and Pull Request information via GitHub REST API.

                    Operations:
                    - get_pr: Fetch PR details (title, description, branch, author, state, etc.)

                    You can provide either:
                    - A GitHub PR URL: https://github.com/owner/repo/pull/123
                    - Or separate owner, repo, and PR number

                    Returns comprehensive PR information including:
                    - Basic info: title, description, state (open/closed/merged), author
                    - Branch info: head branch, base branch
                    - Metadata: created date, updated date, merge status
                    - Statistics: comments count, commits count, changed files

                    Works with public repositories without authentication.
                    For private repositories, configure GITHUB_TOKEN in .env file.
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("operation") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("get_pr")
                            }
                            put("description", "The GitHub operation to perform")
                        }
                        putJsonObject("url") {
                            put("type", "string")
                            put("description", "GitHub PR URL (e.g., https://github.com/owner/repo/pull/123)")
                        }
                        putJsonObject("owner") {
                            put("type", "string")
                            put("description", "Repository owner (alternative to URL)")
                        }
                        putJsonObject("repo") {
                            put("type", "string")
                            put("description", "Repository name (alternative to URL)")
                        }
                        putJsonObject("prNumber") {
                            put("type", "integer")
                            put("description", "Pull request number (alternative to URL)")
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

        logger.info("Executing GitHub operation: $operation")

        return try {
            when (operation) {
                "get_pr" -> getPullRequest(arguments)
                else -> "Error: Unknown operation '$operation'"
            }
        } catch (e: Exception) {
            logger.error("GitHub operation failed: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Get Pull Request details
     */
    private suspend fun getPullRequest(arguments: JsonObject): String {
        // Parse input (URL or owner/repo/prNumber)
        val prInfo = parsePrInput(arguments)
            ?: return "Error: Please provide either 'url' or all of 'owner', 'repo', and 'prNumber'"

        logger.info("Fetching PR: ${prInfo.owner}/${prInfo.repo}#${prInfo.prNumber}")

        // Fetch PR from GitHub API
        val apiUrl = "$GITHUB_API_BASE/repos/${prInfo.owner}/${prInfo.repo}/pulls/${prInfo.prNumber}"

        try {
            val response = httpClient.get(apiUrl) {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                if (!githubToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $githubToken")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                return buildString {
                    appendLine("Error: GitHub API request failed with status ${response.status}")
                    appendLine()
                    when (response.status) {
                        HttpStatusCode.NotFound -> {
                            appendLine("Pull Request not found. Possible reasons:")
                            appendLine("- PR number is incorrect")
                            appendLine("- Repository doesn't exist or is private")
                            appendLine("- You don't have access (configure GITHUB_TOKEN for private repos)")
                        }
                        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                            appendLine("Authentication failed or rate limit exceeded.")
                            appendLine()
                            if (githubToken.isNullOrBlank()) {
                                appendLine("Note: No GitHub token configured. Set GITHUB_TOKEN in .env for:")
                                appendLine("- Access to private repositories")
                                appendLine("- Higher rate limits (5000 req/hour vs 60 req/hour)")
                            } else {
                                appendLine("Your GitHub token may be invalid or expired.")
                            }
                        }
                        else -> {
                            appendLine("Response: $errorBody")
                        }
                    }
                }
            }

            val prData = response.body<GitHubPullRequest>()
            return formatPullRequest(prData, prInfo)

        } catch (e: Exception) {
            logger.error("Failed to fetch PR: ${e.message}", e)
            return "Error fetching PR from GitHub: ${e.message}"
        }
    }

    /**
     * Parse PR input from arguments (URL or owner/repo/prNumber)
     */
    private fun parsePrInput(arguments: JsonObject): PrInfo? {
        // Try to parse URL first
        val url = arguments["url"]?.jsonPrimitive?.content
        if (!url.isNullOrBlank()) {
            val match = PR_URL_REGEX.find(url)
            if (match != null) {
                val (owner, repo, prNumber) = match.destructured
                return PrInfo(owner, repo, prNumber.toInt(), url)
            } else {
                throw IllegalArgumentException("Invalid GitHub PR URL format. Expected: https://github.com/owner/repo/pull/123")
            }
        }

        // Try to parse individual fields
        val owner = arguments["owner"]?.jsonPrimitive?.content
        val repo = arguments["repo"]?.jsonPrimitive?.content
        val prNumber = arguments["prNumber"]?.jsonPrimitive?.intOrNull

        return if (owner != null && repo != null && prNumber != null) {
            val constructedUrl = "https://github.com/$owner/$repo/pull/$prNumber"
            PrInfo(owner, repo, prNumber, constructedUrl)
        } else {
            null
        }
    }

    /**
     * Format PR data into readable text
     */
    private fun formatPullRequest(pr: GitHubPullRequest, info: PrInfo): String {
        return buildString {
            appendLine("# GitHub Pull Request Details")
            appendLine()
            appendLine("**Repository:** ${info.owner}/${info.repo}")
            appendLine("**PR Number:** #${info.prNumber}")
            appendLine("**URL:** ${info.url}")
            appendLine()

            appendLine("## Basic Information")
            appendLine()
            appendLine("**Title:** ${pr.title}")
            appendLine("**State:** ${pr.state.uppercase()}")
            if (pr.merged) {
                appendLine("**Merged:** Yes ✅")
                pr.mergedAt?.let { appendLine("**Merged At:** $it") }
            } else if (pr.state == "closed") {
                appendLine("**Merged:** No (closed without merging)")
            }
            appendLine("**Author:** ${pr.user.login}")
            appendLine("**Created:** ${pr.createdAt}")
            appendLine("**Updated:** ${pr.updatedAt}")
            appendLine()

            appendLine("## Branch Information")
            appendLine()
            appendLine("**Head Branch:** ${pr.head.ref} (${pr.head.repo?.fullName ?: "deleted"})")
            appendLine("**Base Branch:** ${pr.base.ref} (${pr.base.repo?.fullName ?: "unknown"})")
            appendLine()

            appendLine("## Description")
            appendLine()
            if (!pr.body.isNullOrBlank()) {
                appendLine(pr.body)
            } else {
                appendLine("_No description provided_")
            }
            appendLine()

            appendLine("## Statistics")
            appendLine()
            appendLine("- **Comments:** ${pr.comments}")
            appendLine("- **Commits:** ${pr.commits}")
            appendLine("- **Changed Files:** ${pr.changedFiles}")
            appendLine("- **Additions:** +${pr.additions} lines")
            appendLine("- **Deletions:** -${pr.deletions} lines")
            appendLine()

            if (pr.labels.isNotEmpty()) {
                appendLine("## Labels")
                appendLine()
                pr.labels.forEach { label ->
                    appendLine("- ${label.name}")
                }
                appendLine()
            }

            if (pr.assignees.isNotEmpty()) {
                appendLine("## Assignees")
                appendLine()
                pr.assignees.forEach { assignee ->
                    appendLine("- ${assignee.login}")
                }
                appendLine()
            }

            if (pr.requestedReviewers.isNotEmpty()) {
                appendLine("## Requested Reviewers")
                appendLine()
                pr.requestedReviewers.forEach { reviewer ->
                    appendLine("- ${reviewer.login}")
                }
                appendLine()
            }

            appendLine("---")
            appendLine()
            appendLine("_Fetched from GitHub API at ${java.time.Instant.now()}_")
        }
    }

    /**
     * Data class for parsed PR information
     */
    private data class PrInfo(
        val owner: String,
        val repo: String,
        val prNumber: Int,
        val url: String
    )

    /**
     * GitHub API response models
     */
    @Serializable
    private data class GitHubPullRequest(
        val number: Int,
        val title: String,
        val state: String,
        val body: String? = null,
        val user: GitHubUser,
        val head: GitHubBranch,
        val base: GitHubBranch,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("merged_at") val mergedAt: String? = null,
        val merged: Boolean = false,
        val comments: Int = 0,
        val commits: Int = 0,
        @SerialName("changed_files") val changedFiles: Int = 0,
        val additions: Int = 0,
        val deletions: Int = 0,
        val labels: List<GitHubLabel> = emptyList(),
        val assignees: List<GitHubUser> = emptyList(),
        @SerialName("requested_reviewers") val requestedReviewers: List<GitHubUser> = emptyList()
    )

    @Serializable
    private data class GitHubUser(
        val login: String,
        val id: Long,
        @SerialName("avatar_url") val avatarUrl: String? = null
    )

    @Serializable
    private data class GitHubBranch(
        val ref: String,
        val sha: String,
        val repo: GitHubRepository?
    )

    @Serializable
    private data class GitHubRepository(
        val name: String,
        @SerialName("full_name") val fullName: String
    )

    @Serializable
    private data class GitHubLabel(
        val name: String,
        val color: String? = null
    )
}
