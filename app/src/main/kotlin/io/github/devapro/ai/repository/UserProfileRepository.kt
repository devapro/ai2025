package io.github.devapro.ai.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime

/**
 * Repository for managing user profiles
 * Similar pattern to FileRepository but focused on user profiles
 */
class UserProfileRepository(
    private val profilesDir: String = "profiles"
) {
    private val logger = LoggerFactory.getLogger(UserProfileRepository::class.java)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Ensure profiles directory exists
        File(profilesDir).mkdirs()
        logger.info("UserProfileRepository initialized with directory: $profilesDir")
    }

    /**
     * Check if a user profile exists
     */
    fun profileExists(userId: Long): Boolean {
        val profileFile = File(profilesDir, "user_${userId}_profile.json")
        return profileFile.exists()
    }

    /**
     * Get user profile, returns null if not found
     */
    fun getUserProfile(userId: Long): UserProfile? {
        val profileFile = File(profilesDir, "user_${userId}_profile.json")
        if (!profileFile.exists()) {
            logger.debug("Profile not found for user $userId")
            return null
        }

        return try {
            val profileJson = profileFile.readText()
            json.decodeFromString<UserProfile>(profileJson)
        } catch (e: Exception) {
            logger.error("Failed to load profile for user $userId: ${e.message}", e)
            null
        }
    }

    /**
     * Save or update user profile
     */
    fun saveUserProfile(profile: UserProfile) {
        val profileFile = File(profilesDir, "user_${profile.userId}_profile.json")

        try {
            val updatedProfile = profile.copy(
                updatedAt = LocalDateTime.now().toString()
            )
            val profileJson = json.encodeToString(updatedProfile)
            profileFile.writeText(profileJson)
            logger.info("Saved profile for user ${profile.userId}")
        } catch (e: Exception) {
            logger.error("Failed to save profile for user ${profile.userId}: ${e.message}", e)
            throw e
        }
    }

    /**
     * Create a new profile with default values
     */
    fun createDefaultProfile(userId: Long): UserProfile {
        val now = LocalDateTime.now().toString()
        return UserProfile(
            userId = userId,
            createdAt = now,
            updatedAt = now,
            isComplete = false
        )
    }

    /**
     * Get or create profile (convenience method)
     */
    fun getOrCreateProfile(userId: Long): UserProfile {
        return getUserProfile(userId) ?: createDefaultProfile(userId)
    }

    /**
     * Get profile setup state
     */
    fun getSetupState(userId: Long): ProfileSetupState? {
        val stateFile = File(profilesDir, "user_${userId}_setup.json")
        if (!stateFile.exists()) {
            return null
        }

        return try {
            val stateJson = stateFile.readText()
            json.decodeFromString<ProfileSetupState>(stateJson)
        } catch (e: Exception) {
            logger.error("Failed to load setup state for user $userId: ${e.message}", e)
            null
        }
    }

    /**
     * Save profile setup state
     */
    fun saveSetupState(state: ProfileSetupState) {
        val stateFile = File(profilesDir, "user_${state.userId}_setup.json")

        try {
            val stateJson = json.encodeToString(state)
            stateFile.writeText(stateJson)
            logger.debug("Saved setup state for user ${state.userId}, step ${state.currentStep}")
        } catch (e: Exception) {
            logger.error("Failed to save setup state for user ${state.userId}: ${e.message}", e)
            throw e
        }
    }

    /**
     * Clear setup state after interview completion
     */
    fun clearSetupState(userId: Long) {
        val stateFile = File(profilesDir, "user_${userId}_setup.json")
        if (stateFile.exists()) {
            stateFile.delete()
            logger.info("Cleared setup state for user $userId")
        }
    }
}
