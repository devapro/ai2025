package io.github.devapro.ai.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User profile data for personalization
 * Stores user preferences and communication style
 */
@Serializable
data class UserProfile(
    @SerialName("userId")
    val userId: Long,

    // Basic Info (all nullable for incomplete profiles)
    @SerialName("name")
    val name: String? = null,

    @SerialName("role")
    val role: String? = null,  // e.g., "developer", "project manager", "designer"

    @SerialName("timezone")
    val timezone: String? = null,  // e.g., "America/New_York", "Europe/London"

    // Communication Style
    @SerialName("language")
    val language: String? = null,  // e.g., "en", "es", "fr"

    @SerialName("formality")
    val formality: String? = null,  // "casual", "professional", "friendly"

    @SerialName("verbosity")
    val verbosity: String? = null,  // "concise", "balanced", "detailed"

    @SerialName("useEmoji")
    val useEmoji: Boolean? = null,  // true = use emoji, false = avoid emoji

    // Metadata
    @SerialName("createdAt")
    val createdAt: String,  // ISO timestamp

    @SerialName("updatedAt")
    val updatedAt: String,  // ISO timestamp

    @SerialName("isComplete")
    val isComplete: Boolean = false  // Whether profile setup is complete
)

/**
 * Profile setup state for tracking interview progress
 * This is a temporary state deleted after interview completion
 */
@Serializable
data class ProfileSetupState(
    @SerialName("userId")
    val userId: Long,

    @SerialName("isInSetup")
    val isInSetup: Boolean = false,

    @SerialName("currentStep")
    val currentStep: Int = 0,  // 0-10 for different questions

    @SerialName("startedAt")
    val startedAt: String,

    @SerialName("conversationHistory")
    val conversationHistory: List<InterviewMessage> = emptyList(),

    @SerialName("partialProfile")
    val partialProfile: UserProfile
)

/**
 * Interview message for storing conversation during setup
 */
@Serializable
data class InterviewMessage(
    @SerialName("role")
    val role: String,  // "user" or "assistant"

    @SerialName("content")
    val content: String
)
