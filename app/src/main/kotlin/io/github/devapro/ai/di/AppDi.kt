package io.github.devapro.ai.di

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.mcp.McpManager
import io.github.devapro.ai.mcp.config.McpConfigLoader
import io.github.devapro.ai.repository.FileRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin DI module that defines all application components
 */
val appModule = module {
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

    single(qualifier = named("mcpConfigPath")) {
        get<Dotenv>()["MCP_CONFIG_PATH"] ?: "mcp-config.json"
    }

    // Shared HTTP client (used by both AiAgent and MCP)
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    encodeDefaults = true  // Include default values like jsonrpc = "2.0"
                    explicitNulls = false  // Omit null fields (critical for JSON-RPC notifications)
                })
            }
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 520_000
            }
        }
    }

    // MCP config loader
    single {
        McpConfigLoader(
            configPath = get(qualifier = named("mcpConfigPath"))
        )
    }

    // MCP config (loaded once at startup)
    single {
        val configLoader = get<McpConfigLoader>()
        configLoader.loadConfig()
    }

    // MCP manager
    single {
        McpManager(
            config = get(),
            httpClient = get()
        )
    }

    // Repository layer
    single {
        FileRepository(
            promptsDir = get(qualifier = named("promptsDir")),
            historyDir = get(qualifier = named("historyDir"))
        )
    }

    // Agent layer (now with MCP support)
    single {
        AiAgent(
            apiKey = get(qualifier = named("openAiApiKey")),
            fileRepository = get(),
            mcpManager = get(),
            httpClient = get()
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
