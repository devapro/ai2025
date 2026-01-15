package io.github.devapro.ai.tools.jira

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import io.ktor.client.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Tool for fetching JIRA backlog tasks
 *
 * Returns unresolved issues that are:
 * - In configured backlog statuses (e.g., "Backlog", "To Do", "Open")
 * - OR not assigned to any sprint
 * - AND not yet resolved
 */
class JiraGetBacklogTool(
    httpClient: HttpClient,
    private val jiraUrl: String?,
    jiraEmail: String?,
    jiraToken: String?,
    private val jiraProjectKey: String?,
    private val jiraBacklogStatuses: String
) : Tool {

    private val logger = LoggerFactory.getLogger(JiraGetBacklogTool::class.java)

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
                name = "jira_get_backlog",
                description = """
                    Fetch list of tasks from the project backlog.

                    Returns unresolved issues that are not in any sprint or have backlog status.
                    Useful for sprint planning and reviewing upcoming work.

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

        logger.info("Fetching backlog for project: $jiraProjectKey")

        val maxResults = arguments?.get("maxResults")?.jsonPrimitive?.intOrNull?.coerceIn(1, 100) ?: 50

        // Parse backlog statuses from configuration
        val statuses = jiraBacklogStatuses.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val statusConditions = statuses.joinToString(" OR ") { status -> "status = \"$status\"" }

        // Build JQL query
        val jql = "project = \"$jiraProjectKey\" AND ($statusConditions OR sprint is EMPTY) AND resolution = Unresolved ORDER BY created DESC"

        val result = apiClient.searchIssues(jql, maxResults, "summary,status,issuetype,priority,assignee,created,updated")

        return result.fold(
            onSuccess = { searchResult ->
                JiraFormatters.formatSearchResults(searchResult, "Backlog Tasks for $jiraProjectKey", jiraUrl!!)
            },
            onFailure = { error -> error.message ?: "Unknown error" }
        )
    }
}
