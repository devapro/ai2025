# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System

This is a Gradle-based Kotlin JVM project using the Gradle Wrapper. Always use `./gradlew` (not `gradle`) for commands.

**Core commands:**
- `./gradlew run` - Build and run the Telegram bot application
- `./gradlew :utils:run` - Run the RAG utilities for document indexing
- `./gradlew build` - Build the project (includes fat JAR creation)
- `./gradlew test` - Run all tests
- `./gradlew :app:test` / `./gradlew :utils:test` - Run tests for specific module
- `./gradlew clean` - Clean build outputs

**RAG utilities:**
- `./gradlew :utils:run` - Index embeddings.md to embeddings.db (default)
- `./gradlew :utils:run --args="input.md output.db"` - Index custom file
- `./gradlew :utils:run --args="docs.md db.db 800 150"` - Custom chunk size and overlap
- Requires LM Studio running at http://127.0.0.1:1234 with text-embedding-nomic-embed-text-v1.5 model

**Docker:**
- `cd docker && docker-compose up --build` - Build and run
- `docker-compose logs -f` - View logs
- `docker-compose down` - Stop container

## Configuration

The application uses environment variables from a `.env` file (copy from `.env.example`):

**Required:**
- `OPENAI_API_KEY` - OpenAI API key for AI conversations
- `TELEGRAM_BOT_TOKEN` - Bot token from @BotFather

**Optional:**
- `PROMPTS_DIR` - Prompt files directory (default: `promts`)
- `HISTORY_DIR` - Conversation history directory (default: `history`)
- `MCP_CONFIG_PATH` - MCP configuration file (default: `mcp-config.json`)
- `RAG_ENABLED` - Enable RAG document search (default: false)
- `RAG_DATABASE_PATH` - Vector database path (default: `embeddings.db`)
- `RAG_EMBEDDING_API_URL` - LM Studio endpoint (default: http://127.0.0.1:1234/v1/embeddings)
- `RAG_TOP_K` - Search results count (default: 5)
- `RAG_MIN_SIMILARITY` - Similarity threshold (default: 0.7)

**Important files:**
- `.env` - Configuration with secrets (git-ignored)
- `promts/system.md` - System prompt for AI agent
- `promts/assistant.md` - Initial greeting message
- `history/` - User conversation history (git-ignored, auto-created)
- `mcp-config.json` - MCP server configuration (git-ignored)
- `embeddings.db` - Vector database for RAG (git-ignored)

## Project Architecture

AI Assistant Telegram Bot with modular architecture:

**Module structure:**
- `app/` - Main application module
  - Entry point: `io.github.devapro.ai.AppKt`
  - Depends on `utils` module
- `utils/` - RAG components and utilities
  - Entry point: `io.github.devapro.ai.utils.UtilAppKt`
  - Standalone document indexing application
- `buildSrc/` - Convention plugins for shared build logic
  - Java 21 toolchain, JUnit Platform, test logging

**App module components:**
- `di/AppDi.kt` - Koin dependency injection configuration
- `bot/TelegramBot.kt` - Telegram API integration, commands (/start, /help, /clear)
- `agent/AiAgent.kt` - OpenAI API integration with MCP and RAG support
  - Function calling for tool execution
  - Multi-turn conversations (max 20 iterations)
  - Response formatting with markdown and statistics
- `mcp/` - Model Context Protocol integration
  - Multiple transports: Stdio (npx/python), SSE (HTTP), HttpTransport (MCP SDK)
  - Multi-server orchestrator with parallel initialization
  - Tool discovery, caching, and execution
  - See `MCP_GUIDE.md` for detailed setup
- `repository/FileRepository.kt` - Prompts and history management
- `scheduler/DailySummaryScheduler.kt` - Daily conversation summaries

**Utils module components:**
- `rag/TextChunker.kt` - Smart text chunking (sentence-first + Markdown-aware)
- `rag/MarkdownParser.kt` - Markdown structure parsing
- `rag/EmbeddingGenerator.kt` - Vector embeddings via LM Studio
- `rag/VectorDatabase.kt` - SQLite vector storage with cosine similarity
- See `RAG_IMPLEMENTATION.md` for detailed architecture

**Key patterns:**
- Dependency Injection via Koin (all components are singletons)
- Shared Ktor HTTP client for OpenAI and MCP
- File-based storage for prompts/history (JSON format)
- Graceful degradation (works without MCP/RAG if not configured)
- Coroutines for async operations

## Technology Stack

**Core:**
- Kotlin 2.2.0, Java 21 toolchain, JUnit Platform
- kotlinx-serialization-json 1.7.3, kotlinx-coroutines 1.9.0

**Key libraries:**
- kotlin-telegram-bot 6.3.0 - Telegram Bot API
- Ktor 3.3.0 - HTTP client with SSE support
- OpenAI API - gpt-4o-mini via Ktor (custom integration)
- MCP Kotlin SDK 0.8.1 - Model Context Protocol
- Koin 4.0.0 - Dependency injection
- Exposed 0.57.0 + SQLite 3.47.1.0 - RAG vector storage
- jtokkit 1.1.0 - Token counting for GPT models
- dotenv-kotlin 6.5.1 - Environment variables
- SLF4J Simple 2.0.16 - Logging

**Local embeddings:**
- LM Studio with text-embedding-nomic-embed-text-v1.5
- 1536-dimensional embeddings, no API costs

## Application Flow

1. **Startup** - Koin initializes all components from `.env`, starts bot polling
2. **Message handling** - TelegramBot receives message → AiAgent processes → Response sent
3. **AI processing** - Load history → Build messages → Call OpenAI (with tools if available) → Format response → Save history
4. **Tool calling** - If OpenAI requests tool use → Execute via McpManager or RAG → Return result → Continue conversation

## MCP Integration

Model Context Protocol enables external tool integration. The bot supports three transport types:

**Transports:**
- **StdioTransport** - Local processes (npx, python) via stdin/stdout
- **SseTransport** - HTTP servers using Server-Sent Events (GET /sse + POST /message)
- **HttpTransport** - Official MCP Kotlin SDK (single POST endpoint)

**Configuration** (`mcp-config.json`):
```json
{
  "mcpServers": [
    {
      "name": "filesystem",
      "enabled": true,
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path"]
    }
  ]
}
```

**Features:**
- Parallel server initialization
- Tool discovery and caching
- Automatic tool routing to correct server
- Graceful degradation if servers fail

See `MCP_GUIDE.md` for comprehensive setup instructions.

## RAG Integration

Retrieval-Augmented Generation enables semantic document search as a built-in tool.

**Workflow:**
1. Index documents: `./gradlew :utils:run --args="docs.md embeddings.db"`
2. Enable in `.env`: `RAG_ENABLED=true`
3. Start bot with LM Studio running
4. AI automatically uses `search_documents` tool when helpful

**Features:**
- Sentence-first text chunking for semantic coherence
- Markdown-aware chunking (preserves structure, never splits code blocks)
- Local embeddings via LM Studio (no API costs)
- SQLite vector storage with cosine similarity
- Configurable top-K results and similarity threshold

**Example:**
```
User: How do I authenticate users?
[Bot searches embeddings.db, finds relevant chunks]
Bot: Based on the documentation, authentication is handled using...
```

See `RAG_IMPLEMENTATION.md` for detailed architecture and `MARKDOWN_CHUNKING.md` for chunking strategies.

## Development Notes

**Adding dependencies:**
- Update `gradle/libs.versions.toml` first
- Reference with `libs.` notation (e.g., `implementation(libs.ktor.client.core)`)

**File organization:**
- Entry points: `app/src/main/kotlin/io/github/devapro/ai/App.kt`, `utils/src/main/kotlin/io/github/devapro/ai/utils/UtilApp.kt`
- DI config: `app/src/main/kotlin/io/github/devapro/ai/di/AppDi.kt`
- Components in packages: `agent/`, `bot/`, `mcp/`, `repository/`, `scheduler/`
- RAG components: `utils/src/main/kotlin/io/github/devapro/ai/utils/rag/`
- Tests mirror source structure

**Coding standards:**
- Use `@Serializable` for JSON data classes
- Use coroutines for async operations (`suspend` functions)
- Use SLF4J logger: `LoggerFactory.getLogger(ClassName::class.java)`
- Define dependencies in `AppDi.kt` using Koin DSL
- Constructor injection for all components

**Conversation history format:**
- Stored as JSON: `history/user_{userId}.json`
- Contains messages array with role, content, timestamp

**Prompts:**
- `promts/system.md` - Instructs AI to return JSON with answer format
- `promts/assistant.md` - Initial greeting shown to users
- Changes take effect immediately (volume-mounted in Docker)
- System prompt includes RAG search instructions when enabled

**Debugging:**
- Check logs: `docker-compose logs -f` or console output
- Verify `.env` exists with correct values
- Ensure OpenAI API key is valid
- For RAG: Verify LM Studio running and embeddings.db exists
- Koin logs DI resolution during startup

**Docker:**
- Multi-stage build (Gradle builder + JRE runtime)
- Volumes: `/app/promts`, `/app/history`
- Environment from `.env` file

**Documentation:**
- `README.md` - User setup and usage guide
- `MCP_GUIDE.md` - MCP setup and configuration
- `RAG_IMPLEMENTATION.md` - Complete RAG architecture
- `MARKDOWN_CHUNKING.md` - Chunking strategies
- `TEXTCHUNKER_IMPROVEMENTS.md` - Sentence-first strategy
- `DI_IMPLEMENTATION.md` - Dependency injection details
- `IMPLEMENTATION_SUMMARY.md` - Architecture decisions
- `HTTP_TRANSPORT_SDK_IMPLEMENTATION.md` - MCP SDK details
- `KOTLIN_SDK_RESEARCH.md` - MCP SDK research notes
- `results.md` - AI model benchmarks

## Dependency Injection (Koin)

All components are defined in `AppDi.kt` as singletons:

**Pattern:**
```kotlin
val appModule = module {
    // Configuration from .env
    single<Dotenv> { dotenv { ignoreIfMissing = true } }

    // Named config values with validation
    single(named("openAiApiKey")) {
        get<Dotenv>()["OPENAI_API_KEY"]
            ?: throw IllegalStateException("OPENAI_API_KEY required")
    }

    // Components with constructor injection
    single { FileRepository(get(named("promptsDir")), get(named("historyDir"))) }
    single { AiAgent(get(named("openAiApiKey")), get(), get(), get(), get(), get()) }
    single { TelegramBot(get(named("telegramBotToken")), get()) }
}
```

**Benefits:**
- Automatic dependency resolution and lifecycle management
- Fail-fast on missing configuration
- Clear dependency graph
- Easy testing with mock modules
