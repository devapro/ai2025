package io.github.devapro.ai.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.devapro.ai.agent.AiAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Telegram Bot component that handles user interactions
 */
class TelegramBot(
    private val botToken: String,
    private val aiAgent: AiAgent,
    private val profileRepository: io.github.devapro.ai.repository.UserProfileRepository,
    private val profileInterviewer: io.github.devapro.ai.agent.ProfileInterviewer
) {
    private val logger = LoggerFactory.getLogger(TelegramBot::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val MAX_MESSAGE_LENGTH = 4096

        /**
         * Escape special Markdown characters to prevent parsing errors
         * Telegram Markdown special characters: _ * ` [
         */
        private fun escapeMarkdown(text: String): String {
            return text
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`")
                .replace("[", "\\[")
        }
    }

    val bot = bot {
        token = botToken

        dispatch {
            // Handle /start command
            command("start") {
                val chatId = ChatId.fromId(message.chat.id)
                bot.sendMessage(
                    chatId = chatId,
                    text = """
                        🤖 *Welcome to your AI Project Assistant!*

                        I'm your intelligent assistant for project management, code review, and task automation.

                        *What I can do:*
                        • 📅 Manage your schedule and calendar
                        • 🔍 Review Pull Requests with detailed analysis
                        • 📝 Search and explore your codebase
                        • 📊 Track JIRA tasks and requirements
                        • 🔧 Perform git operations and code analysis
                        • 💡 Answer questions about your project

                        *Key Commands:*
                        • /review-pr <url> - Review a GitHub Pull Request
                        • /summary - Get today's schedule
                        • /profile - View or setup your profile
                        • /clear - Clear conversation history
                        • /help - Show all available commands

                        *Pro Tips:*
                        • Set up your profile for personalized responses
                        • Ask me anything about your codebase
                        • I can analyze code quality and security
                        • Configure GitHub/JIRA in mcp-config.json for enhanced features

                        Just tell me what you need - I'm here to help! 🚀
                    """.trimIndent(),
                    parseMode = ParseMode.MARKDOWN
                )
            }

            // Handle /help command
            command("help") {
                val chatId = ChatId.fromId(message.chat.id)
                bot.sendMessage(
                    chatId = chatId,
                    text = """
                        *Available commands:*

                        /start - Show welcome message
                        /summary - Get today's schedule and events
                        /review-pr - Review a GitHub Pull Request
                        /profile - View or setup your profile
                        /clear - Clear conversation history
                        /help - Show this help message

                        *What I can do:*
                        • 📝 Create events: "Schedule a meeting tomorrow at 2pm"
                        • 📋 List schedule: "What's on my calendar today?"
                        • 🔍 Search: "Find my meetings with John"
                        • ✏️ Update: "Move my dentist appointment to Friday"
                        • 🗑️ Delete: "Cancel the team lunch"
                        • ⏰ Availability: "When am I free this week?"
                        • 🔍 Review PRs: /review-pr https://github.com/owner/repo/pull/123

                        *Tips:*
                        • Use natural language for dates and times
                        • I'll ask for details if needed
                        • Use /summary for a quick daily overview
                        • Configure GitHub/JIRA in mcp-config.json for PR reviews

                        Just tell me what you need!
                    """.trimIndent(),
                    parseMode = ParseMode.MARKDOWN
                )
            }

            // Handle /clear command
            command("clear") {
                val chatId = message.chat.id
                aiAgent.clearHistory(chatId)
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "✅ *Conversation history cleared!*",
                    parseMode = ParseMode.MARKDOWN
                )
            }

            // Handle /profile command
            command("profile") {
                val chatId = message.chat.id
                val args = message.text?.substringAfter("/profile")?.trim() ?: ""

                logger.info("Profile command received from user $chatId, args: '$args'")

                val profile = profileRepository.getUserProfile(chatId)

                when {
                    args == "update" || profile == null || !profile.isComplete -> {
                        // Start or restart profile setup
                        val welcomeMessage = if (args == "update") {
                            "Let's update your profile! Starting fresh interview..."
                        } else {
                            """
                                👋 Welcome! Let me learn a bit about you to personalize your experience.

                                I'll ask you a few quick questions. Ready to start?
                            """.trimIndent()
                        }

                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = welcomeMessage,
                            parseMode = ParseMode.MARKDOWN
                        )

                        // Clear setup state if updating
                        if (args == "update") {
                            profileRepository.clearSetupState(chatId)
                        }

                        // Start interview in coroutine
                        coroutineScope.launch {
                            try {
                                val response = profileInterviewer.conductInterview(chatId, "I'm ready!")
                                sendMessageSafely(chatId, response)
                            } catch (e: Exception) {
                                logger.error("Error starting profile interview: ${e.message}", e)
                                sendMessageSafely(chatId, "Sorry, I encountered an error. Please try /profile again.")
                            }
                        }
                    }
                    else -> {
                        // Show existing profile
                        val profileText = buildString {
                            appendLine("📋 *Your Profile*")
                            appendLine()
                            profile.name?.let { appendLine("**Name:** $it") }
                            profile.role?.let { appendLine("**Role:** $it") }
                            profile.timezone?.let { appendLine("**Timezone:** $it") }
                            profile.language?.let { appendLine("**Language:** $it") }
                            profile.formality?.let { appendLine("**Style:** $it") }
                            profile.verbosity?.let { appendLine("**Verbosity:** $it") }
                            profile.useEmoji?.let { appendLine("**Emoji:** ${if (it) "Yes" else "No"}") }
                            appendLine()
                            appendLine("To update your profile, use `/profile update`")
                        }

                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = profileText,
                            parseMode = ParseMode.MARKDOWN
                        )
                    }
                }
            }

            // Handle /summary command
            command("summary") {
                val chatId = message.chat.id
                logger.info("Summary command received from user $chatId")

                // Send typing indicator
                bot.sendChatAction(ChatId.fromId(chatId), com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                // Process summary request with AI agent in coroutine
                coroutineScope.launch {
                    try {
                        val response = aiAgent.processMessage(
                            chatId,
                            "Please provide a summary of all events and tasks scheduled for today. Include time, title, location, and any important details."
                        )
                        logger.info("Sending summary response to user $chatId")
                        logger.info("Response preview: ${response.take(200)}...")

                        // Send with safe handling
                        sendMessageSafely(chatId, response)
                        logger.info("sendMessageSafely completed for summary")
                    } catch (e: Exception) {
                        logger.error("Error in summary command coroutine: ${e.message}", e)
                        try {
                            sendMessageSafely(chatId, "Sorry, I encountered an error getting your schedule summary. Please try again.")
                        } catch (e2: Exception) {
                            logger.error("Failed to send error message: ${e2.message}", e2)
                        }
                    }
                }
            }

            // Handle /review-pr command
            command("review-pr") {
                val chatId = message.chat.id
                val prUrl = message.text?.substringAfter("/review-pr ")?.trim()

                // Validate URL
                if (prUrl.isNullOrBlank() || !prUrl.startsWith("http")) {
                    bot.sendMessage(
                        chatId = ChatId.fromId(chatId),
                        text = """
                            *Usage:* /review-pr <github-pr-url>

                            *Example:*
                            /review-pr https://github.com/owner/repo/pull/123

                            *Note:* Make sure GitHub and JIRA MCP servers are configured in mcp-config.json if you want to fetch PR details and task information.
                        """.trimIndent(),
                        parseMode = ParseMode.MARKDOWN
                    )
                    return@command
                }

                logger.info("PR review command received from user $chatId: $prUrl")

                // Send initial status
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "🔍 *Starting PR review for:* $prUrl\n\nThis may take a minute...",
                    parseMode = ParseMode.MARKDOWN
                )

                // Send typing indicator
                bot.sendChatAction(ChatId.fromId(chatId), com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                // Process in coroutine
                coroutineScope.launch {
                    try {
                        // Build specialized prompt for PR review
                        val prReviewPrompt = buildPrReviewPrompt(prUrl)

                        // Use existing AI agent (reuse, don't create new)
                        val response = aiAgent.processMessage(chatId, prReviewPrompt)

                        sendMessageSafely(chatId, response)
                    } catch (e: Exception) {
                        logger.error("Error in PR review: ${e.message}", e)
                        sendMessageSafely(chatId, "Sorry, I encountered an error during PR review: ${e.message}")
                    }
                }
            }

            // Handle regular text messages
            text {
                val chatId = message.chat.id
                val userMessage = text

                // Ignore commands (they are handled by command handlers)
                if (userMessage.startsWith("/")) {
                    return@text
                }

                logger.info("Received message from user $chatId: $userMessage")

                // Check if user is in profile interview
                val setupState = profileRepository.getSetupState(chatId)
                if (setupState != null && setupState.isInSetup) {
                    // Continue interview
                    logger.info("User $chatId is in profile setup, continuing interview")
                    bot.sendChatAction(ChatId.fromId(chatId), com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                    coroutineScope.launch {
                        try {
                            val response = profileInterviewer.conductInterview(chatId, userMessage)
                            sendMessageSafely(chatId, response)
                        } catch (e: Exception) {
                            logger.error("Error in profile interview: ${e.message}", e)
                            sendMessageSafely(chatId, "Sorry, I encountered an error. Let's continue - please answer again.")
                        }
                    }
                    return@text
                }

                // Check if this is first message (no profile)
                if (!profileRepository.profileExists(chatId)) {
                    logger.info("First message from new user $chatId, starting auto-interview")

                    // Start automatic interview
                    bot.sendMessage(
                        chatId = ChatId.fromId(chatId),
                        text = """
                            👋 Hi! I'm your AI Project Assistant.

                            Before we begin, let me quickly learn about you to personalize our conversations.
                            This will only take a minute!
                        """.trimIndent(),
                        parseMode = ParseMode.MARKDOWN
                    )

                    // Send typing indicator
                    bot.sendChatAction(ChatId.fromId(chatId), com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                    // Start interview
                    coroutineScope.launch {
                        try {
                            val response = profileInterviewer.conductInterview(chatId, userMessage)
                            sendMessageSafely(chatId, response)
                        } catch (e: Exception) {
                            logger.error("Error in auto-interview: ${e.message}", e)
                            // Fall back to normal processing
                            sendMessageSafely(chatId, "Sorry, I couldn't start the profile setup. Let me answer your question anyway...")
                            try {
                                val response = aiAgent.processMessage(chatId, userMessage)
                                sendMessageSafely(chatId, response)
                            } catch (e2: Exception) {
                                logger.error("Error in fallback processing: ${e2.message}", e2)
                            }
                        }
                    }
                    return@text
                }

                // Normal message processing
                bot.sendChatAction(ChatId.fromId(chatId), com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                coroutineScope.launch {
                    try {
                        val response = aiAgent.processMessage(chatId, userMessage)
                        logger.info("Sending response to user $chatId")
                        logger.info("Response preview: ${response.take(200)}...")

                        // Send with safe handling
                        sendMessageSafely(chatId, response)
                        logger.info("sendMessageSafely completed")
                    } catch (e: Exception) {
                        logger.error("Error in message handler coroutine: ${e.message}", e)
                        try {
                            sendMessageSafely(chatId, "Sorry, I encountered an error processing your message. Please try again.")
                        } catch (e2: Exception) {
                            logger.error("Failed to send error message: ${e2.message}", e2)
                        }
                    }
                }
            }
        }
    }

    /**
     * Build specialized prompt for PR review workflow
     */
    private fun buildPrReviewPrompt(prUrl: String): String {
        return """
Please review the Pull Request at: $prUrl

Follow these steps to conduct a comprehensive PR review:

1. **Parse PR URL** - Extract owner, repository name, and PR number from the URL

2. **Fetch PR Details**
   - Use github_api tool to fetch PR details (title, description, branch name, author)
   - The tool works immediately without MCP configuration
   - Example: github_api(operation="get_pr", url="https://github.com/owner/repo/pull/123")

3. **Extract JIRA Link** (if present in PR description)
   - Look for JIRA task ID in format: PROJ-123, PROJECT-456, etc.
   - Extract task link if found

4. **Fetch JIRA Task** (if link found)
   - Use jira_api tool to fetch task description and requirements
   - Example: jira_api(operation="get_issue", issueKey="PROJ-123")
   - If JIRA not configured, skip JIRA analysis

5. **Read Project Rules**
   - Use read_file tool to read CLAUDE.md: read_file(path="CLAUDE.md", mode="document")
   - Use read_file tool to read promts/rules.md: read_file(path="promts/rules.md", mode="system")
   - If files don't exist, proceed with general code review

6. **Get Code Diff**
   - Use git_operation tool with operation="get_diff"
   - Provide prBranch (from GitHub or extracted from URL)
   - Provide baseBranch (default: "develop")
   - This will automatically handle branch switching and cleanup

7. **Analyze Code Changes**
   - Review diff against CLAUDE.md conventions
   - Check compliance with rules.md guidelines
   - Verify JIRA requirements are met (if task was fetched)
   - Look for:
     * Code quality issues
     * Security vulnerabilities
     * Missing tests
     * Documentation gaps
     * Convention violations

8. **Generate Review Report**

Please format your report as follows:

# PR Review Report

**Title:** [PR title]
**JIRA:** [JIRA link] (if found)

## Summary
[Brief overview of what this PR changes - 2-3 sentences]

## Requirements Compliance

### JIRA Task Requirements (if available)
✅ Requirement 1 met
❌ Requirement 2 missing
⚠️ Requirement 3 partially implemented

### Project Conventions (CLAUDE.md)
✅ Follows Kotlin coding standards
✅ Uses dependency injection
❌ Missing: [specific issue]

### Code Quality Rules (rules.md)
✅ No hardcoded secrets
⚠️ Warning: [issue]
❌ Error: [issue]

## Issues Found

### Critical Issues (Must Fix Before Merge)
1. **[Issue Title]**
   - File: `path/to/file.kt`
   - Line: 123
   - Code: `problematic code snippet`
   - Issue: [Detailed description of the problem]
   - Recommendation: [How to fix it]

### Major Issues (Should Fix)
[Same format as critical]

### Minor Issues (Nice to Have)
[Same format as critical]

## Security Analysis
[Security concerns, or "✅ No security issues found"]

## Testing Coverage
[Analysis of test coverage, or "❌ No tests found for new code"]

## Documentation
[Documentation completeness check]

## Conclusion
**Status:** ✅ APPROVE / ⚠️ REQUEST CHANGES / ❌ REJECT

[Overall assessment and key points]

---

**Important:**
- Be thorough but constructive
- Provide specific file paths and line numbers for all issues
- Use existing project tools (read_file, search_code, explore_files) if you need more context
- If you encounter errors with any tool, gracefully skip that step and continue with the review
        """.trimIndent()
    }

    /**
     * Safely send a message to Telegram, handling long messages and Markdown escaping
     * Tries to send with escaped Markdown first, falls back to plain text on error
     */
    private fun sendMessageSafely(chatId: Long, text: String) {
        logger.info("sendMessageSafely called for user $chatId, message length: ${text.length}")

        val chatIdObj = ChatId.fromId(chatId)

        // Split long messages into chunks
        if (text.length <= MAX_MESSAGE_LENGTH) {
            // Try with escaped Markdown first
            val escapedText = try {
                escapeMarkdown(text)
            } catch (e: Exception) {
                logger.warn("Failed to escape Markdown: ${e.message}, will use plain text")
                text
            }

            logger.info("Sending single message with Markdown (escaped)")
            try {
                bot.sendMessage(
                    chatId = chatIdObj,
                    text = escapedText,
                    parseMode = ParseMode.MARKDOWN
                )
                logger.info("Message sent successfully with Markdown")
            } catch (e: Exception) {
                logger.warn("Failed with Markdown: ${e.message}, retrying as plain text")
                try {
                    bot.sendMessage(
                        chatId = chatIdObj,
                        text = text
                    )
                    logger.info("Message sent successfully as plain text")
                } catch (e2: Exception) {
                    logger.error("Failed to send plain text message: ${e2.message}", e2)
                }
            }
        } else {
            // Split into chunks
            logger.info("Message too long (${text.length} chars), splitting into chunks")
            val chunks = splitMessage(text)
            logger.info("Split into ${chunks.size} chunks")

            chunks.forEachIndexed { index, chunk ->
                logger.info("Sending chunk ${index + 1}/${chunks.size}, length: ${chunk.length}")

                // Try with escaped Markdown first
                val escapedChunk = try {
                    escapeMarkdown(chunk)
                } catch (e: Exception) {
                    logger.warn("Failed to escape chunk $index: ${e.message}")
                    chunk
                }

                try {
                    bot.sendMessage(
                        chatId = chatIdObj,
                        text = escapedChunk,
                        parseMode = ParseMode.MARKDOWN
                    )
                    logger.info("Chunk $index sent successfully with Markdown")
                } catch (e: Exception) {
                    logger.warn("Chunk $index failed with Markdown: ${e.message}, trying plain text")
                    try {
                        bot.sendMessage(
                            chatId = chatIdObj,
                            text = chunk
                        )
                        logger.info("Chunk $index sent successfully as plain text")
                    } catch (e2: Exception) {
                        logger.error("Failed to send chunk $index as plain text: ${e2.message}", e2)
                    }
                }

                // Small delay between chunks to avoid rate limiting
                if (index < chunks.size - 1) {
                    Thread.sleep(100)
                }
            }
            logger.info("All chunks sent")
        }
    }

    /**
     * Split a long message into chunks that fit within Telegram's limit
     */
    private fun splitMessage(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var remainingText = text

        while (remainingText.length > MAX_MESSAGE_LENGTH) {
            // Try to split at a newline near the limit
            var splitIndex = MAX_MESSAGE_LENGTH
            val lastNewline = remainingText.substring(0, MAX_MESSAGE_LENGTH).lastIndexOf('\n')

            if (lastNewline > MAX_MESSAGE_LENGTH / 2) {
                // Found a good newline to split at
                splitIndex = lastNewline + 1
            }

            chunks.add(remainingText.substring(0, splitIndex))
            remainingText = remainingText.substring(splitIndex)
        }

        if (remainingText.isNotEmpty()) {
            chunks.add(remainingText)
        }

        return chunks
    }

    /**
     * Start the bot
     */
    fun start() {
        logger.info("Starting Telegram bot...")
        bot.startPolling()
        logger.info("Telegram bot started successfully!")
    }

    /**
     * Stop the bot
     */
    fun stop() {
        logger.info("Stopping Telegram bot...")
        bot.stopPolling()
        logger.info("Telegram bot stopped")
    }
}
