package io.github.devapro.ai.agent

import io.github.devapro.ai.repository.InterviewMessage
import io.github.devapro.ai.repository.ProfileSetupState
import io.github.devapro.ai.repository.UserProfile
import io.github.devapro.ai.repository.UserProfileRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Handles user profile interview flow
 * Uses OpenAI to conduct conversational interview
 */
class ProfileInterviewer(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val profileRepository: UserProfileRepository
) {
    private val logger = LoggerFactory.getLogger(ProfileInterviewer::class.java)
    private val modelName = "gpt-4o-mini"  // Cost-effective for interviews

    /**
     * Start or continue profile interview
     * Returns AI response to user
     */
    suspend fun conductInterview(userId: Long, userMessage: String): String {
        logger.info("Conducting interview for user $userId, message: ${userMessage.take(50)}...")

        // Get or create setup state
        val setupState = profileRepository.getSetupState(userId)
            ?: createInitialSetupState(userId)

        // Build interview messages
        val messages = buildInterviewMessages(setupState, userMessage)

        // Call OpenAI
        val response = callOpenAI(messages)

        // Check if interview is complete
        val isComplete = checkIfComplete(response, setupState)

        if (isComplete) {
            logger.info("Interview complete for user $userId, extracting profile...")
            // Extract profile from conversation and save
            val profile = extractProfileFromConversation(userId, messages, response)
            profileRepository.saveUserProfile(profile)
            profileRepository.clearSetupState(userId)

            val cleanResponse = response.replace("INTERVIEW_COMPLETE", "").trim()
            return "$cleanResponse\n\n✅ Profile setup complete! You can update it anytime with /profile"
        } else {
            // Update setup state with new conversation history
            val updatedHistory = setupState.conversationHistory +
                InterviewMessage(role = "user", content = userMessage) +
                InterviewMessage(role = "assistant", content = response)

            val updatedState = setupState.copy(
                currentStep = setupState.currentStep + 1,
                conversationHistory = updatedHistory
            )
            profileRepository.saveSetupState(updatedState)

            return response
        }
    }

    /**
     * Create initial setup state for new interview
     */
    private fun createInitialSetupState(userId: Long): ProfileSetupState {
        val now = LocalDateTime.now().toString()
        val state = ProfileSetupState(
            userId = userId,
            isInSetup = true,
            currentStep = 0,
            startedAt = now,
            conversationHistory = emptyList(),
            partialProfile = profileRepository.createDefaultProfile(userId)
        )
        profileRepository.saveSetupState(state)
        logger.info("Created initial setup state for user $userId")
        return state
    }

    /**
     * Build conversation messages for interview
     */
    private fun buildInterviewMessages(
        state: ProfileSetupState,
        userMessage: String
    ): List<OpenAIMessage> {
        val messages = mutableListOf<OpenAIMessage>()

        // System prompt for interview
        messages.add(
            OpenAIMessage(
                role = "system",
                content = """
                You are conducting a friendly user profile interview.
                Gather the following information conversationally:
                1. Name (first name is enough)
                2. Role (e.g., developer, designer, manager, student)
                3. Timezone (e.g., America/New_York, Europe/London, Asia/Tokyo)
                4. Preferred language (e.g., English, Spanish, Russian)
                5. Communication formality (casual, professional, or friendly)
                6. Response verbosity preference (concise, balanced, or detailed)
                7. Emoji usage preference (yes or no)

                Ask 2-3 questions at a time naturally. Be friendly and conversational.
                After getting all 7 pieces of information, thank the user and include "INTERVIEW_COMPLETE" at the end of your message.

                Don't list questions like a form. Ask naturally based on what they've shared.
                If they provide multiple pieces of info at once, acknowledge all and move on.
                If they seem unsure, suggest reasonable defaults.
            """.trimIndent()
            )
        )

        // Add conversation history from setup state
        state.conversationHistory.forEach { msg ->
            messages.add(OpenAIMessage(role = msg.role, content = msg.content))
        }

        // Add current user message
        messages.add(OpenAIMessage(role = "user", content = userMessage))

        return messages
    }

    /**
     * Call OpenAI API for interview response
     */
    private suspend fun callOpenAI(messages: List<OpenAIMessage>): String {
        val request = OpenAIRequest(
            model = modelName,
            messages = messages,
            temperature = 0.7,  // Slightly creative but consistent
            stream = false
        )

        try {
            val httpResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!httpResponse.status.isSuccess()) {
                logger.error("OpenAI API error during interview: ${httpResponse.status}")
                return "Sorry, I encountered an error. Please try again with /profile"
            }

            val response = httpResponse.body<OpenAIResponse>()
            return response.choices.firstOrNull()?.message?.content
                ?: "Sorry, I couldn't process that. Please try again."
        } catch (e: Exception) {
            logger.error("Exception during OpenAI call in interview: ${e.message}", e)
            return "Sorry, I encountered an error. Please try again with /profile"
        }
    }

    /**
     * Check if interview is complete based on AI response
     */
    private fun checkIfComplete(response: String, state: ProfileSetupState): Boolean {
        // Simple check: AI includes "INTERVIEW_COMPLETE" signal
        // Or we've asked 10+ questions (fallback)
        return response.contains("INTERVIEW_COMPLETE", ignoreCase = true) || state.currentStep >= 10
    }

    /**
     * Extract profile data from conversation using OpenAI
     * Makes a second API call to extract structured data
     */
    private suspend fun extractProfileFromConversation(
        userId: Long,
        conversationMessages: List<OpenAIMessage>,
        finalResponse: String
    ): UserProfile {
        logger.info("Extracting profile data for user $userId")

        // Build conversation summary for extraction
        val conversationSummary = conversationMessages
            .filter { it.role != "system" }
            .joinToString("\n") { "${it.role}: ${it.content}" }

        // Build extraction prompt
        val extractionMessages = listOf(
            OpenAIMessage(
                role = "system",
                content = """
                    Extract user profile information from the conversation.
                    Return ONLY valid JSON with these fields:
                    {
                        "name": "string or null",
                        "role": "string or null",
                        "timezone": "string or null",
                        "language": "string or null",
                        "formality": "casual or professional or friendly or null",
                        "verbosity": "concise or balanced or detailed or null",
                        "useEmoji": true or false or null
                    }

                    If information wasn't provided, use null for that field.
                    Make sure the JSON is valid and properly formatted.
                """.trimIndent()
            ),
            OpenAIMessage(
                role = "user",
                content = "Extract profile from this conversation:\n\n$conversationSummary"
            )
        )

        val request = OpenAIRequest(
            model = modelName,
            messages = extractionMessages,
            temperature = 0.0,  // Deterministic extraction
            stream = false,
            responseFormat = ResponseFormat(type = "json_object")
        )

        return try {
            val httpResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val response = httpResponse.body<OpenAIResponse>()
            val jsonContent = response.choices.firstOrNull()?.message?.content ?: "{}"
            logger.debug("Extracted JSON: $jsonContent")

            val extractedData = Json.parseToJsonElement(jsonContent).jsonObject

            val now = LocalDateTime.now().toString()
            UserProfile(
                userId = userId,
                name = extractedData["name"]?.jsonPrimitive?.contentOrNull,
                role = extractedData["role"]?.jsonPrimitive?.contentOrNull,
                timezone = extractedData["timezone"]?.jsonPrimitive?.contentOrNull,
                language = extractedData["language"]?.jsonPrimitive?.contentOrNull,
                formality = extractedData["formality"]?.jsonPrimitive?.contentOrNull,
                verbosity = extractedData["verbosity"]?.jsonPrimitive?.contentOrNull,
                useEmoji = extractedData["useEmoji"]?.jsonPrimitive?.booleanOrNull,
                createdAt = now,
                updatedAt = now,
                isComplete = true
            )
        } catch (e: Exception) {
            logger.error("Failed to extract profile: ${e.message}", e)
            // Return minimal profile on failure
            val now = LocalDateTime.now().toString()
            UserProfile(
                userId = userId,
                createdAt = now,
                updatedAt = now,
                isComplete = true
            )
        }
    }
}
