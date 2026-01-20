package io.github.devapro.ai.tools.jira

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import io.ktor.client.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Tool for creating new JIRA issues
 *
 * Creates tasks, stories, bugs, or other issue types in the configured project.
 * Supports setting summary, description, priority, assignee, and labels.
 */
class JiraCreateIssueTool(
    httpClient: HttpClient,
    private val jiraUrl: String?,
    jiraEmail: String?,
    jiraToken: String?,
    private val jiraProjectKey: String?
) : Tool {

    private val logger = LoggerFactory.getLogger(JiraCreateIssueTool::class.java)

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
                name = "jira_create_issue",
                description = """
                    Create a new JIRA task in the configured project.

                    Use this to create tasks, stories, bugs, or other issue types.
                    After analyzing requirements and code, create well-defined tasks with:
                    - Clear, actionable summary
                    - Detailed description with technical context
                    - Appropriate issue type (Task, Story, Bug, Epic)
                    - Priority based on impact
                    - Labels for categorization

                    Returns the created issue key and URL.

                    Requires JIRA_PROJECT_KEY to be configured in .env
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("summary") {
                            put("type", "string")
                            put("description", "Issue title - clear and concise (required)")
                        }
                        putJsonObject("description") {
                            put("type", "string")
                            put("description", "Detailed issue description with context, technical details, and acceptance criteria")
                        }
                        putJsonObject("issueType") {
                            put("type", "string")
                            put("description", "Issue type: Task, Story, Bug, Epic, etc. (default: Task)")
                            put("default", "Task")
                        }
                        putJsonObject("priority") {
                            put("type", "string")
                            put("description", "Priority: Highest, High, Medium, Low, Lowest")
                        }
                        putJsonObject("assignee") {
                            put("type", "string")
                            put("description", "Assignee account ID or email address")
                        }
                        putJsonObject("labels") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "string")
                            }
                            put("description", "Array of labels for categorization (e.g., [\"backend\", \"security\", \"performance\"])")
                        }
                    }
                    putJsonArray("required") {
                        add("summary")
                    }
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

        // Check if project key is configured
        if (jiraProjectKey.isNullOrBlank()) {
            return JiraFormatters.buildProjectKeyError()
        }

        val summary = arguments["summary"]?.jsonPrimitive?.content
            ?: return "Error: 'summary' is required for creating an issue"

        val description = arguments["description"]?.jsonPrimitive?.content
        val issueType = arguments["issueType"]?.jsonPrimitive?.content ?: "Task"
        val priority = arguments["priority"]?.jsonPrimitive?.content
        val assignee = arguments["assignee"]?.jsonPrimitive?.content
        val labels = arguments["labels"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

        logger.info("Creating issue in project $jiraProjectKey: $summary")

        val result = apiClient.createIssue(
            projectKey = jiraProjectKey,
            summary = summary,
            description = description,
            issueType = issueType,
            priority = priority,
            assignee = assignee,
            labels = labels
        )

        return result.fold(
            onSuccess = { createResult ->
                JiraFormatters.formatCreatedIssue(
                    createResult = createResult,
                    summary = summary,
                    description = description,
                    issueType = issueType,
                    priority = priority,
                    assignee = assignee,
                    labels = labels,
                    jiraUrl = jiraUrl!!
                )
            },
            onFailure = { error -> error.message ?: "Unknown error" }
        )
    }
}
