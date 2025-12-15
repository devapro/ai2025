package io.github.devapro.ai.mcp

/**
 * Configuration for a single MCP server
 *
 * @property name Server identifier (for logging)
 * @property type Transport type: "stdio" (local process) or "sse" (HTTP/SSE)
 * @property command Command to execute for stdio servers (e.g., "npx", "python")
 * @property args Command arguments for stdio servers
 * @property url Server URL for SSE servers
 */
data class McpServerConfig(
    val name: String,
    val type: String,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val url: String? = null
) {
    /**
     * Validate configuration
     */
    fun validate(): Boolean {
        return when (type) {
            "stdio" -> !command.isNullOrBlank()
            "sse" -> !url.isNullOrBlank()
            else -> false
        }
    }

    /**
     * Human-readable description for logging
     */
    fun describe(): String {
        return when (type) {
            "stdio" -> "$name: stdio '$command ${args.joinToString(" ")}'"
            "sse" -> "$name: sse '$url'"
            else -> "$name: unknown type '$type'"
        }
    }
}
