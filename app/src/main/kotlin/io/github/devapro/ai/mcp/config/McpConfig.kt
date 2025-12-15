package io.github.devapro.ai.mcp.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root configuration for MCP servers
 *
 * Loaded from mcp-config.json file
 */
@Serializable
data class McpConfig(
    @SerialName("mcpServers")
    val mcpServers: List<McpServerConfig> = emptyList()
)

/**
 * Configuration for a single MCP server
 *
 * Supports both stdio (local process) and HTTP (remote server) transports
 */
@Serializable
data class McpServerConfig(
    /**
     * Unique identifier for this server
     */
    @SerialName("name")
    val name: String,

    /**
     * Human-readable description (optional)
     */
    @SerialName("description")
    val description: String? = null,

    /**
     * Whether this server is enabled
     */
    @SerialName("enabled")
    val enabled: Boolean = true,

    /**
     * Transport type: "stdio" or "http"
     */
    @SerialName("type")
    val type: String,

    // Stdio-specific configuration

    /**
     * Command to execute (e.g., "npx", "python", "node")
     * Required for stdio type
     */
    @SerialName("command")
    val command: String? = null,

    /**
     * Command-line arguments
     * Used with stdio type
     */
    @SerialName("args")
    val args: List<String>? = null,

    /**
     * Environment variables for the process
     * Used with stdio type
     */
    @SerialName("env")
    val env: Map<String, String>? = null,

    // HTTP-specific configuration

    /**
     * Base URL for HTTP server
     * Required for http type
     */
    @SerialName("url")
    val url: String? = null,

    /**
     * HTTP headers (e.g., Authorization, API keys)
     * Used with http type
     */
    @SerialName("headers")
    val headers: Map<String, String>? = null,

    // Common configuration

    /**
     * Request timeout in milliseconds
     * Defaults to 30000 (30 seconds) if not specified
     */
    @SerialName("timeout")
    val timeout: Long? = null
) {
    /**
     * Validate configuration for stdio type
     */
    fun validateStdio(): String? {
        if (type != "stdio") return null
        if (command.isNullOrBlank()) {
            return "stdio server '$name' missing required 'command' field"
        }
        return null
    }

    /**
     * Validate configuration for HTTP type
     */
    fun validateHttp(): String? {
        if (type != "http") return null
        if (url.isNullOrBlank()) {
            return "http server '$name' missing required 'url' field"
        }
        return null
    }

    /**
     * Validate configuration based on type
     */
    fun validate(): String? {
        return when (type) {
            "stdio" -> validateStdio()
            "http" -> validateHttp()
            else -> "server '$name' has invalid type: '$type' (must be 'stdio' or 'http')"
        }
    }
}
