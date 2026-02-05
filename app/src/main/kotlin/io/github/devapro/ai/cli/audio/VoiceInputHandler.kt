package io.github.devapro.ai.cli.audio

import io.github.devapro.ai.cli.CliOutputFormatter
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Orchestrates voice input capture and transcription
 *
 * Workflow:
 * 1. Record audio from microphone (with visual feedback)
 * 2. Save to temporary WAV file
 * 3. Transcribe using Whisper API
 * 4. Clean up temporary file
 * 5. Return transcribed text
 *
 * User Experience:
 * - Real-time recording progress (0s/30s, 1s/30s, etc.)
 * - Press Enter to stop recording early
 * - Visual feedback during transcription
 * - Clean error messages
 * - Automatic cleanup (even on errors)
 */
class VoiceInputHandler(
    private val audioRecorder: AudioRecorder,
    private val whisperService: WhisperService,
    private val outputFormatter: CliOutputFormatter
) {
    private val logger = LoggerFactory.getLogger(VoiceInputHandler::class.java)

    /**
     * Capture voice input and convert to text
     *
     * Full workflow with user feedback at each step
     *
     * @param onWaitForStop Suspend function to wait for stop signal (e.g., Enter key)
     * @return Transcribed text, or null if cancelled/failed
     */
    suspend fun captureVoiceInput(onWaitForStop: suspend () -> Unit): String? {
        var audioFile: File? = null

        try {
            // Show instructions
            outputFormatter.println(outputFormatter.formatSystemMessage(
                "🎤 Recording started... Press Enter to stop early"
            ))

            // Start recording in background and wait for stop signal concurrently
            coroutineScope {
                // Launch recording coroutine
                val recordingJob = async(Dispatchers.IO) {
                    audioRecorder.record { status ->
                        // Update recording status (shows progress)
                        outputFormatter.print("\r" + outputFormatter.formatSystemMessage(status))
                    }
                }

                // Launch stop signal listener
                val stopSignalJob = async(Dispatchers.IO) {
                    try {
                        onWaitForStop()
                        // User pressed Enter - stop recording
                        audioRecorder.stop()
                    } catch (e: Exception) {
                        logger.warn("Error waiting for stop signal", e)
                    }
                }

                // Wait for recording to complete
                audioFile = recordingJob.await()

                // Cancel stop signal listener if still running
                stopSignalJob.cancel()
            }

            // Check if recording was cancelled
            if (audioFile == null) {
                outputFormatter.println()
                outputFormatter.println(outputFormatter.formatSystemMessage(
                    "⏸️  Recording cancelled"
                ))
                return null
            }

            // Recording complete - move to next line
            outputFormatter.println()

            // Validate recorded file
            if (!audioFile.exists() || audioFile.length() == 0L) {
                outputFormatter.println(outputFormatter.formatError(
                    "Recording failed: No audio data captured"
                ))
                return null
            }

            // Show transcription status
            outputFormatter.println(outputFormatter.formatSystemMessage(
                "⏳ Transcribing audio..."
            ))

            // Transcribe audio to text
            val transcribedText = whisperService.transcribe(audioFile)

            // Show result
            outputFormatter.println(outputFormatter.formatSystemMessage(
                "✓ Transcribed: \"${transcribedText.take(100)}${if (transcribedText.length > 100) "..." else ""}\""
            ))
            outputFormatter.println()

            return transcribedText

        } catch (e: Exception) {
            logger.error("Voice input failed", e)
            outputFormatter.println()
            outputFormatter.println(outputFormatter.formatError(
                "Voice input failed: ${e.message}"
            ))
            return null

        } finally {
            // Keep audio files for debugging (don't delete them)
            if (audioFile != null && audioFile.exists()) {
                logger.info("Audio file saved for debugging: ${audioFile.absolutePath}")
                outputFormatter.println(outputFormatter.formatSystemMessage(
                    "Audio file saved: ${audioFile.absolutePath}"
                ))
            }
        }
    }
}
