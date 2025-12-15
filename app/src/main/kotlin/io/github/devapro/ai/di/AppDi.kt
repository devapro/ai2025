package io.github.devapro.ai.di

import ai.koog.agents.core.tools.ToolRegistry
import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.bot.TelegramBot
import io.github.devapro.ai.history.HistoryManager
import io.github.devapro.ai.mcp.McpConfigProvider
import io.github.devapro.ai.mcp.McpInitializer
import io.github.devapro.ai.repository.FileRepository
import kotlinx.coroutines.runBlocking
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

    // MCP Configuration Provider
    single {
        McpConfigProvider(dotenv = get())
    }

    // MCP Initializer component
    single {
        val configProvider = get<McpConfigProvider>()
        McpInitializer(
            serverConfigs = configProvider.loadConfigurations()
        )
    }

    // Repository layer
    single {
        FileRepository(
            promptsDir = get(qualifier = named("promptsDir")),
            historyDir = get(qualifier = named("historyDir"))
        )
    }

    // History Manager
    single {
        HistoryManager(
            apiKey = get(qualifier = named("openAiApiKey")),
            fileRepository = get(),
            summarizationThreshold = 10
        )
    }

    // Agent layer (with Koog)
    single {
        AiAgent(
            apiKey = get(qualifier = named("openAiApiKey")),
            fileRepository = get(),
            historyManager = get(),
            mcpInitializer = get()
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
