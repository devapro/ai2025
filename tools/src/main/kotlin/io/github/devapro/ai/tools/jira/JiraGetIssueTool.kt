package io.github.devapro.ai.tools.jira

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import io.ktor.client.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Tool for fetching a single JIRA issue by key or URL
 *
 * Provides detailed information about a specific issue including:
 * - Summary, description, status, priority
 * - Assignee, reporter, creator
 * - Dates, labels, hierarchy
 * - Story points and custom fields
 */
class JiraGetIssueTool(
    httpClient: HttpClient,
    private val jiraUrl: String?,
    jiraEmail: String?,
    jiraToken: String?
) : Tool {

    private val logger = LoggerFactory.getLogger(JiraGetIssueTool::class.java)

    // API client (null if not configured)
    private val apiClient: JiraApiClient? = if (
        !jiraUrl.isNullOrBlank() &&
        !jiraEmail.isNullOrBlank() &&
        !jiraToken.isNullOrBlank()
    ) {
        JiraApiClient(httpClient, jiraUrl, jiraEmail, jiraToken)
    } else {
        null
    }

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "jira_get_issue",
                description = """
                    Fetch detailed information about a specific JIRA issue.

                    Provide either an issue key (PROJ-123) or a full JIRA URL.

                    Returns comprehensive issue details:
                    - Summary, description, issue type, priority, status
                    - Assignee, reporter, creator information
                    - Dates: created, updated, due date, resolved
                    - Parent issue and subtasks
                    - Labels, story points, custom fields

                    Examples:
                    - Issue key: "PROJ-123"
                    - URL: "https://company.atlassian.net/browse/PROJ-123"
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("issueKey") {
                            put("type", "string")
                            put("description", "JIRA issue key (e.g., PROJ-123, PROJECT-456)")
                        }
                        putJsonObject("url") {
                            put("type", "string")
                            put("description", "JIRA issue URL (alternative to issueKey)")
                        }
                    }
                    // Either issueKey or url is required (checked in execute)
                }
            )
        )
    }

    override suspend fun execute(arguments: JsonObject?): String {
        if (arguments == null) {
            throw IllegalArgumentException("Arguments are required")
        }

        // Check if JIRA is configured
        if (apiClient == null) {
            return JiraFormatters.buildConfigError(jiraUrl, null, null)
        }

        // Parse input (issueKey or URL)
        val issueKeyOrUrl = arguments["issueKey"]?.jsonPrimitive?.content
            ?: arguments["url"]?.jsonPrimitive?.content
            ?: return "Error: Please provide either 'issueKey' (e.g., PROJ-123) or 'url'"

        val issueKey = apiClient.parseIssueKey(issueKeyOrUrl)
            ?: return "Error: Invalid JIRA issue key or URL format. Expected: PROJ-123 or https://company.atlassian.net/browse/PROJ-123"

        logger.info("Fetching JIRA issue: $issueKey")

        val result = apiClient.getIssue(issueKey)

        return result.fold(
            onSuccess = { issue -> JiraFormatters.formatIssue(issue, issueKey, jiraUrl!!) },
            onFailure = { error -> error.message ?: "Unknown error" }
        )
    }
}
