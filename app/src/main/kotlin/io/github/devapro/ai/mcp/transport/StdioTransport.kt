package io.github.devapro.ai.mcp.transport

import io.github.devapro.ai.mcp.model.JsonRpcError
import io.github.devapro.ai.mcp.model.JsonRpcRequest
import io.github.devapro.ai.mcp.model.JsonRpcResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

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

            logger.info("Stdio transport initialized successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to initialize stdio transport: ${e.message}", e)
            close()
            false
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
                logger.debug("Sending request: $requestJson")

                // Write request with newline delimiter
                currentWriter.write(requestJson)
                currentWriter.newLine()
                currentWriter.flush()

                // Read response line
                val responseLine = currentReader.readLine()
                    ?: throw Exception("No response from server (EOF)")

                logger.debug("Received response: $responseLine")

                // Parse response
                json.decodeFromString<JsonRpcResponse>(responseLine)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error("Request timed out after ${timeout}ms")
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
