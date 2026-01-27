package io.github.devapro.ai.cli.audio

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.sound.sampled.*

/**
 * Records audio from system microphone using Java Sound API
 *
 * Features:
 * - 16kHz mono audio (optimal for speech recognition)
 * - 30-second maximum duration
 * - Real-time visual feedback
 * - Manual stop via stop() method
 * - Saves to WAV format
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,  // 16kHz
    private val maxDuration: Int = 30      // 30 seconds
) {
    private val logger = LoggerFactory.getLogger(AudioRecorder::class.java)

    @Volatile
    private var isRecording = false

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

            val startTime = System.currentTimeMillis()
            val maxDurationMs = maxDuration * 1000L
            var lastStatusSecond = -1L

            // Record in background while monitoring duration and cancellation
            val recordingJob = launch {
                while (isRecording && (System.currentTimeMillis() - startTime < maxDurationMs)) {
                    val count = microphone.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        audioData.write(buffer, 0, count)
                    }

                    // Update status every second (but don't block the recording loop!)
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    if (elapsed != lastStatusSecond) {
                        lastStatusSecond = elapsed
                        onStatus("🎤 Recording... (${elapsed}s/${maxDuration}s)")
                    }
                }
            }

            // Wait for recording to finish or max duration
            recordingJob.join()

            microphone.stop()
            microphone.close()

            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            val recordedBytes = audioData.toByteArray()

            logger.info("Recording stopped: ${recordedBytes.size} bytes captured in ${elapsed}s")

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

            onStatus("✓ Recording complete (${elapsed}s, ${recordedBytes.size} bytes)")

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
            // List available mixers for debugging
            val mixers = AudioSystem.getMixerInfo()
//            logger.info("Available audio mixers: ${mixers.size}")
//            mixers.forEachIndexed { index, mixer ->
//                logger.info("  [$index] ${mixer.name} - ${mixer.description}")
//            }

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
