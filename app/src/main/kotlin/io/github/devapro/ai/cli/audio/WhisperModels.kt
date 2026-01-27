package io.github.devapro.ai.cli.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for OpenAI Whisper API
 * Endpoint: https://api.openai.com/v1/audio/transcriptions
 */

/**
 * Successful transcription response from Whisper API
 */
@Serializable
data class WhisperTranscription(
    @SerialName("text")
    val text: String,

    @SerialName("language")
    val language: String? = null,

    @SerialName("duration")
    val duration: Double? = null
)

/**
 * Error response from Whisper API
 */
@Serializable
data class WhisperError(
    @SerialName("error")
    val error: WhisperErrorDetail
)

/**
 * Error details
 */
@Serializable
data class WhisperErrorDetail(
    @SerialName("message")
    val message: String,

    @SerialName("type")
    val type: String? = null,

    @SerialName("code")
    val code: String? = null
)
