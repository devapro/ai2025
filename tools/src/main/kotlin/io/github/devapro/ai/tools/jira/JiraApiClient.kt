package io.github.devapro.ai.tools.jira

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Client for interacting with JIRA REST API
 *
 * Handles:
 * - Authentication (Basic Auth with email + API token)
 * - HTTP requests to JIRA endpoints
 * - Response parsing and error handling
 * - Works with both Cloud and Server/Data Center instances
 */
class JiraApiClient(
    private val httpClient: HttpClient,
    private val jiraUrl: String,
    private val jiraEmail: String,
    private val jiraToken: String
) {
    private val logger = LoggerFactory.getLogger(JiraApiClient::class.java)

    companion object {
        val JIRA_ISSUE_KEY_REGEX = Regex("""([A-Z][A-Z0-9]+)-(\d+)""")
        val JIRA_URL_REGEX = Regex("""https?://([^/]+)/browse/([A-Z][A-Z0-9]+-\d+)""")
    }

    /**
     * Get normalized JIRA base URL (without trailing slash)
     */
    private val normalizedJiraUrl = jiraUrl.removeSuffix("/")

    /**
     * Create Basic Auth credentials
     */
    private val credentials = "$jiraEmail:$jiraToken".encodeBase64()

    /**
     * Fetch issue details by key
     */
    suspend fun getIssue(issueKey: String): Result<JiraIssue> {
        logger.info("Fetching JIRA issue: $issueKey")

        val apiUrl = "$normalizedJiraUrl/rest/api/3/issue/$issueKey"

        return try {
            val response = httpClient.get(apiUrl) {
                header("Authorization", "Basic $credentials")
                header("Accept", "application/json")
            }

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                val errorMessage = buildErrorMessage(response.status, issueKey, errorBody)
                Result.failure(JiraApiException(errorMessage, response.status.value))
            } else {
                val issueData = response.body<JiraIssue>()
                Result.success(issueData)
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch JIRA issue: ${e.message}", e)
            val errorMessage = buildString {
                appendLine("Error fetching JIRA issue: ${e.message}")
                appendLine()
                appendLine("Troubleshooting:")
                appendLine("1. Verify JIRA_URL is correct: $jiraUrl")
                appendLine("2. Check if you can access: $normalizedJiraUrl/browse/$issueKey")
                appendLine("3. Verify your API token is still valid")
            }
            Result.failure(JiraApiException(errorMessage))
        }
    }

    /**
     * Search for issues using JQL query
     */
    suspend fun searchIssues(
        jql: String,
        maxResults: Int = 50,
        fields: String = "summary,status,issuetype,priority,assignee,created,updated"
    ): Result<JiraSearchResult> {
        logger.info("Searching JIRA issues with JQL: $jql")

        val apiUrl = "$normalizedJiraUrl/rest/api/3/search/jql"

        return try {
            val response = httpClient.get(apiUrl) {
                header("Authorization", "Basic $credentials")
                header("Accept", "application/json")
                parameter("jql", jql)
                parameter("maxResults", maxResults)
                parameter("fields", fields)
            }

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                Result.failure(
                    JiraApiException(
                        "JIRA API request failed with status ${response.status}\n$errorBody",
                        response.status.value
                    )
                )
            } else {
                val searchResult = response.body<JiraSearchResult>()
                Result.success(searchResult)
            }
        } catch (e: Exception) {
            logger.error("Failed to search JIRA issues: ${e.message}", e)
            Result.failure(JiraApiException("Error searching JIRA issues: ${e.message}"))
        }
    }

    /**
     * Create a new issue
     */
    suspend fun createIssue(
        projectKey: String,
        summary: String,
        description: String?,
        issueType: String,
        priority: String?,
        assignee: String?,
        labels: List<String>
    ): Result<JiraCreateResult> {
        logger.info("Creating issue in project $projectKey: $summary")

        val apiUrl = "$normalizedJiraUrl/rest/api/3/issue"

        return try {
            // Build request payload
            val payload = buildJsonObject {
                putJsonObject("fields") {
                    putJsonObject("project") {
                        put("key", projectKey)
                    }
                    put("summary", summary)
                    if (!description.isNullOrBlank()) {
                        putJsonObject("description") {
                            put("type", "doc")
                            put("version", 1)
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "paragraph")
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "text")
                                            put("text", description)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    putJsonObject("issuetype") {
                        put("name", issueType)
                    }
                    if (!priority.isNullOrBlank()) {
                        putJsonObject("priority") {
                            put("name", priority)
                        }
                    }
                    if (!assignee.isNullOrBlank()) {
                        putJsonObject("assignee") {
                            // Try to use as accountId first, fallback to email
                            if (assignee.contains("@")) {
                                put("emailAddress", assignee)
                            } else {
                                put("accountId", assignee)
                            }
                        }
                    }
                    if (labels.isNotEmpty()) {
                        putJsonArray("labels") {
                            labels.forEach { add(it) }
                        }
                    }
                }
            }

            val response = httpClient.post(apiUrl) {
                header("Authorization", "Basic $credentials")
                header("Accept", "application/json")
                header("Content-Type", "application/json")
                setBody(payload.toString())
            }

            if (response.status != HttpStatusCode.Created) {
                val errorBody = response.bodyAsText()
                val errorMessage = buildString {
                    appendLine("Failed to create issue (status ${response.status})")
                    appendLine()
                    appendLine("Response:")
                    appendLine(errorBody)
                    appendLine()
                    appendLine("Troubleshooting:")
                    appendLine("- Verify issue type '$issueType' exists in project $projectKey")
                    if (!priority.isNullOrBlank()) {
                        appendLine("- Verify priority '$priority' is valid")
                    }
                    if (!assignee.isNullOrBlank()) {
                        appendLine("- Verify assignee '$assignee' exists (use accountId or email)")
                    }
                }
                Result.failure(JiraApiException(errorMessage, response.status.value))
            } else {
                val createResult = response.body<JiraCreateResult>()
                Result.success(createResult)
            }
        } catch (e: Exception) {
            logger.error("Failed to create issue: ${e.message}", e)
            val errorMessage = buildString {
                appendLine("Error creating issue: ${e.message}")
                appendLine()
                appendLine("Please verify:")
                appendLine("1. JIRA credentials are correct")
                appendLine("2. Project key '$projectKey' exists and you have access")
                appendLine("3. Issue type '$issueType' is available in the project")
            }
            Result.failure(JiraApiException(errorMessage))
        }
    }

    /**
     * Parse issue key from string or URL
     */
    fun parseIssueKey(issueKeyOrUrl: String): String? {
        // Try direct issue key match
        if (JIRA_ISSUE_KEY_REGEX.matches(issueKeyOrUrl)) {
            return issueKeyOrUrl.uppercase()
        }

        // Try to extract from URL
        val match = JIRA_URL_REGEX.find(issueKeyOrUrl)
        return match?.groupValues?.get(2)?.uppercase()
    }

    /**
     * Validate issue key format
     */
    fun isValidIssueKey(issueKey: String): Boolean {
        return JIRA_ISSUE_KEY_REGEX.matches(issueKey)
    }

    /**
     * Build error message based on HTTP status
     */
    private fun buildErrorMessage(status: HttpStatusCode, issueKey: String, errorBody: String): String {
        return buildString {
            appendLine("Error: JIRA API request failed with status $status")
            appendLine()
            when (status) {
                HttpStatusCode.NotFound -> {
                    appendLine("Issue not found: $issueKey")
                    appendLine()
                    appendLine("Possible reasons:")
                    appendLine("- Issue key is incorrect")
                    appendLine("- Issue doesn't exist in this JIRA instance")
                    appendLine("- You don't have permission to view this issue")
                }
                HttpStatusCode.Unauthorized -> {
                    appendLine("Authentication failed.")
                    appendLine()
                    appendLine("Please check your JIRA credentials in .env:")
                    appendLine("- JIRA_EMAIL: Verify email is correct")
                    appendLine("- JIRA_API_TOKEN: Token may be invalid or expired")
                    appendLine()
                    appendLine("Get a new token from:")
                    appendLine("https://id.atlassian.com/manage-profile/security/api-tokens")
                }
                HttpStatusCode.Forbidden -> {
                    appendLine("Access denied to issue: $issueKey")
                    appendLine()
                    appendLine("You don't have permission to view this issue.")
                    appendLine("Ask your JIRA admin to grant you access.")
                }
                else -> {
                    appendLine("Response: $errorBody")
                }
            }
        }
    }
}

/**
 * Custom exception for JIRA API errors
 */
class JiraApiException(
    override val message: String,
    val statusCode: Int? = null
) : Exception(message)
