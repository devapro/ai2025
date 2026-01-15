package io.github.devapro.ai.tools.jira

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import io.ktor.client.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Tool for fetching tasks in the active sprint
 *
 * Returns unresolved issues currently in open sprints.
 * Useful for tracking sprint progress and identifying blockers.
 */
class JiraGetSprintTool(
    httpClient: HttpClient,
    private val jiraUrl: String?,
    jiraEmail: String?,
    jiraToken: String?,
    private val jiraProjectKey: String?
) : Tool {

    private val logger = LoggerFactory.getLogger(JiraGetSprintTool::class.java)

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
                name = "jira_get_sprint",
                description = """
                    Fetch tasks currently in the active sprint.

                    Returns unresolved issues that are in open sprints, sorted by priority.
                    Useful for:
                    - Sprint status reporting
                    - Identifying blocked or at-risk tasks
                    - Tracking sprint progress

                    Results include:
                    - Issue key, summary, status
                    - Issue type, priority
                    - Assignee information
                    - Created and updated dates
                    - Story points (if available)

                    Requires JIRA_PROJECT_KEY to be configured in .env
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("maxResults") {
                            put("type", "integer")
                            put("description", "Maximum number of results to return (default: 50, max: 100)")
                            put("default", 50)
                        }
                    }
                }
            )
        )
    }

    override suspend fun execute(arguments: JsonObject?): String {
        // Check if JIRA is configured
        if (apiClient == null) {
            return JiraFormatters.buildConfigError(jiraUrl, null, null)
        }

        // Check if project key is configured
        if (jiraProjectKey.isNullOrBlank()) {
            return JiraFormatters.buildProjectKeyError()
        }

        logger.info("Fetching active sprint for project: $jiraProjectKey")

        val maxResults = arguments?.get("maxResults")?.jsonPrimitive?.intOrNull?.coerceIn(1, 100) ?: 50

        // Build JQL query for active sprint
        val jql = "project = \"$jiraProjectKey\" AND sprint in openSprints() AND resolution = Unresolved ORDER BY priority DESC, updated DESC"

        val result = apiClient.searchIssues(jql, maxResults, "summary,status,issuetype,priority,assignee,created,updated,customfield_10016")

        return result.fold(
            onSuccess = { searchResult ->
                JiraFormatters.formatSearchResults(searchResult, "Active Sprint Tasks for $jiraProjectKey", jiraUrl!!)
            },
            onFailure = { error -> error.message ?: "Unknown error" }
        )
    }
}
