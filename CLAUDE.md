# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System

This is a Gradle-based Kotlin JVM project using the Gradle Wrapper. Always use `./gradlew` (not `gradle`) for commands.

**Core commands:**
- `./gradlew run` - Build and run the Telegram bot application
- `./gradlew build` - Build the project without running
- `./gradlew check` - Run all checks including tests
- `./gradlew test` - Run tests only
- `./gradlew clean` - Clean build outputs

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

**Important files:**
- `.env` - Configuration file with secrets (git-ignored)
- `promts/system.md` - System prompt for AI agent (customizable)
- `promts/assistant.md` - Assistant prompt shown at start of conversation (customizable)
- `history/` - User conversation history (git-ignored, auto-created)

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
  - `AiAgent.kt` - Manages AI conversations using OpenAI API directly
  - Uses Ktor HTTP client for API communication
  - Handles conversation context and history
  - **JSON mode enabled**: Forces OpenAI to return structured JSON responses
  - **Response format**: Answer response with fields:
    - `type`: Always "answer"
    - `text`: Full answer text with markdown formatting
    - `summary`: Optional one-line summary
  - Parses JSON responses and formats with markdown
  - **General AI Assistant**: Answers questions, provides explanations, offers advice
  - **Performance tracking**: Measures response time and token usage
  - Includes data classes for OpenAI request/response serialization
  - Injected as singleton via Koin
- `repository/` - File Repository component
  - `FileRepository.kt` - Manages prompts and conversation history
  - Stores user history in markdown files: `history/user_{userId}.md`
  - Reads system prompt from: `promts/system.md`
  - Reads assistant prompt from: `promts/assistant.md`
  - Injected as singleton via Koin

**Key architectural patterns:**
- **Dependency Injection**: All components managed by Koin DI framework
- **Clean separation of concerns**: Bot → Agent → Repository
- **Singleton pattern**: All main components are singletons managed by Koin
- **File-based storage** for prompts and history (markdown format)
- **Coroutines** for async message processing
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

**Repositories:**
- Maven Central - Primary dependency source

## Application Flow

1. **Startup** (`App.kt`):
   - Initialize Koin DI framework with `allModules`
   - Koin loads environment variables via Dotenv
   - Koin creates all components (FileRepository, AiAgent, TelegramBot) as singletons
   - Components are wired automatically based on DI definitions in `AppDi.kt`
   - Get TelegramBot from Koin container
   - Register shutdown hook (stops bot, closes agent, stops Koin)
   - Start bot polling loop

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

**Coding standards:**
- Use `@Serializable` annotation on data classes that need JSON serialization
- Use coroutines for async operations (`suspend` functions)
- Use SLF4J logger for logging: `LoggerFactory.getLogger(ClassName::class.java)`
- Follow Kotlin naming conventions
- Define all dependencies in `AppDi.kt` using Koin DSL
- Use constructor injection for all components (dependencies passed as constructor parameters)
- Retrieve components from Koin using `koin.get<ComponentType>()`

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
- Verify `.env` file exists and has correct values
- Check `history/` directory for conversation files
- Test OpenAI API key: ensure valid and has credits
- Koin logs dependency resolution during startup - check for DI errors

## Dependency Injection (Koin)

**DI Configuration** (`AppDi.kt`):
The application uses Koin for dependency injection. All components are defined in `AppDi.kt`:

```kotlin
val appModule = module {
    // Configuration layer - Dotenv and environment variables
    single<Dotenv> { dotenv { ignoreIfMissing = true } }

    // Named qualifiers for configuration values
    single(named("openAiApiKey")) { get<Dotenv>()["OPENAI_API_KEY"] }
    single(named("telegramBotToken")) { get<Dotenv>()["TELEGRAM_BOT_TOKEN"] }
    single(named("promptsDir")) { get<Dotenv>()["PROMPTS_DIR"] ?: "promts" }
    single(named("historyDir")) { get<Dotenv>()["HISTORY_DIR"] ?: "history" }

    // Application components as singletons
    single { FileRepository(get(named("promptsDir")), get(named("historyDir"))) }
    single { AiAgent(get(named("openAiApiKey")), get()) }
    single { TelegramBot(get(named("telegramBotToken")), get()) }
}
```

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
