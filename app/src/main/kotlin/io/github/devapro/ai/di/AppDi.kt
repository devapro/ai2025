package io.github.devapro.ai.di

import io.github.devapro.ai.AppShutDownManager
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.agent.AiAgentConversationSummarizer
import io.github.devapro.ai.agent.AiAgentResponseFormatter
import io.github.devapro.ai.agent.ToolProvider
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.tools.rag.RagSearchToolInterface
import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.tools.CodeSearchTool
import io.github.devapro.ai.tools.tools.DocumentWriterTool
import io.github.devapro.ai.tools.tools.ExploringTool
import io.github.devapro.ai.tools.tools.FindFileTool
import io.github.devapro.ai.tools.tools.FolderStructureTool
import io.github.devapro.ai.tools.tools.GitHubTool
import io.github.devapro.ai.tools.tools.GitOperationTool
import io.github.devapro.ai.tools.tools.JiraTool
import io.github.devapro.ai.tools.tools.ReadFileTool
import io.github.devapro.ai.tools.rag.ContextCompressor
import io.github.devapro.ai.tools.rag.EnhancedRagSearchTool
import io.github.devapro.ai.tools.rag.HybridSearchTool
import io.github.devapro.ai.tools.rag.QueryExpander
import io.github.devapro.ai.tools.rag.RagResultsRefiner
import io.github.devapro.ai.tools.rag.RagSearchTool
import io.github.devapro.ai.tools.rag.TokenCounter
import io.github.devapro.ai.mcp.McpManager
import io.github.devapro.ai.mcp.config.McpConfigLoader
import io.github.devapro.ai.repository.FileRepository
import io.github.devapro.ai.scheduler.DailySummaryScheduler
import io.github.devapro.ai.embeds.rag.EmbeddingGenerator
import io.github.devapro.ai.embeds.rag.VectorDatabase
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

    // User profile repository
    single {
        io.github.devapro.ai.repository.UserProfileRepository(
            profilesDir = get(qualifier = named("profilesDir"))
        )
    }

    // Profile interviewer
    single {
        io.github.devapro.ai.agent.ProfileInterviewer(
            apiKey = get(qualifier = named("openAiApiKey")),
            httpClient = get(),
            profileRepository = get()
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

    // RAG search tool component (hybrid search - combines vector + text matching)
    single<RagSearchToolInterface> {
        val vectorDatabase: VectorDatabase = get()
        val embeddingGenerator: EmbeddingGenerator = get()

        // Use hybrid search by default (best of both worlds: semantic + keyword)
        HybridSearchTool(
            vectorDatabase = vectorDatabase,
            embeddingGenerator = embeddingGenerator,
            ragTopK = get(qualifier = named("ragTopK")),
            ragMinSimilarity = 0.5,  // Lower threshold since we have text matching fallback
            enableTextSearch = true  // Enable exact text matching
        )
    }

    // Internal tools (integrated in code, not via external MCP)
    single {
        val ragEnabled: Boolean = get(qualifier = named("ragEnabled"))
        val tools = mutableListOf<Tool>()

        // Project source directory - root for all file operations
        val projectSourcePath: String = get(qualifier = named("projectSourceDir"))
        val projectSourceDir = java.io.File(System.getProperty("user.dir"), projectSourcePath)

        // Add RAG search tool if enabled
        if (ragEnabled) {
            tools.add(get<RagSearchToolInterface>())
        }

        // Add file tools (always available) - all use project-source as working directory
        val docSourceDir = java.io.File(System.getProperty("user.dir"), "doc-source")
        tools.add(FindFileTool(workingDirectory = projectSourceDir))
        tools.add(ReadFileTool(
            projectSourceDirectory = projectSourceDir,
            documentSourceDirectory = docSourceDir
        ))
        tools.add(CodeSearchTool(workingDirectory = projectSourceDir))
        tools.add(FolderStructureTool(workingDirectory = projectSourceDir))
        tools.add(ExploringTool(
            apiKey = get(qualifier = named("openAiApiKey")),
            httpClient = get(),
            workingDirectory = projectSourceDir
        ))
        tools.add(DocumentWriterTool())  // Uses doc-source, not project-source
        tools.add(GitOperationTool(workingDirectory = projectSourceDir))  // Git operations for PR review
        tools.add(GitHubTool(
            httpClient = get(),
            githubToken = get(qualifier = named("githubToken"))
        ))  // GitHub API for PR details
        tools.add(JiraTool(
            httpClient = get(),
            jiraUrl = get(qualifier = named("jiraUrl")),
            jiraEmail = get(qualifier = named("jiraEmail")),
            jiraToken = get(qualifier = named("jiraApiToken"))
        ))  // JIRA API for issue details

        tools
    }

    // Tool provider component
    single {
        ToolProvider(
            internalTools = get(),
            mcpManager = get()
        )
    }

    // Agent layer (now with MCP and internal tools support)
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
            profileRepository = get()
        )
    }

    // Bot layer
    single {
        TelegramBot(
            botToken = get(qualifier = named("telegramBotToken")),
            aiAgent = get(),
            profileRepository = get(),
            profileInterviewer = get()
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
