# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System

This is a Gradle-based Kotlin JVM project using the Gradle Wrapper. Always use `./gradlew` (not `gradle`) for commands.

**Core commands:**
- `./gradlew run` - Build and run the Telegram bot application
- `./gradlew build` - Build the project (includes fat JAR creation)
- `./gradlew check` - Run all checks including tests
- `./gradlew test` - Run tests only
- `./gradlew clean` - Clean build outputs
- `./gradlew jar` - Create fat JAR with all dependencies in `app/build/libs/`

**Test commands:**
- `./gradlew test` - Run all tests
- `./gradlew :utils:test` - Run tests for utils module only
- `./gradlew :app:test` - Run tests for app module only

**Docker commands:**
- `cd docker && docker-compose up --build` - Build and run in Docker
- `docker-compose logs -f` - View logs
- `docker-compose down` - Stop the container

## Configuration

The application uses environment variables from a `.env` file:

**Setup:**
1. Copy `.env.example` to `.env`: `cp .env.example .env`
2. Fill in required values in `.env`

**Required variables:**
- `OPENAI_API_KEY` - OpenAI API key for AI-powered conversations
- `TELEGRAM_BOT_TOKEN` - Telegram bot token from @BotFather

**Optional variables:**
- `PROMPTS_DIR` - Directory for prompt files (default: `promts`)
- `HISTORY_DIR` - Directory for conversation history (default: `history`)
- `MCP_CONFIG_PATH` - Path to MCP configuration file (default: `mcp-config.json`)

**Important files:**
- `.env` - Configuration file with secrets (git-ignored)
- `promts/system.md` - System prompt for AI agent (customizable)
- `promts/assistant.md` - Assistant prompt shown at start of conversation (customizable)
- `history/` - User conversation history (git-ignored, auto-created)
- `mcp-config.json` - MCP server configuration (git-ignored, contains API keys/credentials)
- `mcp-config.json.example` - Example MCP configuration file

## Project Architecture

This is an AI Assistant Telegram Bot implemented with a modular architecture:

**Module structure:**
- `app/` - Main application module containing all bot logic
  - Entry point: `io.github.devapro.ai.AppKt` (compiled from `App.kt`)
  - Main class: `io.github.devapro.ai.main()`
  - Depends on: `utils` module
- `utils/` - Shared utilities module
- `buildSrc/` - Convention plugins for shared build logic
  - `kotlin-jvm.gradle.kts` - Shared Kotlin JVM configuration
  - Configures: Java 21 toolchain, test logging with JUnit Platform
  - Applied to both `app` and `utils` modules via `buildsrc.convention.kotlin-jvm` plugin

**Component architecture (within app module):**
- `di/` - Dependency Injection configuration
  - `AppDi.kt` - Koin module definitions for all components
  - Manages component lifecycle and dependencies
  - Provides configuration values from environment variables
- `bot/` - Telegram Bot component
  - `TelegramBot.kt` - Handles Telegram API, user commands, message routing
  - Commands: /start, /help, /clear
  - Uses kotlin-telegram-bot library for Telegram integration
  - **Markdown support**: All messages sent with ParseMode.MARKDOWN
  - Injected as singleton via Koin
- `agent/` - AI Agent component
  - `AiAgent.kt` - Manages AI conversations using OpenAI API with MCP tool support
  - Uses shared Ktor HTTP client for API communication
  - Handles conversation context and history
  - **MCP Tool Integration**: Automatically uses external tools when needed
  - **Function Calling**: Implements OpenAI function calling for tool execution
  - **Multi-turn conversations**: Handles tool calls across multiple API requests (max 5 iterations)
  - **Response format**: Answer response with fields:
    - `type`: Always "answer"
    - `text`: Full answer text with markdown formatting
    - `summary`: Optional one-line summary
  - Parses JSON responses and formats with markdown
  - **General AI Assistant**: Answers questions, provides explanations, offers advice
  - **Performance tracking**: Measures response time and token usage
  - Includes data classes for OpenAI request/response serialization
  - Injected as singleton via Koin with dependencies: apiKey, FileRepository, McpManager, HttpClient
- `mcp/` - Model Context Protocol integration
  - `model/McpModels.kt` - JSON-RPC and MCP protocol data structures
  - `transport/McpTransport.kt` - Transport interface for stdio and HTTP
  - `transport/StdioTransport.kt` - Local process transport (npx, python scripts)
  - `transport/HttpTransport.kt` - Remote HTTP server transport
  - `client/McpClient.kt` - Single server client with tool caching and execution
  - `config/McpConfig.kt` - Configuration data classes
  - `config/McpConfigLoader.kt` - JSON config file loader
  - `McpManager.kt` - Multi-server orchestrator, parallel initialization, tool routing
  - All components support graceful degradation if MCP is not configured
- `repository/` - File Repository component
  - `FileRepository.kt` - Manages prompts and conversation history
  - Stores user history in JSON format: `history/user_{userId}.json`
  - Reads system prompt from: `promts/system.md`
  - Reads assistant prompt from: `promts/assistant.md`
  - Injected as singleton via Koin

**Key architectural patterns:**
- **Dependency Injection**: All components managed by Koin DI framework
- **Clean separation of concerns**: Bot → Agent → Repository, Agent → MCP Manager
- **Singleton pattern**: All main components are singletons managed by Koin
- **Shared HTTP client**: Single Ktor HttpClient instance used by both OpenAI and MCP (DI-managed)
- **File-based storage** for prompts and history (JSON format)
- **Tool calling loop**: Multi-turn conversation support for tool execution (max 5 iterations)
- **Graceful degradation**: Agent works without MCP if not configured
- **Coroutines** for async message processing and parallel MCP server initialization
- **Convention plugins** in `buildSrc` centralize build configuration
- **Version catalog** in `gradle/libs.versions.toml` manages all dependency versions
- **Docker multi-stage build** for production deployment

## Technology Stack

**Core:**
- Kotlin 2.2.0 with JVM target
- Java 21 toolchain (configured via convention plugin)
- JUnit Platform for testing

**Kotlin Ecosystem:**
- kotlinx-datetime 0.6.1 - Date/time handling
- kotlinx-serialization-json 1.7.3 - JSON serialization
- kotlinx-coroutines 1.9.0 - Async/await coroutines support

**AI & Automation:**
- OpenAI API - Direct integration via Ktor HTTP client
  - Uses gpt-4o-mini model by default
  - Custom conversation management with history support
  - Supports system prompts and message history

**Integrations:**
- kotlin-telegram-bot 6.3.0 - Telegram Bot API client
  - Long polling for receiving messages
  - Command handling (/start, /help, /clear)
  - Chat actions (typing indicator)
- Ktor 3.3.0 - HTTP client (used by Koog)
  - CIO engine for async I/O
  - Content negotiation for JSON

**Dependency Injection:**
- Koin 4.0.0 - Lightweight dependency injection framework
  - koin-core - Core DI functionality
  - koin-logger-slf4j - SLF4J logging integration

**Configuration & Logging:**
- dotenv-kotlin 6.5.1 - Environment variable management from `.env` files
- SLF4J Simple 2.0.16 - Simple logging implementation

**Token Management:**
- jtokkit 1.1.0 - Token counting library for OpenAI models
  - Accurate token counting for GPT models
  - Used for tracking prompt and completion token usage
  - Helps manage API costs and context limits

**Repositories:**
- Maven Central - Primary dependency source
- JitPack - Required for kotlin-telegram-bot library

## Recent Development History

Based on git commit history, recent features include:
- **Message history in JSON**: Enhanced conversation history storage format
- **History compacting**: Automatic conversation history management to prevent context overflow
- **Message count tracking**: Monitor conversation length and message counts
- **Token limits**: Implementation of token limit handling for OpenAI API
- **Token calculation**: Accurate token counting using jtokkit library

**Benchmarking results** (`results.md`):
- Contains performance comparisons of different AI models
- Includes response time measurements and token usage statistics
- Useful for model selection and performance optimization

## MCP Integration Architecture

### Overview

The Model Context Protocol (MCP) integration enables the AI agent to use external tools dynamically based on conversation needs. The implementation follows a layered architecture with clean separation of concerns.

### Component Layers

**1. Transport Layer** (`mcp/transport/`):
- Abstraction for different communication protocols
- **StdioTransport**: Launches external processes, communicates via stdin/stdout with newline-delimited JSON-RPC
- **HttpTransport**: Sends JSON-RPC requests to remote HTTP servers
- Timeout handling (default: 30s), error recovery, graceful failure

**2. Client Layer** (`mcp/client/`):
- **McpClient**: Manages single server connection
- Tool discovery via `tools/list` method (cached for performance)
- Tool execution via `tools/call` method
- Thread-safe with Mutex for concurrent requests
- UUID-based request IDs for correlation

**3. Configuration Layer** (`mcp/config/`):
- **McpConfig**: Data classes for server configuration
- **McpConfigLoader**: Loads `mcp-config.json`, validates, handles missing files gracefully
- Supports both stdio and HTTP server types
- Per-server configuration: command, args, env vars, URL, headers, timeout

**4. Manager Layer** (`mcp/`):
- **McpManager**: Orchestrates multiple MCP servers
- Parallel server initialization (performance optimization)
- Tool routing (maps tool names to servers)
- Availability checking for graceful degradation
- Provides unified tool interface to AiAgent

### OpenAI Function Calling Integration

**Tool Discovery**:
1. On startup, McpManager initializes all enabled servers
2. Each server's tools are fetched via `tools/list`
3. MCP tools are converted to OpenAI tool format (name, description, parameters)
4. Tool list is cached and provided to AiAgent

**Execution Flow**:
1. User sends message to bot
2. AiAgent builds message list with conversation history
3. Available tools are fetched from McpManager
4. OpenAI API called with tools parameter
5. **Tool Calling Loop** (max 5 iterations):
   - If `finish_reason == "tool_calls"`:
     - Parse tool calls from response
     - Execute each tool via McpManager
     - Add tool results to message list
     - Call OpenAI API again with updated messages
   - If `finish_reason == "stop"`:
     - Parse final response, save to history, return to user
   - If max iterations exceeded:
     - Return error message to prevent infinite loops

**Data Flow**:
```
User Message
  ↓
TelegramBot
  ↓
AiAgent.processMessage()
  ├→ Get tools from McpManager.getAllTools()
  ├→ Call OpenAI with tools
  ├→ If tool_calls:
  │   ├→ McpManager.callTool()
  │   ├→ McpClient.callTool()
  │   └→ Transport.send()
  └→ Return final response
```

### Configuration File Format

Location: `mcp-config.json` (git-ignored)

Example:
```json
{
  "mcpServers": [
    {
      "name": "filesystem",
      "enabled": true,
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path"],
      "timeout": 30000
    },
    {
      "name": "api",
      "enabled": true,
      "type": "http",
      "url": "https://api.example.com/mcp",
      "headers": {"Authorization": "Bearer token"},
      "timeout": 15000
    }
  ]
}
```

### Error Handling

**Configuration Errors**:
- Missing config file → Log warning, continue without tools
- Invalid JSON → Log error, continue without tools
- Invalid server config → Skip that server, load others

**Runtime Errors**:
- Server initialization fails → Skip that server, log error
- Tool execution fails → Return error to OpenAI as tool result
- Transport timeout → Return timeout error message
- Max iterations exceeded → Return error to user

**Graceful Degradation**:
- Agent works fully without MCP configuration
- Tools only used if at least one server is available
- Failed servers don't block other servers or application startup

### Performance Optimizations

- **Parallel initialization**: All servers start concurrently using `async`/`awaitAll`
- **Tool caching**: Tools list cached after first fetch
- **Shared HTTP client**: Single Ktor client instance for all HTTP communication
- **Connection pooling**: Stdio processes kept alive for duration of bot runtime

### Security Considerations

- `mcp-config.json` is git-ignored (may contain secrets)
- Filesystem server requires explicit allowed directories
- Tool execution limited to max 5 iterations (prevents infinite loops)
- 30-second default timeout prevents resource exhaustion
- All tool calls logged for audit purposes

### Documentation

- **User Guide**: `MCP_GUIDE.md` - Comprehensive setup and usage instructions
- **Example Config**: `mcp-config.json.example` - Sample configurations for popular servers
- **README Section**: Brief overview and quick start guide

## Application Flow

1. **Startup** (`App.kt`):
   - Initialize SLF4J logger for application logging
   - Initialize Koin DI framework with `allModules` and SLF4J logger
   - Koin loads environment variables via Dotenv (fails if required vars missing)
   - Koin creates all components (FileRepository, AiAgent, TelegramBot) as singletons
   - Components are wired automatically based on DI definitions in `AppDi.kt`
   - Get TelegramBot and AiAgent instances from Koin container
   - Register shutdown hook that executes on JVM termination:
     - Stops TelegramBot polling
     - Closes AiAgent resources (HTTP client)
     - Stops Koin DI framework
   - Start bot polling loop with `telegramBot.start()`
   - Main thread waits indefinitely (`Thread.currentThread().join()`)
   - Any fatal errors trigger Koin shutdown and exception re-throw

2. **Message Processing** (`TelegramBot.kt`):
   - Receive user message
   - Filter out commands (they have dedicated handlers)
   - Send typing indicator
   - Call AiAgent to process message (async)
   - Send response back to user with ParseMode.MARKDOWN

3. **AI Processing** (`AiAgent.kt`):
   - Get system prompt from FileRepository (instructs AI to return JSON answer format)
   - Load user's conversation history
   - Build message list (system + assistant prompt if first message + history + current)
   - Assistant prompt loaded from `promts/assistant.md` for new conversations
   - Measure start time for performance tracking
   - Call OpenAI API with `response_format: json_object` via Ktor HTTP client
   - Calculate response time in milliseconds
   - Extract token usage statistics from API response
   - Parse JSON response (type, text, summary fields)
   - Format response with:
     - Answer text with markdown formatting
     - Optional summary (💡 emoji)
     - Statistics section (📊 emoji with response time and token usage)
   - Save user message and formatted response to history
   - Return formatted markdown response

4. **Storage** (`FileRepository.kt`):
   - Read `promts/system.md` for system prompt
   - Read `promts/assistant.md` for assistant prompt (shown to new users)
   - Read/write `history/user_{userId}.md` for conversation history
   - Parse markdown format with `## User:` and `## Assistant:` headers

## Development Notes

**When adding dependencies:**
- Update `gradle/libs.versions.toml` first (add version, library declaration)
- Reference using `libs.` notation in build files (e.g., `implementation(libs.koogAgents)`)
- Consider creating bundles for related dependencies (e.g., `bundles.ktorClient`)

**File organization:**
- Application entry point: `app/src/main/kotlin/io/github/devapro/ai/App.kt`
- DI configuration: `app/src/main/kotlin/io/github/devapro/ai/di/AppDi.kt`
- Components in dedicated packages: `agent/`, `bot/`, `repository/`
- Each component in its own file
- Tests mirror source structure
- Build outputs: `app/build/libs/app.jar` (fat JAR with all dependencies)

**Coding standards:**
- Use `@Serializable` annotation on data classes that need JSON serialization
- Use coroutines for async operations (`suspend` functions)
- Use SLF4J logger for logging: `LoggerFactory.getLogger(ClassName::class.java)`
- Follow Kotlin naming conventions
- Define all dependencies in `AppDi.kt` using Koin DSL
- Use constructor injection for all components (dependencies passed as constructor parameters)
- Retrieve components from Koin using `koin.get<ComponentType>()`

**Build configuration:**
- `app/build.gradle.kts` configures the Application plugin with main class
- JAR task configured to create fat JAR (includes all runtime dependencies)
- Duplicates strategy set to EXCLUDE to avoid conflicts
- Manifest includes Main-Class attribute for executable JAR
- Run directly: `java -jar app/build/libs/app.jar`

**Conversation history format:**
```markdown
## User:
*timestamp*

message content

---

## Assistant:
*timestamp*

response content

---
```

**Docker deployment:**
- Multi-stage build: Gradle build → JRE runtime
- Volumes mounted: `history/` and `promts/`
- Environment variables passed from `.env` to container
- Dockerfile location: `docker/Dockerfile`
- Docker Compose config: `docker/docker-compose.yml`

**When modifying prompts:**
- Edit `promts/system.md` to change AI assistant behavior and guidelines
- Edit `promts/assistant.md` to customize the initial greeting message shown to new users
- System prompt instructs AI to return JSON with answer fields:
  - `type`: Always "answer"
  - `text`: Full answer text with markdown formatting
  - `summary`: Optional one-line summary
- **AI Assistant instructions** are included in system prompt:
  - Answer questions accurately and clearly
  - Provide practical value with context and examples
  - Use clear, accessible language
  - Break complex topics into understandable parts
  - Be honest about limitations when uncertain
  - Use markdown formatting (*bold*, _italic_, bullet lists)
- AI is instructed to provide helpful, relevant, and complete responses
- Changes take effect on next message (no restart needed in Docker due to volume mount)
- OpenAI API is configured with `response_format: json_object` to enforce JSON responses

**Markdown formatting in responses:**
- Bot sends all messages with `ParseMode.MARKDOWN`
- AI responses include:
  - Answer text with markdown formatting (*bold* for key terms, _italic_ for emphasis)
  - Bullet lists (•) and numbered lists
  - Optional summary with 💡 emoji
  - 📊 Statistics section with response time and token usage
- Assistant prompt shown at start of new conversations (from `promts/assistant.md`)
- Commands (/start, /help, /clear) use markdown for better visual appearance
- Supported markdown: *bold*, _italic_, `code`, bullet lists (•), numbered lists

**When debugging:**
- Check logs: `docker-compose logs -f` (Docker) or console output (local)
- Verify `.env` file exists and has correct values (use `.env.example` as template)
- Check `history/` directory for conversation files (auto-created if missing)
- Test OpenAI API key: ensure valid and has credits
- Koin logs dependency resolution during startup - check for DI errors
- Review `results.md` for performance benchmarks and model comparisons

**Project documentation files:**
- `README.md` - User-facing documentation with setup and usage instructions
- `CLAUDE.md` - This file - developer guidance for Claude Code
- `DI_IMPLEMENTATION.md` - Detailed dependency injection implementation notes
- `IMPLEMENTATION_SUMMARY.md` - Summary of implementation decisions and architecture
- `MARKDOWN_IMPLEMENTATION.md` - Details about markdown formatting implementation
- `PLANNING_AGENT.md` - Planning and agent architecture documentation
- `results.md` - AI model benchmarking results and performance data

## Dependency Injection (Koin)

**DI Configuration** (`AppDi.kt`):
The application uses Koin for dependency injection. All components are defined in `AppDi.kt`:

```kotlin
val appModule = module {
    // Configuration layer - Dotenv and environment variables
    single<Dotenv> { dotenv { ignoreIfMissing = true } }

    // Named qualifiers for configuration values with validation
    single(named("openAiApiKey")) {
        get<Dotenv>()["OPENAI_API_KEY"]
            ?: throw IllegalStateException("OPENAI_API_KEY environment variable is required")
    }
    single(named("telegramBotToken")) {
        get<Dotenv>()["TELEGRAM_BOT_TOKEN"]
            ?: throw IllegalStateException("TELEGRAM_BOT_TOKEN environment variable is required")
    }
    single(named("promptsDir")) { get<Dotenv>()["PROMPTS_DIR"] ?: "promts" }
    single(named("historyDir")) { get<Dotenv>()["HISTORY_DIR"] ?: "history" }

    // Application components as singletons with constructor injection
    single { FileRepository(get(named("promptsDir")), get(named("historyDir"))) }
    single { AiAgent(get(named("openAiApiKey")), get()) }
    single { TelegramBot(get(named("telegramBotToken")), get()) }
}
```

**Error handling:**
- Required environment variables throw `IllegalStateException` if missing
- Application fails fast during startup if configuration is invalid
- Clear error messages indicate which variable is missing

**Adding new components:**
1. Add component definition to `appModule` in `AppDi.kt`
2. Use `single { }` for singletons, `factory { }` for new instances
3. Use `get()` to resolve dependencies
4. Use `named("qualifier")` for multiple instances of same type
5. Retrieve component in `App.kt` using `koin.get<ComponentType>()`

**DI Benefits:**
- Automatic dependency resolution and lifecycle management
- Easy testing with mock/test modules
- Clear dependency graph in one place
- No manual object instantiation or wiring
- Centralized configuration management
