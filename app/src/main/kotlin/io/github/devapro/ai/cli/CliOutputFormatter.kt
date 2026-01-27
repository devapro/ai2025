package io.github.devapro.ai.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.markdown.Markdown

/**
 * Formats and colors CLI output using Mordant terminal library
 *
 * Responsibilities:
 * - Color-coded message prefixes for different message types
 * - Markdown rendering in terminal
 * - Statistics formatting
 * - Visual separators
 */
class CliOutputFormatter {
    private val terminal = Terminal()

    /**
     * Format user input message with cyan prefix
     */
    fun formatUserMessage(message: String): String {
        return cyan("[You] ") + message
    }

    /**
     * Format assistant response with green prefix and markdown rendering
     */
    fun formatAssistantMessage(message: String): String {
        val prefix = green("[Assistant]")

        // Split message and statistics if present
        val parts = message.split("\n\n---\n\n")
        if (parts.size == 2) {
            // Message has statistics at the end
            val content = parts[0]
            val stats = parts[1]
            return buildString {
                appendLine(prefix)
                appendLine(renderMarkdown(content))
                appendLine()
                append(formatStatistics(stats))
            }
        }

        // No statistics, just render the content
        return buildString {
            appendLine(prefix)
            append(renderMarkdown(message))
        }
    }

    /**
     * Format system message with yellow prefix
     */
    fun formatSystemMessage(message: String): String {
        return yellow("[System] ") + message
    }

    /**
     * Format error message with red prefix
     */
    fun formatError(message: String): String {
        return red("[Error] ") + message
    }

    /**
     * Format statistics with dimmed/gray color
     */
    fun formatStatistics(stats: String): String {
        return TextStyles.dim(stats)
    }

    /**
     * Render markdown content to terminal format
     */
    fun renderMarkdown(content: String): String {
        return try {
            // Mordant's Markdown renders directly when printed
            // For now, return the content as-is and let terminal handle formatting
            // TODO: Consider using Mordant's markdown widget for richer formatting
            content
        } catch (e: Exception) {
            // Fallback to plain text if markdown rendering fails
            content
        }
    }

    /**
     * Print visual separator between messages
     */
    fun printSeparator() {
        terminal.println(gray("─".repeat(60)))
    }

    /**
     * Print message directly to terminal
     */
    fun print(text: String) {
        terminal.print(text)
    }

    /**
     * Print message with newline to terminal
     */
    fun println(text: String = "") {
        terminal.println(text)
    }
}
