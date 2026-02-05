package io.github.devapro.ai.cli.audio

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.sound.sampled.*
import kotlin.math.sqrt

/**
 * Records audio from system microphone using Java Sound API
 *
 * Features:
 * - 16kHz mono audio (optimal for speech recognition)
 * - 30-second maximum duration
 * - Automatic silence detection (optional)
 * - Real-time visual feedback
 * - Manual stop via stop() method
 * - Saves to WAV format
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,  // 16kHz
    private val maxDuration: Int = 30,      // 30 seconds
    private val autoStopOnSilence: Boolean = true,
    private val silenceThreshold: Double = 300.0,
    private val silenceDuration: Int = 5  // seconds
) {
    private val logger = LoggerFactory.getLogger(AudioRecorder::class.java)

    @Volatile
    private var isRecording = false

    private val silenceDetector: SilenceDetector? =
        if (autoStopOnSilence) {
            SilenceDetector(
                silenceThreshold = silenceThreshold,
                silenceDurationMs = silenceDuration * 1000L,
                gracePeriodMs = 2000L,  // 2-second grace period
                sampleRate = sampleRate,
                bufferSize = 4096
            )
        } else {
            null
        }

    /**
     * Record audio from microphone
     *
     * @param onStatus Callback for status updates (recording progress)
     * @return File containing recorded audio in WAV format, or null if cancelled
     */
    suspend fun record(onStatus: (String) -> Unit): File? = withContext(Dispatchers.IO) {
        val audioFormat = getAudioFormat()
        val microphone = openMicrophone(audioFormat)

        if (microphone == null) {
            onStatus("❌ No microphone available. Please check your audio devices.")
            return@withContext null
        }

        try {
            isRecording = true
            val audioData = ByteArrayOutputStream()
            val buffer = ByteArray(4096)

            microphone.start()
            silenceDetector?.reset()

            val startTime = System.currentTimeMillis()
            val maxDurationMs = maxDuration * 1000L
            var lastStatusSecond = -1L
            var stoppedBySilence = false

            // Record in background while monitoring duration and cancellation
            val recordingJob = launch {
                while (isRecording && (System.currentTimeMillis() - startTime < maxDurationMs)) {
                    val count = microphone.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        audioData.write(buffer, 0, count)

                        // Analyze for silence (if enabled)
                        silenceDetector?.let { detector ->
                            val result = detector.analyze(buffer, count)

                            if (result.shouldStop) {
                                logger.info("Auto-stopping: ${result.consecutiveSilentMs}ms of silence detected (RMS: ${result.rms})")
                                stoppedBySilence = true
                                isRecording = false
                            }
                        }
                    }

                    // Update status every second (but don't block the recording loop!)
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    if (elapsed != lastStatusSecond) {
                        lastStatusSecond = elapsed

                        // Show silence indicator if applicable
                        val silenceInfo = silenceDetector?.let {
                            val testResult = it.analyze(buffer, count)
                            if (testResult.consecutiveSilentMs > 1000) {
                                " [${testResult.consecutiveSilentMs / 1000}s silence]"
                            } else {
                                ""
                            }
                        } ?: ""

                        onStatus("🎤 Recording... (${elapsed}s/${maxDuration}s)$silenceInfo")
                    }
                }
            }

            // Wait for recording to finish or max duration
            recordingJob.join()

            microphone.stop()
            microphone.close()

            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            val recordedBytes = audioData.toByteArray()

            logger.info("Recording stopped: ${recordedBytes.size} bytes captured in ${elapsed}s" +
                    if (stoppedBySilence) " (auto-stopped by silence detection)" else "")

            // Check if we have any audio data
            if (recordedBytes.isEmpty()) {
                onStatus("❌ No audio recorded. Please check your microphone.")
                return@withContext null
            }

            // Check minimum size (at least 1KB)
            if (recordedBytes.size < 1024) {
                onStatus("⚠️  Very short recording (${recordedBytes.size} bytes). Audio may be too quiet.")
                logger.warn("Very short recording: ${recordedBytes.size} bytes")
            }

            // Show appropriate completion message
            if (stoppedBySilence) {
                onStatus("✓ Auto-stopped after silence (${elapsed}s, ${recordedBytes.size} bytes)")
            } else {
                onStatus("✓ Recording complete (${elapsed}s, ${recordedBytes.size} bytes)")
            }

            // Create temporary WAV file
            val tempFile = File.createTempFile("ai-cli-voice-", ".wav")
            writeWavFile(recordedBytes, tempFile, audioFormat)

            logger.info("WAV file size: ${tempFile.length()} bytes (${recordedBytes.size} + 44 byte header)")

            tempFile
        } catch (e: CancellationException) {
            microphone?.stop()
            microphone?.close()
            onStatus("⏸️  Recording cancelled")
            null
        } catch (e: Exception) {
            logger.error("Recording error", e)
            microphone?.stop()
            microphone?.close()
            onStatus("❌ Recording failed: ${e.message}")
            null
        } finally {
            isRecording = false
        }
    }

    /**
     * Stop recording early
     */
    fun stop() {
        isRecording = false
    }

    /**
     * Get audio format for recording
     * 16kHz, mono, 16-bit, signed, little-endian
     */
    private fun getAudioFormat(): AudioFormat {
        return AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate.toFloat(),  // Sample rate (16kHz)
            16,                     // Sample size (16-bit)
            1,                      // Channels (mono)
            2,                      // Frame size (16-bit = 2 bytes)
            sampleRate.toFloat(),  // Frame rate (same as sample rate)
            false                   // Little-endian
        )
    }

    /**
     * Open microphone with specified audio format
     * Returns null if no microphone available
     */
    private fun openMicrophone(format: AudioFormat): TargetDataLine? {
        return try {

            val dataLineInfo = DataLine.Info(TargetDataLine::class.java, format)

            // Check if microphone is supported
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                logger.error("Microphone not supported for format: $format")
                logger.error("Required: 16kHz, mono, 16-bit, PCM")
                return null
            }

            // Get microphone line
            val line = AudioSystem.getLine(dataLineInfo) as TargetDataLine
            line.open(format)

            logger.info("Microphone opened successfully")
            logger.info("  Format: $format")
            logger.info("  Buffer size: ${line.bufferSize} bytes")
            line
        } catch (e: LineUnavailableException) {
            logger.error("Microphone unavailable - is it in use by another application?", e)
            null
        } catch (e: Exception) {
            logger.error("Failed to open microphone", e)
            null
        }
    }

    /**
     * Write audio data to WAV file with proper headers
     */
    private fun writeWavFile(audioData: ByteArray, file: File, format: AudioFormat) {
        FileOutputStream(file).use { fos ->
            val channels = format.channels
            val sampleRate = format.sampleRate.toInt()
            val bitsPerSample = format.sampleSizeInBits
            val byteRate = sampleRate * channels * (bitsPerSample / 8)
            val blockAlign = (channels * bitsPerSample / 8).toShort()

            // WAV file header
            // RIFF chunk
            fos.write("RIFF".toByteArray())
            fos.write(intToBytes(audioData.size + 36))  // File size - 8
            fos.write("WAVE".toByteArray())

            // fmt sub-chunk
            fos.write("fmt ".toByteArray())
            fos.write(intToBytes(16))  // Subchunk size
            fos.write(shortToBytes(1))  // Audio format (1 = PCM)
            fos.write(shortToBytes(channels.toShort()))
            fos.write(intToBytes(sampleRate))
            fos.write(intToBytes(byteRate))
            fos.write(shortToBytes(blockAlign))
            fos.write(shortToBytes(bitsPerSample.toShort()))

            // data sub-chunk
            fos.write("data".toByteArray())
            fos.write(intToBytes(audioData.size))
            fos.write(audioData)
        }

        logger.info("WAV file written: ${file.absolutePath} (${audioData.size} bytes)")
    }

    /**
     * Convert int to little-endian bytes
     */
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    /**
     * Convert short to little-endian bytes
     */
    private fun shortToBytes(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
}

/**
 * Detects silence in audio data using RMS amplitude analysis
 *
 * Analyzes PCM audio buffers to determine if audio is below silence threshold.
 * Tracks consecutive silent duration and signals when recording should stop.
 *
 * @param silenceThreshold RMS amplitude threshold (typical speech: 1000-10000, silence: 0-300)
 * @param silenceDurationMs How long silence must persist before triggering stop
 * @param gracePeriodMs Initial period where silence detection is disabled (prevents early cutoff)
 * @param sampleRate Audio sample rate in Hz
 * @param bufferSize Size of audio buffer in bytes
 */
class SilenceDetector(
    private val silenceThreshold: Double = 300.0,
    private val silenceDurationMs: Long = 5000L,
    private val gracePeriodMs: Long = 2000L,
    private val sampleRate: Int = 16000,
    private val bufferSize: Int = 4096
) {
    private var totalRecordingMs: Long = 0
    private var consecutiveSilentMs: Long = 0
    private val bufferDurationMs: Long =
        (bufferSize.toDouble() / (sampleRate * 2) * 1000).toLong()  // 2 bytes per sample

    /**
     * Analyze audio buffer for silence
     *
     * @param buffer Audio data buffer (16-bit PCM, little-endian)
     * @param count Number of valid bytes in buffer
     * @return Analysis result with silence status and duration
     */
    fun analyze(buffer: ByteArray, count: Int): SilenceAnalysisResult {
        totalRecordingMs += bufferDurationMs
        val rms = calculateRMS(buffer, count)
        val isSilent = rms < silenceThreshold

        if (isSilent) {
            consecutiveSilentMs += bufferDurationMs
        } else {
            consecutiveSilentMs = 0
        }

        // Only trigger stop after grace period and sufficient silence
        val shouldStop = totalRecordingMs > gracePeriodMs &&
                consecutiveSilentMs >= silenceDurationMs

        return SilenceAnalysisResult(
            rms = rms,
            isSilent = isSilent,
            consecutiveSilentMs = consecutiveSilentMs,
            shouldStop = shouldStop
        )
    }

    /**
     * Reset silence tracking (call at start of recording)
     */
    fun reset() {
        totalRecordingMs = 0
        consecutiveSilentMs = 0
    }

    /**
     * Calculate Root Mean Square (RMS) amplitude of audio buffer
     *
     * RMS measures the overall energy/loudness of the audio signal.
     * For 16-bit PCM audio:
     * - Near zero: silence
     * - 1000-10000: typical speech
     * - 15000+: loud speech or noise
     *
     * @param buffer Audio data (16-bit PCM, little-endian)
     * @param count Number of valid bytes
     * @return RMS amplitude value
     */
    private fun calculateRMS(buffer: ByteArray, count: Int): Double {
        var sum = 0.0
        val samples = count / 2  // 16-bit = 2 bytes per sample

        for (i in 0 until samples) {
            // Read 16-bit little-endian sample
            val byte1 = buffer[i * 2].toInt() and 0xFF
            val byte2 = buffer[i * 2 + 1].toInt() and 0xFF
            val sample = (byte2 shl 8) or byte1

            // Convert unsigned to signed
            val signedSample = if (sample > 32767) sample - 65536 else sample

            sum += (signedSample * signedSample).toDouble()
        }

        return sqrt(sum / samples)
    }
}

/**
 * Result of silence analysis for an audio buffer
 *
 * @param rms Root Mean Square amplitude of the buffer
 * @param isSilent Whether this buffer is below silence threshold
 * @param consecutiveSilentMs Total duration of consecutive silence so far
 * @param shouldStop Whether recording should be stopped due to prolonged silence
 */
data class SilenceAnalysisResult(
    val rms: Double,
    val isSilent: Boolean,
    val consecutiveSilentMs: Long,
    val shouldStop: Boolean
)
