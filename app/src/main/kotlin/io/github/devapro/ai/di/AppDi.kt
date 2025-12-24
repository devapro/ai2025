package io.github.devapro.ai.di

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
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

    // RAG configuration

    single(qualifier = named("ragEnabled")) {
        get<Dotenv>()["RAG_ENABLED"]?.toBoolean() ?: false
    }

    single(qualifier = named("ragDatabasePath")) {
        get<Dotenv>()["RAG_DATABASE_PATH"] ?: "embeddings.db"
    }

    single(qualifier = named("ragEmbeddingApiUrl")) {
        get<Dotenv>()["RAG_EMBEDDING_API_URL"] ?: "http://127.0.0.1:1234/v1/embeddings"
    }

    single(qualifier = named("ragEmbeddingModel")) {
        get<Dotenv>()["RAG_EMBEDDING_MODEL"] ?: "text-embedding-nomic-embed-text-v1.5"
    }

    single(qualifier = named("ragTopK")) {
        get<Dotenv>()["RAG_TOP_K"]?.toIntOrNull() ?: 10
    }

    single(qualifier = named("ragFinalTopK")) {
        get<Dotenv>()["RAG_FINAL_TOP_K"]?.toIntOrNull() ?: 3
    }

    single(qualifier = named("ragMinSimilarity")) {
        get<Dotenv>()["RAG_MIN_SIMILARITY"]?.toDoubleOrNull() ?: 0.6
    }

    // Enhanced RAG features configuration
    single(qualifier = named("ragEnhancedMode")) {
        get<Dotenv>()["RAG_ENHANCED_MODE"]?.toBoolean() ?: true
    }

    single(qualifier = named("ragEnableQueryExpansion")) {
        get<Dotenv>()["RAG_ENABLE_QUERY_EXPANSION"]?.toBoolean() ?: true
    }

    single(qualifier = named("ragEnableReranking")) {
        get<Dotenv>()["RAG_ENABLE_RERANKING"]?.toBoolean() ?: true
    }

    single(qualifier = named("ragEnableCompression")) {
        get<Dotenv>()["RAG_ENABLE_COMPRESSION"]?.toBoolean() ?: true
    }

    single(qualifier = named("ragLlmModel")) {
        get<Dotenv>()["RAG_LLM_MODEL"] ?: "gpt-4o-mini"
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
    single<QueryExpander?> {
        val ragEnabled: Boolean = get(qualifier = named("ragEnabled"))
        val enhancedMode: Boolean = get(qualifier = named("ragEnhancedMode"))
        if (ragEnabled && enhancedMode) {
            QueryExpander(
                apiKey = get(qualifier = named("openAiApiKey")),
                httpClient = get(),
                model = get(qualifier = named("ragLlmModel"))
            )
        } else null
    }

    // Results refiner for RAG re-ranking
    single<RagResultsRefiner?> {
        val ragEnabled: Boolean = get(qualifier = named("ragEnabled"))
        val enhancedMode: Boolean = get(qualifier = named("ragEnhancedMode"))
        if (ragEnabled && enhancedMode) {
            RagResultsRefiner(
                apiKey = get(qualifier = named("openAiApiKey")),
                httpClient = get(),
                model = get(qualifier = named("ragLlmModel"))
            )
        } else null
    }

    // Context compressor for RAG
    single<ContextCompressor?> {
        val ragEnabled: Boolean = get(qualifier = named("ragEnabled"))
        val enhancedMode: Boolean = get(qualifier = named("ragEnhancedMode"))
        if (ragEnabled && enhancedMode) {
            ContextCompressor(
                apiKey = get(qualifier = named("openAiApiKey")),
                httpClient = get(),
                tokenCounter = get(),
                model = get(qualifier = named("ragLlmModel"))
            )
        } else null
    }

    // RAG search tool component (basic or enhanced based on config)
    single<RagSearchToolInterface?> {
        val ragEnabled: Boolean = get(qualifier = named("ragEnabled"))
        val enhancedMode: Boolean = get(qualifier = named("ragEnhancedMode"))

        if (!ragEnabled) return@single null

        val vectorDatabase: VectorDatabase? = get()
        val embeddingGenerator: EmbeddingGenerator = get()

        if (vectorDatabase == null) return@single null

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
 */
val allModules = listOf(appModule)
