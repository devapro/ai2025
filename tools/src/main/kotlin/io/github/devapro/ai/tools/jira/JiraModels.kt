package io.github.devapro.ai.tools.jira

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * JIRA API data models
 */

@Serializable
data class JiraIssue(
    val key: String,
    val fields: JiraFields
)

@Serializable
data class JiraFields(
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
data class JiraIssueType(
    val name: String,
    val id: String? = null
)

@Serializable
data class JiraPriority(
    val name: String,
    val id: String? = null
)

@Serializable
data class JiraStatus(
    val name: String,
    val id: String? = null
)

@Serializable
data class JiraResolution(
    val name: String,
    val id: String? = null
)

@Serializable
data class JiraUser(
    val displayName: String,
    val emailAddress: String? = null,
    val accountId: String? = null
)

@Serializable
data class JiraIssueReference(
    val key: String,
    val fields: JiraReferenceFields
)

@Serializable
data class JiraReferenceFields(
    val summary: String,
    val status: JiraStatus
)

@Serializable
data class JiraSearchResult(
    // Old API format (deprecated /rest/api/3/search) - included 'total'
    val total: Int? = null,
    // New API format (/rest/api/3/search/jql) - uses token-based pagination
    val nextPageToken: String? = null,
    val isLast: Boolean? = null,
    // Common field in both formats
    val issues: List<JiraIssue>
) {
    /**
     * Get the total count of issues
     * For new API: returns actual count of issues in this response
     * For old API: returns the total field value
     */
    fun getTotalCount(): Int = total ?: issues.size
}

@Serializable
data class JiraCreateResult(
    val id: String,
    val key: String,
    val self: String
)
