package io.github.devapro.ai.mcp.config

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Loads MCP configuration from JSON file
 *
 * Provides graceful degradation if the config file is missing or invalid.
 * The application will continue to work without MCP tools in that case.
 *
 * @param configPath Path to the configuration file (default: "mcp-config.json")
 */
class McpConfigLoader(
    private val configPath: String = "mcp-config.json"
) {
    private val logger = LoggerFactory.getLogger(McpConfigLoader::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    /**
     * Load MCP configuration from file
     *
     * Returns empty config if:
     * - File doesn't exist
     * - File is empty
     * - JSON parsing fails
     * - Configuration validation fails
     *
     * @return McpConfig instance (may be empty)
     */
    fun loadConfig(): McpConfig {
        try {
            val configFile = File(configPath)

            // Check if file exists
            if (!configFile.exists()) {
                logger.info("MCP config file not found: $configPath")
                logger.info("No MCP servers will be available. Agent will work without tools.")
                return McpConfig(mcpServers = emptyList())
            }

            // Read file content
            val content = configFile.readText()
            if (content.isBlank()) {
                logger.warn("MCP config file is empty: $configPath")
                return McpConfig(mcpServers = emptyList())
            }

            // Parse JSON
            val config = json.decodeFromString<McpConfig>(content)

            // Validate each server config
            val validServers = mutableListOf<McpServerConfig>()
            config.mcpServers.forEach { serverConfig ->
                val validationError = serverConfig.validate()
                if (validationError != null) {
                    logger.error("Invalid MCP server configuration: $validationError")
                } else {
                    validServers.add(serverConfig)
                    logger.info("Loaded MCP server config: ${serverConfig.name} (${serverConfig.type})")
                }
            }

            val validConfig = McpConfig(mcpServers = validServers)

            if (validServers.isEmpty()) {
                logger.warn("No valid MCP servers found in config file")
            } else {
                logger.info("Loaded ${validServers.size} MCP server configuration(s)")
            }

            return validConfig

        } catch (e: Exception) {
            logger.error("Failed to load MCP config from $configPath: ${e.message}", e)
            logger.info("Agent will work without tools.")
            return McpConfig(mcpServers = emptyList())
        }
    }
}
