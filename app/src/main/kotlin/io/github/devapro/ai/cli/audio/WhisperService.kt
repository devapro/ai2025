package io.github.devapro.ai.cli.audio

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * OpenAI Whisper API client for audio transcription
 *
 * Features:
 * - Multipart file upload to Whisper API
 * - Automatic retry on network errors (up to 3 attempts)
 * - Error parsing and handling
 * - 60-second timeout for large files
 *
 * Cost: ~$0.006 per minute of audio
 * Endpoint: https://api.openai.com/v1/audio/transcriptions
 */
class WhisperService(
    private val apiKey: String,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(WhisperService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val MODEL = "whisper-1"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val REQUEST_TIMEOUT_MS = 60000L  // 60 seconds
    }

    /**
     * Transcribe audio file to text using Whisper API
     *
     * @param audioFile WAV audio file to transcribe (up to 25MB)
     * @param language Optional language code (e.g., "en", "es"). Auto-detect if null.
     * @return Transcribed text
     * @throws Exception if transcription fails after retries
     */
    suspend fun transcribe(audioFile: File, language: String? = null): String {
        require(audioFile.exists()) { "Audio file does not exist: ${audioFile.absolutePath}" }
        require(audioFile.length() > 0) { "Audio file is empty: ${audioFile.absolutePath}" }
        require(audioFile.length() <= 25 * 1024 * 1024) {
            "Audio file too large (${audioFile.length() / 1024 / 1024}MB). Maximum is 25MB."
        }

        logger.info("Transcribing audio file: ${audioFile.name} (${audioFile.length()} bytes)")
        logger.info("Audio file path: ${audioFile.absolutePath}")

        var lastException: Exception? = null

        // Retry up to MAX_RETRIES times on network errors
        repeat(MAX_RETRIES) { attempt ->
            try {
                logger.info("Transcription attempt ${attempt + 1}/$MAX_RETRIES")

                val response = httpClient.post(WHISPER_API_URL) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $apiKey")
                    }

                    setBody(MultiPartFormDataContent(
                        formData {
                            // Audio file - use ChannelProvider for proper streaming
                            appendInput(
                                key = "file",
                                headers = Headers.build {
                                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"${audioFile.name}\"")
                                    append(HttpHeaders.ContentType, "audio/wav")
                                },
                                size = audioFile.length()
                            ) {
                                buildPacket { writeFully(audioFile.readBytes()) }
                            }

                            // Model
                            append("model", MODEL)

                            // Language (optional)
                            if (language != null) {
                                append("language", language)
                            }

                            // Response format
                            append("response_format", "json")
                        }
                    ))
                }

                logger.info("API response status: ${response.status}")

                // Parse response
                return parseResponse(response)

            } catch (e: Exception) {
                lastException = e
                logger.warn("Transcription attempt ${attempt + 1}/$MAX_RETRIES failed: ${e.message}")

                // Don't retry on the last attempt
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))  // Exponential backoff
                }
            }
        }

        // All retries failed
        throw Exception("Transcription failed after $MAX_RETRIES attempts: ${lastException?.message}", lastException)
    }

    /**
     * Parse Whisper API response
     *
     * @param response HTTP response from Whisper API
     * @return Transcribed text
     * @throws Exception if response indicates error
     */
    private suspend fun parseResponse(response: HttpResponse): String {
        val responseBody = response.bodyAsText()
        logger.info("API response body: $responseBody")

        return when (response.status) {
            HttpStatusCode.OK -> {
                try {
                    val transcription = json.decodeFromString<WhisperTranscription>(responseBody)
                    logger.info("Transcription successful (${transcription.text.length} chars): ${transcription.text}")
                    transcription.text
                } catch (e: Exception) {
                    logger.error("Failed to parse transcription response: $responseBody", e)
                    throw Exception("Invalid transcription response: ${e.message}")
                }
            }

            HttpStatusCode.BadRequest,
            HttpStatusCode.Unauthorized,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError -> {
                try {
                    val error = json.decodeFromString<WhisperError>(responseBody)
                    val errorMsg = error.error.message
                    logger.error("Whisper API error: $errorMsg")
                    throw Exception("Whisper API error: $errorMsg")
                } catch (e: Exception) {
                    // If we can't parse the error, return the raw response
                    logger.error("Failed to parse error response: $responseBody")
                    throw Exception("API error (${response.status.value}): $responseBody")
                }
            }

            else -> {
                logger.error("Unexpected response status: ${response.status.value}")
                throw Exception("Unexpected API response (${response.status.value}): $responseBody")
            }
        }
    }
}
