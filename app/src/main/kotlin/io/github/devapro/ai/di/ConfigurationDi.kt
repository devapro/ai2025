package io.github.devapro.ai.di

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin DI module for environment-based configuration
 * All configuration properties loaded from .env file
 */
val configurationModule = module {
    // Dotenv instance for loading environment variables
    single<Dotenv> {
        dotenv {
            ignoreIfMissing = true
        }
    }

    // ========================================
    // Core Configuration
    // ========================================

    single(qualifier = named("openAiApiKey")) {
        get<Dotenv>()["OPENAI_API_KEY"]
            ?: throw IllegalStateException("OPENAI_API_KEY environment variable is required")
    }

    single(qualifier = named("telegramBotToken")) {
        get<Dotenv>()["TELEGRAM_BOT_TOKEN"]
            ?: throw IllegalStateException("TELEGRAM_BOT_TOKEN environment variable is required")
    }

    // ========================================
    // Directory Configuration
    // ========================================

    single(qualifier = named("promptsDir")) {
        get<Dotenv>()["PROMPTS_DIR"] ?: "promts"
    }

    single(qualifier = named("historyDir")) {
        get<Dotenv>()["HISTORY_DIR"] ?: "history"
    }

    single(qualifier = named("usersFilePath")) {
        get<Dotenv>()["USERS_FILE_PATH"] ?: "users.md"
    }

    single(qualifier = named("projectSourceDir")) {
        get<Dotenv>()["PROJECT_SOURCE_DIR"] ?: "project-source"
    }

    // ========================================
    // GitHub Configuration
    // ========================================

    single(qualifier = named("githubToken")) {
        get<Dotenv>()["GITHUB_TOKEN"] // Optional - can be null
    }

    // ========================================
    // JIRA Configuration
    // ========================================

    single(qualifier = named("jiraUrl")) {
        get<Dotenv>()["JIRA_URL"] // Optional - can be null
    }

    single(qualifier = named("jiraEmail")) {
        get<Dotenv>()["JIRA_EMAIL"] // Optional - can be null
    }

    single(qualifier = named("jiraApiToken")) {
        get<Dotenv>()["JIRA_API_TOKEN"] // Optional - can be null
    }

    // ========================================
    // MCP Configuration
    // ========================================

    single(qualifier = named("mcpConfigPath")) {
        get<Dotenv>()["MCP_CONFIG_PATH"] ?: "mcp-config.json"
    }

    // ========================================
    // Daily Summary Scheduler Configuration
    // ========================================

    single(qualifier = named("dailySummaryHour")) {
        get<Dotenv>()["DAILY_SUMMARY_HOUR"]?.toIntOrNull() ?: 10
    }

    single(qualifier = named("dailySummaryMinute")) {
        get<Dotenv>()["DAILY_SUMMARY_MINUTE"]?.toIntOrNull() ?: 0
    }

    // ========================================
    // RAG Basic Configuration
    // ========================================

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

    // ========================================
    // Enhanced RAG Features Configuration
    // ========================================

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
}
