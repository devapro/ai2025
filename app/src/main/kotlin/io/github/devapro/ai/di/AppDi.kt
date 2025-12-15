package io.github.devapro.ai.di

import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.core.tools.ToolRegistry
import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.repository.FileRepository
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Simple configuration for MCP servers
 */
data class McpServerConfig(
    val name: String,
    val type: String,  // "stdio" or "sse"
    val command: String? = null,
    val args: List<String> = emptyList(),
    val url: String? = null
)

/**
 * Koin DI module that defines all application components
 */
val appModule = module {
    val logger = LoggerFactory.getLogger("AppDi")

    // Configuration
    single<Dotenv> {
        dotenv {
            ignoreIfMissing = true
        }
    }

    // Environment-based configuration properties
    single(qualifier = named("openAiApiKey")) {
        get<Dotenv>()["OPENAI_API_KEY"]
            ?: throw IllegalStateException("OPENAI_API_KEY environment variable is required")
    }

    single(qualifier = named("telegramBotToken")) {
        get<Dotenv>()["TELEGRAM_BOT_TOKEN"]
            ?: throw IllegalStateException("TELEGRAM_BOT_TOKEN environment variable is required")
    }

    single(qualifier = named("promptsDir")) {
        get<Dotenv>()["PROMPTS_DIR"] ?: "promts"
    }

    single(qualifier = named("historyDir")) {
        get<Dotenv>()["HISTORY_DIR"] ?: "history"
    }

    // MCP Server Configuration (optional, from environment variables)
    single(qualifier = named("mcpServers")) {
        val dotenv = get<Dotenv>()
        val servers = mutableListOf<McpServerConfig>()

        // Stdio server (filesystem or other stdio-based tools)
        dotenv["MCP_FILESYSTEM_COMMAND"]?.let { command ->
            val args = dotenv["MCP_FILESYSTEM_ARGS"]?.split(",")?.map { it.trim() } ?: emptyList()
            servers.add(McpServerConfig(
                name = "stdio-server",
                type = "stdio",
                command = command,
                args = args
            ))
            logger.info("Configured MCP stdio server: $command with args $args")
        }

        // HTTP/SSE server
        dotenv["MCP_HTTP_URL"]?.let { url ->
            servers.add(McpServerConfig(
                name = "http-server",
                type = "sse",
                url = url
            ))
            logger.info("Configured MCP HTTP server: $url")
        }

        if (servers.isEmpty()) {
            logger.info("No MCP servers configured. Agent will run without external tools.")
        } else {
            logger.info("Configured ${servers.size} MCP server(s)")
        }

        servers
    }

    // MCP Tool Registry (optional - only if servers are configured)
    single<ToolRegistry?> {
        val servers = get<List<McpServerConfig>>(qualifier = named("mcpServers"))

        if (servers.isEmpty()) {
            logger.info("No MCP tool registry - running without tools")
            return@single null
        }

        // Initialize MCP tool registries using Koog's McpToolRegistryProvider
        runBlocking {
            try {
                val registries = servers.mapNotNull { serverConfig ->
                    try {
                        when (serverConfig.type) {
                            "stdio" -> {
                                logger.info("Starting stdio MCP server: ${serverConfig.name}")
                                val command = listOf(serverConfig.command!!) + serverConfig.args
                                val process = ProcessBuilder(command).start()

                                // TODO: Fix MCP transport API - method signature unclear in Koog 0.5.4
                                // The defaultStdioTransport method doesn't exist or has different signature
                                // Need to investigate: agents-mcp-jvm API or use MCP Kotlin SDK directly
                                logger.error("MCP stdio transport not yet implemented - API investigation needed")
                                null
                            }
                            "sse" -> {
                                logger.info("Connecting to SSE MCP server: ${serverConfig.url}")
                                // TODO: Fix MCP transport API
                                logger.error("MCP SSE transport not yet implemented - API investigation needed")
                                null
                            }
                            else -> {
                                logger.error("Unknown transport type: ${serverConfig.type}")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to initialize MCP server ${serverConfig.name}: ${e.message}", e)
                        null
                    }
                }

                if (registries.isEmpty()) {
                    logger.warn("No MCP servers could be initialized")
                    null
                } else {
                    logger.info("Initialized ${registries.size} MCP tool registry(ies)")
                    // Use first registry (Koog doesn't have built-in registry combining)
                    // For multiple servers, would need custom ToolRegistry implementation
                    registries.first()
                }
            } catch (e: Exception) {
                logger.error("Error initializing MCP tools: ${e.message}", e)
                null
            }
        }
    }

    // Repository layer
    single {
        FileRepository(
            promptsDir = get(qualifier = named("promptsDir")),
            historyDir = get(qualifier = named("historyDir"))
        )
    }

    // Agent layer (with Koog)
    single {
        AiAgent(
            apiKey = get(qualifier = named("openAiApiKey")),
            fileRepository = get(),
            toolRegistry = get()
        )
    }

    // Bot layer
    single {
        TelegramBot(
            botToken = get(qualifier = named("telegramBotToken")),
            aiAgent = get()
        )
    }
}

/**
 * List of all Koin modules for the application
 */
val allModules = listOf(appModule)
