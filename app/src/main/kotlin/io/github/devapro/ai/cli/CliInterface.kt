package io.github.devapro.ai.cli

import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.cli.audio.VoiceInputHandler
import io.github.devapro.ai.repository.FileRepository
import io.github.devapro.ai.repository.UserProfileRepository
import kotlinx.coroutines.runBlocking
import org.jline.reader.LineReaderBuilder
import org.jline.reader.EndOfFileException
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import org.slf4j.LoggerFactory

/**
 * Main CLI interface for AI Assistant
 *
 * Responsibilities:
 * - REPL loop for continuous conversation
 * - Command parsing and routing
 * - Integration with AiAgent
 * - Profile setup check
 * - Voice input support (optional)
 * - Error handling
 */
class CliInterface(
    private val aiAgent: AiAgent,
    private val outputFormatter: CliOutputFormatter,
    private val profileSetup: CliProfileSetup,
    private val profileRepository: UserProfileRepository,
    private val fileRepository: FileRepository,
    private val voiceInputHandler: VoiceInputHandler?,
    private val userId: Long
) {
    private val logger = LoggerFactory.getLogger(CliInterface::class.java)

    /**
     * Main entry point - starts CLI interface
     */
    fun start() {
        logger.info("Starting CLI interface for user: $userId")

        // Create terminal and line reader once
        val terminal = TerminalBuilder.builder().build()
        val lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .build()

        try {
            // Check for profile and offer setup
            runBlocking {
                checkAndOfferProfileSetup(lineReader)
            }

            // Show welcome message
            showWelcomeMessage()

            // Start REPL loop with the line reader
            runRepl(lineReader)
        } finally {
            terminal.close()
        }
    }

    /**
     * Check if profile exists, offer setup if not
     */
    private suspend fun checkAndOfferProfileSetup(lineReader: org.jline.reader.LineReader) {
        if (!profileRepository.profileExists(userId)) {
            outputFormatter.println()
            outputFormatter.println(outputFormatter.formatSystemMessage(
                "No profile found. Would you like to set up your profile for personalized responses? (yes/no)"
            ))

            try {
                val response = lineReader.readLine("> ").trim().lowercase()
                if (response in listOf("yes", "y")) {
                    profileSetup.runSetup(userId, lineReader)
                } else {
                    outputFormatter.println(outputFormatter.formatSystemMessage(
                        "You can set up your profile later with '/profile setup'"
                    ))
                }
            } catch (e: Exception) {
                logger.error("Error reading profile setup response", e)
                outputFormatter.println(outputFormatter.formatSystemMessage(
                    "Skipping profile setup. Use '/profile setup' later."
                ))
            }
        }
    }

    /**
     * Show welcome message with help instructions
     */
    private fun showWelcomeMessage() {
        outputFormatter.println()
        outputFormatter.println(outputFormatter.formatSystemMessage(
            "AI Assistant CLI - Type your message or use commands (type /help for help)"
        ))
        outputFormatter.println()
    }

    /**
     * REPL loop - continuous conversation
     */
    private fun runRepl(lineReader: org.jline.reader.LineReader) {
        var shouldExit = false
        while (!shouldExit) {
            try {
                // Read user input
                val input = lineReader.readLine("> ").trim()

                // Skip empty lines
                if (input.isEmpty()) continue

                // Handle commands or process message
                shouldExit = runBlocking {
                    if (input.startsWith("/")) {
                        // Command handling
                        val exit = handleCommand(input, lineReader)
                        if (exit) {
                            outputFormatter.println(outputFormatter.formatSystemMessage("Goodbye!"))
                        }
                        exit
                    } else {
                        // Normal message processing
                        processUserMessage(input)
                        false
                    }
                }
            } catch (e: UserInterruptException) {
                // Ctrl+C pressed - ask for confirmation
                outputFormatter.println()
                outputFormatter.println(outputFormatter.formatSystemMessage(
                    "Press Ctrl+D or type /exit to quit"
                ))
            } catch (e: EndOfFileException) {
                // Ctrl+D pressed - exit gracefully
                outputFormatter.println()
                outputFormatter.println(outputFormatter.formatSystemMessage("Goodbye!"))
                shouldExit = true
            }
        }
    }

    /**
     * Handle slash commands
     * Returns true if should exit REPL
     */
    private suspend fun handleCommand(command: String, lineReader: org.jline.reader.LineReader): Boolean {
        val parts = command.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1] else ""

        return when (cmd) {
            "/help" -> {
                showHelp()
                false
            }
            "/clear" -> {
                clearHistory()
                false
            }
            "/profile" -> {
                handleProfileCommand(args, lineReader)
                false
            }
            "/history" -> {
                showHistory()
                false
            }
            "/review-pr" -> {
                if (args.isEmpty()) {
                    outputFormatter.println(outputFormatter.formatError(
                        "Usage: /review-pr <github-pr-url>"
                    ))
                } else {
                    reviewPullRequest(args.trim())
                }
                false
            }
            "/summary" -> {
                getSummary()
                false
            }
            "/voice" -> {
                handleVoiceCommand(lineReader)
                false
            }
            "/exit", "/quit" -> {
                true
            }
            else -> {
                outputFormatter.println(outputFormatter.formatError(
                    "Unknown command: $cmd. Type /help for available commands."
                ))
                false
            }
        }
    }

    /**
     * Show help message with available commands
     */
    private fun showHelp() {
        outputFormatter.println()
        outputFormatter.println(outputFormatter.formatSystemMessage("Available Commands:"))
        outputFormatter.println("  /help                 - Show this help message")
        outputFormatter.println("  /clear                - Clear conversation history")
        outputFormatter.println("  /profile              - Show current profile")
        outputFormatter.println("  /profile setup        - Run profile setup")
        outputFormatter.println("  /review-pr <url>      - Review GitHub Pull Request")
        outputFormatter.println("  /summary              - Get summary of tasks and events")
        outputFormatter.println("  /history              - Show recent conversation history")
        outputFormatter.println("  /voice                - Use voice input (speak your question)")
        outputFormatter.println("  /exit or /quit        - Exit the application")
        outputFormatter.println()
        outputFormatter.println(outputFormatter.formatSystemMessage("Tips:"))
        outputFormatter.println("  - Type any message to chat with the AI assistant")
        outputFormatter.println("  - Press Ctrl+D to exit")
        outputFormatter.println("  - The assistant has access to tools for file operations, code analysis, and more")
        outputFormatter.println()
    }

    /**
     * Clear conversation history
     */
    private suspend fun clearHistory() {
        try {
            aiAgent.clearHistory(userId)
            outputFormatter.println(outputFormatter.formatSystemMessage(
                "Conversation history cleared."
            ))
        } catch (e: Exception) {
            outputFormatter.println(outputFormatter.formatError(
                "Failed to clear history: ${e.message}"
            ))
        }
    }

    /**
     * Handle profile command (view or setup)
     */
    private suspend fun handleProfileCommand(args: String, lineReader: org.jline.reader.LineReader) {
        when (args.trim().lowercase()) {
            "setup" -> {
                profileSetup.runSetup(userId, lineReader)
            }
            "", "show", "view" -> {
                val profile = profileRepository.getUserProfile(userId)
                if (profile != null) {
                    profileSetup.displayProfile(profile)
                } else {
                    outputFormatter.println(outputFormatter.formatSystemMessage(
                        "No profile found. Run '/profile setup' to create one."
                    ))
                }
            }
            else -> {
                outputFormatter.println(outputFormatter.formatError(
                    "Unknown profile command. Use: /profile [show|setup]"
                ))
            }
        }
    }

    /**
     * Show recent conversation history
     */
    private fun showHistory() {
        try {
            val history = fileRepository.getUserHistory(userId)
            if (history.isEmpty()) {
                outputFormatter.println(outputFormatter.formatSystemMessage("No conversation history."))
                return
            }

            outputFormatter.println()
            outputFormatter.println(outputFormatter.formatSystemMessage("Recent Conversation History:"))
            outputFormatter.printSeparator()

            history.takeLast(10).forEach { msg ->
                val formattedMsg = when (msg.role) {
                    "user" -> outputFormatter.formatUserMessage(msg.content)
                    "assistant" -> outputFormatter.formatAssistantMessage(msg.content)
                    else -> outputFormatter.formatSystemMessage(msg.content)
                }
                outputFormatter.println(formattedMsg)
                outputFormatter.printSeparator()
            }
            outputFormatter.println()
        } catch (e: Exception) {
            outputFormatter.println(outputFormatter.formatError(
                "Failed to load history: ${e.message}"
            ))
        }
    }

    /**
     * Review GitHub Pull Request
     */
    private suspend fun reviewPullRequest(prUrl: String) {
        val prompt = buildPrReviewPrompt(prUrl)
        processUserMessage(prompt)
    }

    /**
     * Build PR review prompt (similar to TelegramBot)
     */
    private fun buildPrReviewPrompt(prUrl: String): String {
        return """
Please conduct a comprehensive code review for the following Pull Request: $prUrl

Your review should include:

1. **PR Overview**
   - Use the github_api tool to fetch PR details (title, description, author, branch names)
   - Extract JIRA task link from PR description if present (format: PROJ-123, PROJECT-456, etc.)
   - If JIRA link found, use jira_api tool to fetch task details

2. **Code Analysis**
   - Use git_operation tool to get diff between base and PR branches
   - Read project conventions from CLAUDE.md if available
   - Read code quality rules from promts/rules.md if available
   - Analyze changes against requirements, conventions, and quality standards

3. **Review Report**
   - Requirements compliance (if JIRA task available)
   - Issues found with severity (Critical/Major/Minor), file paths, and line numbers
   - Security analysis
   - Testing coverage assessment
   - Actionable recommendations
   - Final conclusion: APPROVE / REQUEST CHANGES / REJECT

Please be thorough and professional in your review.
        """.trimIndent()
    }

    /**
     * Get summary of tasks and events
     */
    private suspend fun getSummary() {
        val prompt = "Please provide a summary of all events and tasks for today, including any deadlines or important items."
        processUserMessage(prompt)
    }

    /**
     * Handle voice input command
     * Records audio from microphone and transcribes to text
     */
    private suspend fun handleVoiceCommand(lineReader: org.jline.reader.LineReader) {
        // Check if voice input is enabled
        if (voiceInputHandler == null) {
            outputFormatter.println(outputFormatter.formatError(
                "Voice input is not enabled. Set VOICE_ENABLED=true in .env"
            ))
            return
        }

        try {
            // Capture and transcribe voice input
            // Pass a lambda that waits for Enter key to stop recording
            val transcribedText = voiceInputHandler.captureVoiceInput {
                // Wait for Enter key press (blocks until user presses Enter)
                lineReader.readLine("")
            }

            if (transcribedText != null) {
                // Display what was heard
                outputFormatter.println(outputFormatter.formatUserMessage(transcribedText))
                outputFormatter.printSeparator()

                // Process as normal message
                processUserMessage(transcribedText)
            } else {
                outputFormatter.println(outputFormatter.formatSystemMessage(
                    "Voice input cancelled."
                ))
            }
        } catch (e: Exception) {
            logger.error("Voice input error", e)
            outputFormatter.println(outputFormatter.formatError(
                "Voice input failed: ${e.message}"
            ))
        }
    }

    /**
     * Process user message through AI agent
     */
    private suspend fun processUserMessage(message: String) {
        try {
            // Show user message
            outputFormatter.println(outputFormatter.formatUserMessage(message))
            outputFormatter.printSeparator()

            // Process with AI agent
            val response = aiAgent.processMessage(userId, message)

            // Show assistant response
            outputFormatter.println(outputFormatter.formatAssistantMessage(response))
            outputFormatter.printSeparator()
            outputFormatter.println()
        } catch (e: Exception) {
            logger.error("Error processing message", e)
            outputFormatter.println(outputFormatter.formatError(
                "Sorry, I encountered an error: ${e.message}"
            ))
            outputFormatter.println()
        }
    }
}
