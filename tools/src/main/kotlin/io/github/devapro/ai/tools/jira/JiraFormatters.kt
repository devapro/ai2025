package io.github.devapro.ai.tools.jira

/**
 * Shared formatting utilities for JIRA tools
 */
object JiraFormatters {

    /**
     * Format issue data into readable markdown text
     */
    fun formatIssue(issue: JiraIssue, issueKey: String, jiraUrl: String): String {
        val fields = issue.fields
        return buildString {
            appendLine("# JIRA Issue Details")
            appendLine()
            appendLine("**Issue Key:** $issueKey")
            appendLine("**URL:** ${jiraUrl.removeSuffix("/")}/browse/$issueKey")
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
     * Format search results into readable markdown text
     */
    fun formatSearchResults(result: JiraSearchResult, title: String, jiraUrl: String): String {
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("**Found:** ${result.getTotalCount()} issues")
            if (result.total != null) {
                // Old API provided total count
                appendLine("**Returned in this page:** ${result.issues.size} issues")
            }
            appendLine()

            if (result.issues.isEmpty()) {
                appendLine("_No issues found_")
                return@buildString
            }

            appendLine("## Issues")
            appendLine()

            result.issues.forEach { issue ->
                val fields = issue.fields
                appendLine("### ${issue.key}: ${fields.summary}")
                appendLine()
                appendLine("- **Status:** ${fields.status.name}")
                appendLine("- **Type:** ${fields.issuetype.name}")
                if (fields.priority != null) {
                    appendLine("- **Priority:** ${fields.priority.name}")
                }
                if (fields.assignee != null) {
                    appendLine("- **Assignee:** ${fields.assignee.displayName}")
                } else {
                    appendLine("- **Assignee:** Unassigned")
                }
                fields.customfield_10016?.let { storyPoints ->
                    appendLine("- **Story Points:** $storyPoints")
                }
                appendLine("- **Created:** ${fields.created}")
                appendLine("- **Updated:** ${fields.updated}")
                appendLine("- **URL:** ${jiraUrl.removeSuffix("/")}/browse/${issue.key}")
                appendLine()
            }

            appendLine("---")
            appendLine()
            appendLine("_Fetched from JIRA API at ${java.time.Instant.now()}_")
        }
    }

    /**
     * Format created issue result into readable markdown text
     */
    fun formatCreatedIssue(
        createResult: JiraCreateResult,
        summary: String,
        description: String?,
        issueType: String,
        priority: String?,
        assignee: String?,
        labels: List<String>,
        jiraUrl: String
    ): String {
        val normalizedJiraUrl = jiraUrl.removeSuffix("/")
        return buildString {
            appendLine("# Issue Created Successfully")
            appendLine()
            appendLine("**Issue Key:** ${createResult.key}")
            appendLine("**URL:** $normalizedJiraUrl/browse/${createResult.key}")
            appendLine("**Summary:** $summary")
            if (!description.isNullOrBlank()) {
                appendLine("**Description:** ${description.take(100)}${if (description.length > 100) "..." else ""}")
            }
            appendLine("**Issue Type:** $issueType")
            if (!priority.isNullOrBlank()) {
                appendLine("**Priority:** $priority")
            }
            if (!assignee.isNullOrBlank()) {
                appendLine("**Assignee:** $assignee")
            }
            if (labels.isNotEmpty()) {
                appendLine("**Labels:** ${labels.joinToString(", ")}")
            }
        }
    }

    /**
     * Build configuration error message
     */
    fun buildConfigError(jiraUrl: String?, jiraEmail: String?, jiraToken: String?): String {
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

    /**
     * Build project key error message
     */
    fun buildProjectKeyError(): String {
        return buildString {
            appendLine("Error: JIRA_PROJECT_KEY is not configured")
            appendLine()
            appendLine("To use this operation, add to your .env file:")
            appendLine("JIRA_PROJECT_KEY=PROJ")
        }
    }
}
