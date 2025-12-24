package io.github.devapro.ai.di

import io.github.devapro.ai.AppShutDownManager
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.agent.AiAgentConversationSummarizer
import io.github.devapro.ai.agent.AiAgentResponseFormatter
import io.github.devapro.ai.agent.ContextCompressor
import io.github.devapro.ai.agent.EnhancedRagSearchTool
import io.github.devapro.ai.agent.QueryExpander
import io.github.devapro.ai.agent.RagResultsRefiner
import io.github.devapro.ai.agent.RagSearchTool
import io.github.devapro.ai.agent.RagSearchToolInterface
import io.github.devapro.ai.agent.TokenCounter
import io.github.devapro.ai.agent.ToolProvider
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.mcp.McpManager
import io.github.devapro.ai.mcp.config.McpConfigLoader
import io.github.devapro.ai.repository.FileRepository
import io.github.devapro.ai.scheduler.DailySummaryScheduler
import io.github.devapro.ai.utils.rag.EmbeddingGenerator
import io.github.devapro.ai.utils.rag.VectorDatabase
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
 * Note: Configuration properties are defined in ConfigurationDi.kt
 */
val appModule = module {
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

    // RAG components (optional, only if RAG is enabled)
    single<VectorDatabase?> {
        val dbPath: String = get(qualifier = named("ragDatabasePath"))
        VectorDatabase(dbPath)
    }

    single<EmbeddingGenerator> {
        val apiKey: String = get(qualifier = named("openAiApiKey"))
        val apiUrl: String = get(qualifier = named("ragEmbeddingApiUrl"))
        val model: String = get(qualifier = named("ragEmbeddingModel"))
        EmbeddingGenerator(
            apiKey = apiKey,
            httpClient = get(),
            model = model,
            apiUrl = apiUrl
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

    // Token counter component
    single {
        TokenCounter()
    }

    // Enhanced RAG components (optional, only if enhanced mode is enabled)

    // Query expander for RAG
    single<QueryExpander> {
        QueryExpander(
            apiKey = get(qualifier = named("openAiApiKey")),
            httpClient = get(),
            model = get(qualifier = named("ragLlmModel"))
        )
    }

    // Results refiner for RAG re-ranking
    single<RagResultsRefiner> {
        RagResultsRefiner(
            apiKey = get(qualifier = named("openAiApiKey")),
            httpClient = get(),
            model = get(qualifier = named("ragLlmModel"))
        )
    }

    // Context compressor for RAG
    single<ContextCompressor> {
        ContextCompressor(
            apiKey = get(qualifier = named("openAiApiKey")),
            httpClient = get(),
            tokenCounter = get(),
            model = get(qualifier = named("ragLlmModel"))
        )
    }

    // RAG search tool component (basic or enhanced based on config)
    single<RagSearchToolInterface> {
        val enhancedMode: Boolean = get(qualifier = named("ragEnhancedMode"))

        val vectorDatabase: VectorDatabase = get()
        val embeddingGenerator: EmbeddingGenerator = get()

        // Return enhanced or basic tool based on configuration
        if (enhancedMode) {
            EnhancedRagSearchTool(
                vectorDatabase = vectorDatabase,
                embeddingGenerator = embeddingGenerator,
                resultsRefiner = get(),
                queryExpander = get(),
                contextCompressor = get(),
                ragTopK = get(qualifier = named("ragTopK")),
                ragMinSimilarity = get(qualifier = named("ragMinSimilarity")),
                finalTopK = get(qualifier = named("ragFinalTopK")),
                enableQueryExpansion = get(qualifier = named("ragEnableQueryExpansion")),
                enableReranking = get(qualifier = named("ragEnableReranking")),
                enableCompression = get(qualifier = named("ragEnableCompression"))
            )
        } else {
            RagSearchTool(
                vectorDatabase = vectorDatabase,
                embeddingGenerator = embeddingGenerator,
                ragTopK = get(qualifier = named("ragTopK")),
                ragMinSimilarity = get(qualifier = named("ragMinSimilarity"))
            )
        }
    }

    // Tool provider component
    single {
        ToolProvider(
            mcpManager = get(),
            ragSearchTool = get(),
            ragEnabled = get(qualifier = named("ragEnabled"))
        )
    }

    // Agent layer (now with MCP and RAG support)
    single {
        AiAgent(
            apiKey = get(qualifier = named("openAiApiKey")),
            fileRepository = get(),
            mcpManager = get(),
            httpClient = get(),
            conversationSummarizer = get(),
            responseFormatter = get(),
            tokenCounter = get(),
            toolProvider = get(),
            ragSearchTool = get()
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
 * Order matters: configurationModule must be loaded first as appModule depends on it
 */
val allModules = listOf(configurationModule, appModule)
