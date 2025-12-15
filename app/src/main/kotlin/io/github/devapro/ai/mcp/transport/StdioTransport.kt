package io.github.devapro.ai.mcp.transport

import io.github.devapro.ai.mcp.model.JsonRpcError
import io.github.devapro.ai.mcp.model.JsonRpcRequest
import io.github.devapro.ai.mcp.model.JsonRpcResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.launch

/**
 * Stdio-based transport for local MCP servers
 *
 * Launches an external process and communicates via stdin/stdout
 * using newline-delimited JSON-RPC protocol.
 *
 * @param command The command to execute (e.g., "npx", "python")
 * @param args Command-line arguments
 * @param env Environment variables for the process
 * @param timeout Timeout in milliseconds for requests (default: 30 seconds)
 */
class StdioTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
    private val env: Map<String, String> = emptyMap(),
    private val timeout: Long = 30_000
) : McpTransport {

    private val logger = LoggerFactory.getLogger(StdioTransport::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false  // Compact JSON for network efficiency
        encodeDefaults = true  // Include default values like jsonrpc = "2.0"
        explicitNulls = false  // Omit null fields (critical for JSON-RPC notifications)
    }

    @Volatile
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.info("Initializing stdio transport: $command ${args.joinToString(" ")}")

            // Build process with command and args
            val processBuilder = ProcessBuilder(command, *args.toTypedArray())

            // Add environment variables
            if (env.isNotEmpty()) {
                processBuilder.environment().putAll(env)
                logger.debug("Added environment variables: ${env.keys}")
            }

            // Start the process
            process = processBuilder.start()

            // Setup I/O streams
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream, Charsets.UTF_8))
            reader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))

            // Start stderr reader in background to capture server logs
            val errorReader = BufferedReader(InputStreamReader(process!!.errorStream, Charsets.UTF_8))
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    errorReader.useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank()) {
                                logger.info("[Server stderr] $line")
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Stderr reader closed: ${e.message}")
                }
            }

            // Check if process is still alive
            logger.info("DEBUG: About to delay 100ms")
            delay(100) // Give it a moment
            logger.info("DEBUG: After delay, checking if process is alive")

            if (!process!!.isAlive) {
                logger.error("❌ Process died immediately after starting")
                val exitCode = process!!.exitValue()
                logger.error("Exit code: $exitCode")
                close()
                return@withContext false
            }

            logger.info("✅ Stdio transport initialized successfully (process alive)")
            logger.info("DEBUG: About to return true from transport.initialize()")
            true
        } catch (e: Exception) {
            logger.error("Failed to initialize stdio transport: ${e.message}", e)
            close()
            false
        }
    }

    /**
     * Send a notification (fire-and-forget, no response expected)
     *
     * According to JSON-RPC 2.0, notifications have no id field and
     * the server must not send a response.
     */
    suspend fun sendNotification(request: JsonRpcRequest) = withContext(Dispatchers.IO) {
        val currentWriter = writer
        if (currentWriter == null) {
            logger.error("Transport not initialized")
            return@withContext
        }

        try {
            val requestJson = json.encodeToString(request)
            logger.info("📤 Sending notification: ${request.method}")
            logger.debug("Notification JSON: $requestJson")

            currentWriter.write(requestJson)
            currentWriter.newLine()
            currentWriter.flush()
            logger.info("✅ Notification sent (no response expected)")
        } catch (e: Exception) {
            logger.error("Error sending notification: ${e.message}", e)
        }
    }

    override suspend fun send(request: JsonRpcRequest): JsonRpcResponse = withContext(Dispatchers.IO) {
        val currentProcess = process
        val currentWriter = writer
        val currentReader = reader

        if (currentProcess == null || currentWriter == null || currentReader == null) {
            logger.error("Transport not initialized")
            return@withContext JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32603,
                    message = "Transport not initialized"
                )
            )
        }

        if (!currentProcess.isAlive) {
            logger.error("Process is not alive")
            return@withContext JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32603,
                    message = "Process is not alive"
                )
            )
        }

        try {
            withTimeout(timeout) {
                // Serialize request to JSON
                val requestJson = json.encodeToString(request)
                logger.info("📤 Sending ${request.method} request to server")
                logger.info("DEBUG: Request JSON: $requestJson")

                // Write request with newline delimiter
                logger.info("DEBUG: Writing to stdin...")
                currentWriter.write(requestJson)
                logger.info("DEBUG: Writing newline...")
                currentWriter.newLine()
                logger.info("DEBUG: Flushing writer...")
                currentWriter.flush()
                logger.info("DEBUG: Flush complete, request sent!")

                logger.info("⏳ Waiting for ${request.method} response...")
                logger.info("DEBUG: About to call readLine() on reader")

                // Check if process is still alive before reading
                if (!currentProcess.isAlive) {
                    logger.error("❌ Process died while waiting for response!")
                    logger.error("Exit code: ${currentProcess.exitValue()}")
                    throw Exception("Process died while waiting for response")
                }

                logger.info("DEBUG: Process is alive, about to read response...")

                // Read response line
                val responseLine = currentReader.readLine()
                logger.info("DEBUG: readLine() returned: ${if (responseLine == null) "NULL" else "string of ${responseLine.length} chars"}")

                if (responseLine == null) {
                    logger.error("❌ No response from server (EOF reached)")
                    logger.error("Server process may have crashed or closed connection")
                    // Check if process is still alive
                    if (currentProcess.isAlive) {
                        logger.error("Process is still alive but sent EOF - possible communication issue")
                    } else {
                        logger.error("Process has died - exit code: ${currentProcess.exitValue()}")
                    }
                    throw Exception("No response from server (EOF)")
                }

                logger.info("📥 Received ${request.method} response (${responseLine.length} chars)")
                logger.debug("Response JSON: $responseLine")

                // Parse response
                try {
                    val response = json.decodeFromString<JsonRpcResponse>(responseLine)
                    logger.info("✅ Response parsed successfully")
                    response
                } catch (e: Exception) {
                    logger.error("❌ Failed to parse response: ${e.message}")
                    logger.error("Raw response: $responseLine")
                    throw e
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error("❌ Request timed out after ${timeout}ms")
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32603,
                    message = "Request timed out after ${timeout}ms"
                )
            )
        } catch (e: Exception) {
            logger.error("Error sending request: ${e.message}", e)
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32603,
                    message = "Internal error: ${e.message}"
                )
            )
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            logger.info("Closing stdio transport")

            // Close streams
            writer?.close()
            reader?.close()

            // Terminate process
            process?.destroy()

            // Wait for process to terminate (with timeout)
            process?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)

            // Force kill if still alive
            if (process?.isAlive == true) {
                logger.warn("Process did not terminate gracefully, forcing kill")
                process?.destroyForcibly()
            }

            logger.info("Stdio transport closed")
        } catch (e: Exception) {
            logger.error("Error closing transport: ${e.message}", e)
        } finally {
            process = null
            writer = null
            reader = null
        }
    }
}
