package io.github.devapro.ai.di

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import io.github.devapro.ai.AppShutDownManager
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.agent.AiAgentConversationSummarizer
import io.github.devapro.ai.agent.AiAgentResponseFormatter
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.mcp.McpManager
import io.github.devapro.ai.mcp.config.McpConfigLoader
import io.github.devapro.ai.repository.FileRepository
import io.github.devapro.ai.scheduler.DailySummaryScheduler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
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

    single(qualifier = named("usersFilePath")) {
        get<Dotenv>()["USERS_FILE_PATH"] ?: "users.md"
    }

    single(qualifier = named("dailySummaryHour")) {
        get<Dotenv>()["DAILY_SUMMARY_HOUR"]?.toIntOrNull() ?: 10
    }

    single(qualifier = named("dailySummaryMinute")) {
        get<Dotenv>()["DAILY_SUMMARY_MINUTE"]?.toIntOrNull() ?: 0
    }

    // Shared HTTP client (used by both AiAgent and MCP)
    single {
        HttpClient(CIO) {
            // CIO engine configuration for SSE support
            engine {
                endpoint {
                    connectTimeout = 10_000  // 10 seconds to establish connection
                    connectAttempts = 3
                }
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    encodeDefaults = true  // Include default values like jsonrpc = "2.0"
                    explicitNulls = false  // Omit null fields (critical for JSON-RPC notifications)
                })
            }

            // Install SSE plugin for MCP HTTP transport (SDK requirement)
            install(SSE) {
                showCommentEvents()
                showRetryEvents()
            }

            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 520_000  // 520 seconds for long-running requests
                connectTimeoutMillis = 10_000   // 10 seconds to establish connection
                socketTimeoutMillis = 520_000   // 520 seconds for socket operations
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
            historyDir = get(qualifier = named("historyDir")),
            usersFilePath = get(qualifier = named("usersFilePath"))
        )
    }

    // Conversation summarizer component
    single {
        AiAgentConversationSummarizer(
            apiKey = get(qualifier = named("openAiApiKey")),
            fileRepository = get(),
            httpClient = get()
        )
    }

    // Response formatter component
    single {
        AiAgentResponseFormatter()
    }

    // Agent layer (now with MCP support)
    single {
        AiAgent(
            apiKey = get(qualifier = named("openAiApiKey")),
            fileRepository = get(),
            mcpManager = get(),
            httpClient = get(),
            conversationSummarizer = get(),
            responseFormatter = get()
        )
    }

    // Bot layer
    single {
        TelegramBot(
            botToken = get(qualifier = named("telegramBotToken")),
            aiAgent = get()
        )
    }

    // Scheduler layer
    single {
        DailySummaryScheduler(
            aiAgent = get(),
            fileRepository = get(),
            bot = get<TelegramBot>().bot,
            targetHour = get(qualifier = named("dailySummaryHour")),
            targetMinute = get(qualifier = named("dailySummaryMinute"))
        )
    }

    // Shutdown manager
    single {
        AppShutDownManager(
            dailySummaryScheduler = get(),
            mcpManager = get(),
            telegramBot = get(),
            aiAgent = get(),
            httpClient = get()
        )
    }
}

/**
 * List of all Koin modules for the application
 */
val allModules = listOf(appModule)
