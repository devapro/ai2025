package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Tool for fetching JIRA issue information via JIRA REST API
 *
 * Supports:
 * - Fetching issue details (summary, description, status, assignee, etc.)
 * - Parsing JIRA issue keys (PROJ-123 format)
 * - Works with both Cloud and Server/Data Center instances
 * - Handles authentication and error responses
 *
 * Authentication:
 * - Requires: JIRA base URL, email, and API token
 * - Cloud: Use API token from https://id.atlassian.com/manage-profile/security/api-tokens
 * - Server/DC: Use personal access token
 *
 * Examples:
 * - Get issue: {"operation": "get_issue", "issueKey": "PROJ-123"}
 * - Get issue: {"operation": "get_issue", "url": "https://company.atlassian.net/browse/PROJ-123"}
 */
class JiraTool(
    private val httpClient: HttpClient,
    private val jiraUrl: String?,
    private val jiraEmail: String?,
    private val jiraToken: String?
) : Tool {

    private val logger = LoggerFactory.getLogger(JiraTool::class.java)

    companion object {
        private val JIRA_ISSUE_KEY_REGEX = Regex("""([A-Z][A-Z0-9]+)-(\d+)""")
        private val JIRA_URL_REGEX = Regex("""https?://([^/]+)/browse/([A-Z][A-Z0-9]+-\d+)""")
    }

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "jira_api",
                description = """
                    Fetch JIRA issue information via JIRA REST API.

                    Operations:
                    - get_issue: Fetch comprehensive issue details

                    You can provide either:
                    - An issue key: PROJ-123, PROJECT-456, etc.
                    - A JIRA issue URL: https://company.atlassian.net/browse/PROJ-123

                    Returns comprehensive issue information including:
                    - Basic info: summary, description, issue type, priority
                    - Status: current status, resolution
                    - People: assignee, reporter, creator
                    - Dates: created, updated, due date
                    - Links: parent issue, subtasks, related issues
                    - Custom fields: story points, sprint, labels, etc.

                    Requires configuration in .env:
                    - JIRA_URL: Your JIRA instance URL
                    - JIRA_EMAIL: Your email address
                    - JIRA_API_TOKEN: API token from JIRA
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("operation") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("get_issue")
                            }
                            put("description", "The JIRA operation to perform")
                        }
                        putJsonObject("issueKey") {
                            put("type", "string")
                            put("description", "JIRA issue key (e.g., PROJ-123, PROJECT-456)")
                        }
                        putJsonObject("url") {
                            put("type", "string")
                            put("description", "JIRA issue URL (alternative to issueKey)")
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

        // Check if JIRA is configured
        if (jiraUrl.isNullOrBlank() || jiraEmail.isNullOrBlank() || jiraToken.isNullOrBlank()) {
            return buildString {
                appendLine("Error: JIRA is not configured")
                appendLine()
                appendLine("To use JIRA integration, add these to your .env file:")
                appendLine()
                appendLine("JIRA_URL=https://your-company.atlassian.net")
                appendLine("JIRA_EMAIL=your.email@company.com")
                appendLine("JIRA_API_TOKEN=your_api_token")
                appendLine()
                appendLine("Get an API token from:")
                appendLine("https://id.atlassian.com/manage-profile/security/api-tokens")
                appendLine()
                appendLine("Current configuration:")
                appendLine("- JIRA_URL: ${if (jiraUrl.isNullOrBlank()) "❌ Not set" else "✅ Set"}")
                appendLine("- JIRA_EMAIL: ${if (jiraEmail.isNullOrBlank()) "❌ Not set" else "✅ Set"}")
                appendLine("- JIRA_API_TOKEN: ${if (jiraToken.isNullOrBlank()) "❌ Not set" else "✅ Set"}")
            }
        }

        val operation = arguments["operation"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("'operation' argument is required")

        logger.info("Executing JIRA operation: $operation")

        return try {
            when (operation) {
                "get_issue" -> getIssue(arguments)
                else -> "Error: Unknown operation '$operation'"
            }
        } catch (e: Exception) {
            logger.error("JIRA operation failed: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Get JIRA issue details
     */
    private suspend fun getIssue(arguments: JsonObject): String {
        // Parse input (issueKey or URL)
        val issueKey = parseIssueKey(arguments)
            ?: return "Error: Please provide either 'issueKey' (e.g., PROJ-123) or 'url'"

        logger.info("Fetching JIRA issue: $issueKey")

        // Build API URL
        val normalizedJiraUrl = jiraUrl!!.removeSuffix("/")
        val apiUrl = "$normalizedJiraUrl/rest/api/3/issue/$issueKey"

        try {
            // Create Basic Auth credentials
            val credentials = "$jiraEmail:$jiraToken".encodeBase64()

            val response = httpClient.get(apiUrl) {
                header("Authorization", "Basic $credentials")
                header("Accept", "application/json")
            }

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                return buildString {
                    appendLine("Error: JIRA API request failed with status ${response.status}")
                    appendLine()
                    when (response.status) {
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

            val issueData = response.body<JiraIssue>()
            return formatIssue(issueData, issueKey)

        } catch (e: Exception) {
            logger.error("Failed to fetch JIRA issue: ${e.message}", e)
            return buildString {
                appendLine("Error fetching JIRA issue: ${e.message}")
                appendLine()
                appendLine("Troubleshooting:")
                appendLine("1. Verify JIRA_URL is correct: $jiraUrl")
                appendLine("2. Check if you can access: $normalizedJiraUrl/browse/$issueKey")
                appendLine("3. Verify your API token is still valid")
            }
        }
    }

    /**
     * Parse issue key from arguments
     */
    private fun parseIssueKey(arguments: JsonObject): String? {
        // Try direct issue key first
        val issueKey = arguments["issueKey"]?.jsonPrimitive?.content
        if (!issueKey.isNullOrBlank()) {
            // Validate format
            if (JIRA_ISSUE_KEY_REGEX.matches(issueKey)) {
                return issueKey.uppercase()
            } else {
                throw IllegalArgumentException("Invalid JIRA issue key format. Expected: PROJ-123, PROJECT-456, etc.")
            }
        }

        // Try to extract from URL
        val url = arguments["url"]?.jsonPrimitive?.content
        if (!url.isNullOrBlank()) {
            val match = JIRA_URL_REGEX.find(url)
            if (match != null) {
                return match.groupValues[2].uppercase()
            } else {
                throw IllegalArgumentException("Invalid JIRA URL format. Expected: https://company.atlassian.net/browse/PROJ-123")
            }
        }

        return null
    }

    /**
     * Format issue data into readable text
     */
    private fun formatIssue(issue: JiraIssue, issueKey: String): String {
        val fields = issue.fields
        return buildString {
            appendLine("# JIRA Issue Details")
            appendLine()
            appendLine("**Issue Key:** $issueKey")
            appendLine("**URL:** ${jiraUrl?.removeSuffix("/")}/browse/$issueKey")
            appendLine()

            appendLine("## Basic Information")
            appendLine()
            appendLine("**Summary:** ${fields.summary}")
            appendLine("**Issue Type:** ${fields.issuetype.name}")
            appendLine("**Priority:** ${fields.priority?.name ?: "None"}")
            appendLine("**Status:** ${fields.status.name}")
            if (fields.resolution != null) {
                appendLine("**Resolution:** ${fields.resolution.name}")
            }
            appendLine()

            appendLine("## Description")
            appendLine()
            val descriptionText = fields.getDescriptionText()
            if (!descriptionText.isNullOrBlank()) {
                appendLine(descriptionText)
            } else {
                appendLine("_No description provided_")
            }
            appendLine()

            appendLine("## People")
            appendLine()
            appendLine("**Reporter:** ${fields.reporter?.displayName ?: "Unknown"}")
            appendLine("**Assignee:** ${fields.assignee?.displayName ?: "Unassigned"}")
            if (fields.creator != null) {
                appendLine("**Creator:** ${fields.creator.displayName}")
            }
            appendLine()

            appendLine("## Dates")
            appendLine()
            appendLine("**Created:** ${fields.created}")
            appendLine("**Updated:** ${fields.updated}")
            if (fields.duedate != null) {
                appendLine("**Due Date:** ${fields.duedate}")
            }
            if (fields.resolutiondate != null) {
                appendLine("**Resolved:** ${fields.resolutiondate}")
            }
            appendLine()

            if (fields.labels.isNotEmpty()) {
                appendLine("## Labels")
                appendLine()
                fields.labels.forEach { label ->
                    appendLine("- $label")
                }
                appendLine()
            }

            if (fields.parent != null) {
                appendLine("## Hierarchy")
                appendLine()
                appendLine("**Parent Issue:** ${fields.parent.key} - ${fields.parent.fields.summary}")
                appendLine()
            }

            if (fields.subtasks.isNotEmpty()) {
                appendLine("## Subtasks")
                appendLine()
                fields.subtasks.forEach { subtask ->
                    appendLine("- ${subtask.key}: ${subtask.fields.summary} (${subtask.fields.status.name})")
                }
                appendLine()
            }

            // Story points (custom field, may not exist)
            fields.customfield_10016?.let { storyPoints ->
                appendLine("## Estimation")
                appendLine()
                appendLine("**Story Points:** $storyPoints")
                appendLine()
            }

            appendLine("---")
            appendLine()
            appendLine("_Fetched from JIRA API at ${java.time.Instant.now()}_")
        }
    }

    /**
     * JIRA API response models
     */
    @Serializable
    private data class JiraIssue(
        val key: String,
        val fields: JiraFields
    )

    @Serializable
    private data class JiraFields(
        val summary: String,
        // Description can be either string (old format) or Atlassian Document Format object (new format)
        val description: JsonElement? = null,
        val issuetype: JiraIssueType,
        val priority: JiraPriority? = null,
        val status: JiraStatus,
        val resolution: JiraResolution? = null,
        val assignee: JiraUser? = null,
        val reporter: JiraUser? = null,
        val creator: JiraUser? = null,
        val created: String,
        val updated: String,
        val duedate: String? = null,
        val resolutiondate: String? = null,
        val labels: List<String> = emptyList(),
        val parent: JiraIssueReference? = null,
        val subtasks: List<JiraIssueReference> = emptyList(),
        // Story points (Jira Cloud default field ID)
        @SerialName("customfield_10016") val customfield_10016: Double? = null
    ) {
        /**
         * Extract description text from either string or Atlassian Document Format
         */
        fun getDescriptionText(): String? {
            return when (description) {
                is JsonPrimitive -> description.contentOrNull
                is JsonObject -> extractTextFromADF(description)
                else -> null
            }
        }

        /**
         * Extract plain text from Atlassian Document Format (ADF)
         */
        private fun extractTextFromADF(adf: JsonObject): String {
            val content = adf["content"]?.jsonArray ?: return ""
            return buildString {
                content.forEach { node ->
                    if (node is JsonObject) {
                        extractTextFromNode(node, this)
                    }
                }
            }.trim()
        }

        /**
         * Recursively extract text from ADF nodes
         */
        private fun extractTextFromNode(node: JsonObject, output: StringBuilder) {
            val type = node["type"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "text" -> {
                    // Plain text node
                    node["text"]?.jsonPrimitive?.contentOrNull?.let { output.append(it) }
                }
                "paragraph" -> {
                    // Paragraph node - process children and add newline
                    node["content"]?.jsonArray?.forEach { child ->
                        if (child is JsonObject) {
                            extractTextFromNode(child, output)
                        }
                    }
                    output.appendLine()
                }
                "heading" -> {
                    // Heading node - add markdown heading
                    val level = node["attrs"]?.jsonObject?.get("level")?.jsonPrimitive?.intOrNull ?: 1
                    output.append("#".repeat(level)).append(" ")
                    node["content"]?.jsonArray?.forEach { child ->
                        if (child is JsonObject) {
                            extractTextFromNode(child, output)
                        }
                    }
                    output.appendLine()
                }
                "bulletList", "orderedList" -> {
                    // List node - process list items
                    node["content"]?.jsonArray?.forEach { child ->
                        if (child is JsonObject) {
                            extractTextFromNode(child, output)
                        }
                    }
                }
                "listItem" -> {
                    // List item node
                    output.append("- ")
                    node["content"]?.jsonArray?.forEach { child ->
                        if (child is JsonObject) {
                            extractTextFromNode(child, output)
                        }
                    }
                    output.appendLine()
                }
                "codeBlock" -> {
                    // Code block
                    output.appendLine("```")
                    node["content"]?.jsonArray?.forEach { child ->
                        if (child is JsonObject) {
                            extractTextFromNode(child, output)
                        }
                    }
                    output.appendLine("```")
                }
                "hardBreak" -> {
                    output.appendLine()
                }
                else -> {
                    // For other node types, recursively process children
                    node["content"]?.jsonArray?.forEach { child ->
                        if (child is JsonObject) {
                            extractTextFromNode(child, output)
                        }
                    }
                }
            }
        }
    }

    @Serializable
    private data class JiraIssueType(
        val name: String,
        val id: String? = null
    )

    @Serializable
    private data class JiraPriority(
        val name: String,
        val id: String? = null
    )

    @Serializable
    private data class JiraStatus(
        val name: String,
        val id: String? = null
    )

    @Serializable
    private data class JiraResolution(
        val name: String,
        val id: String? = null
    )

    @Serializable
    private data class JiraUser(
        val displayName: String,
        val emailAddress: String? = null,
        val accountId: String? = null
    )

    @Serializable
    private data class JiraIssueReference(
        val key: String,
        val fields: JiraReferenceFields
    )

    @Serializable
    private data class JiraReferenceFields(
        val summary: String,
        val status: JiraStatus
    )
}
