package io.github.devapro.ai.cli

import io.github.devapro.ai.repository.UserProfile
import io.github.devapro.ai.repository.UserProfileRepository
import java.time.LocalDateTime

/**
 * Handles synchronous profile setup for CLI users
 *
 * Responsibilities:
 * - Interactive Q&A prompts for profile fields
 * - Sensible defaults for each field
 * - Profile validation and saving
 * - Simple, no AI conversation (unlike ProfileInterviewer)
 */
class CliProfileSetup(
    private val profileRepository: UserProfileRepository,
    private val outputFormatter: CliOutputFormatter
) {
    /**
     * Run interactive profile setup
     * Returns UserProfile if successful, null if user cancels
     */
    suspend fun runSetup(userId: Long, lineReader: org.jline.reader.LineReader? = null): UserProfile? {
        outputFormatter.println()
        outputFormatter.println(outputFormatter.formatSystemMessage(
            "Welcome! Let's set up your profile for personalized responses."
        ))
        outputFormatter.println(outputFormatter.formatSystemMessage(
            "Press Enter to use default values, or type 'skip' to skip profile setup."
        ))
        outputFormatter.println()

        // Check if user wants to skip
        val name = promptWithDefault("Name", "User", lineReader) ?: return null
        if (name.equals("skip", ignoreCase = true)) {
            outputFormatter.println(outputFormatter.formatSystemMessage(
                "Profile setup skipped. You can run '/profile setup' later."
            ))
            return null
        }

        val role = promptWithDefault("Role (e.g., developer, manager)", "User", lineReader) ?: return null
        val timezone = promptWithDefault("Timezone (e.g., America/New_York)", "UTC", lineReader) ?: return null
        val language = promptWithDefault("Language (e.g., English, русский)", "English", lineReader) ?: return null
        val formality = promptWithDefault("Formality (casual/professional/friendly)", "professional", lineReader) ?: return null
        val verbosity = promptWithDefault("Verbosity (concise/balanced/detailed)", "balanced", lineReader) ?: return null
        val useEmojiInput = promptWithDefault("Use emoji (yes/no)", "no", lineReader) ?: return null
        val useEmoji = useEmojiInput.lowercase() in listOf("yes", "y", "true", "1")

        // Create profile
        val now = LocalDateTime.now().toString()
        val profile = UserProfile(
            userId = userId,
            name = name,
            role = role,
            timezone = timezone,
            language = language,
            formality = formality,
            verbosity = verbosity,
            useEmoji = useEmoji,
            createdAt = now,
            updatedAt = now,
            isComplete = true
        )

        // Save profile
        return try {
            profileRepository.saveUserProfile(profile)
            outputFormatter.println()
            outputFormatter.println(outputFormatter.formatSystemMessage("✓ Profile saved successfully!"))
            outputFormatter.println()
            profile
        } catch (e: Exception) {
            outputFormatter.println(outputFormatter.formatError("Failed to save profile: ${e.message}"))
            null
        }
    }

    /**
     * Prompt user for input with a default value
     * Returns user input or default if empty, null if EOF (Ctrl+D)
     */
    private fun promptWithDefault(prompt: String, default: String, lineReader: org.jline.reader.LineReader?): String? {
        return try {
            val input = if (lineReader != null) {
                // Use JLine's LineReader for proper terminal support
                lineReader.readLine("${prompt} [${default}]: ")
            } else {
                // Fallback to basic readLine (for backwards compatibility)
                outputFormatter.print("${prompt} [${default}]: ")
                readLine()
            }

            // Handle Ctrl+D (EOF)
            if (input == null) {
                outputFormatter.println()
                return null
            }

            // Return trimmed input or default
            input.trim().ifEmpty { default }
        } catch (e: org.jline.reader.EndOfFileException) {
            // Ctrl+D pressed
            outputFormatter.println()
            null
        } catch (e: org.jline.reader.UserInterruptException) {
            // Ctrl+C pressed
            outputFormatter.println()
            null
        }
    }

    /**
     * Display current profile
     */
    fun displayProfile(profile: UserProfile) {
        outputFormatter.println()
        outputFormatter.println(outputFormatter.formatSystemMessage("Current Profile:"))
        outputFormatter.println("  User ID:   ${profile.userId}")
        outputFormatter.println("  Name:      ${profile.name ?: "Not set"}")
        outputFormatter.println("  Role:      ${profile.role ?: "Not set"}")
        outputFormatter.println("  Timezone:  ${profile.timezone ?: "Not set"}")
        outputFormatter.println("  Language:  ${profile.language ?: "Not set"}")
        outputFormatter.println("  Formality: ${profile.formality ?: "Not set"}")
        outputFormatter.println("  Verbosity: ${profile.verbosity ?: "Not set"}")
        outputFormatter.println("  Use Emoji: ${profile.useEmoji ?: "Not set"}")
        outputFormatter.println("  Status:    ${if (profile.isComplete) "Complete" else "Incomplete"}")
        outputFormatter.println("  Created:   ${profile.createdAt}")
        outputFormatter.println("  Updated:   ${profile.updatedAt}")
        outputFormatter.println()
    }
}
