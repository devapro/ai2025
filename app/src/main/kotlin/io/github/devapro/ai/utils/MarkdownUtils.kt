package io.github.devapro.ai.utils

/**
 * Utility functions for handling Telegram markdown formatting
 */
object MarkdownUtils {
    /**
     * Escape special markdown characters that could break Telegram's markdown parser
     *
     * Telegram Markdown V1 special characters: _ * [ ] ( ) ~ ` > # + - = | { } . !
     * We selectively escape only the most problematic ones that commonly break formatting
     */
    fun escapeMarkdown(text: String): String {
        return text
            // Only escape asterisks that are likely not intentional markdown (single asterisks)
            // Leave double asterisks (**bold**) and single asterisks with spaces intact
            .replace(Regex("(?<!\\*)\\*(?!\\*)(?!\\s)"), "\\*")
            // Escape underscores only when they appear to be breaking markdown
            .replace(Regex("(?<!_)_(?!_)(?!\\s)"), "\\_")
            // Escape backticks that aren't part of code blocks
            .replace(Regex("(?<!`)\\`(?!`)(?!\\s)"), "\\`")
    }

    /**
     * Sanitize text by removing all markdown formatting
     * Use this as a fallback when markdown parsing fails
     */
    fun stripMarkdown(text: String): String {
        return text
            .replace("**", "")
            .replace("*", "")
            .replace("__", "")
            .replace("_", "")
            .replace("`", "")
            .replace("[", "")
            .replace("]", "")
            .replace("(", "")
            .replace(")", "")
    }

    /**
     * Truncate message if it exceeds Telegram's limit (4096 characters)
     */
    fun truncateIfNeeded(text: String, maxLength: Int = 4096): String {
        return if (text.length > maxLength) {
            val truncated = text.substring(0, maxLength - 50)
            "$truncated\n\n[Message truncated - too long]"
        } else {
            text
        }
    }
}
