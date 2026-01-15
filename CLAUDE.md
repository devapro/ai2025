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
- `PROJECT_SOURCE_DIR` - Project source code directory for file tools (default: `project-source`)
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
- `project-source/` - Project source code (configurable via PROJECT_SOURCE_DIR, used by file tools)
- `doc-source/` - Documentation folder (writable by agent via DocumentWriterTool)

## Project Architecture

AI Assistant Telegram Bot with modular architecture:

**Module structure:**
- `app/` - Main application module
  - Entry point: `io.github.devapro.ai.AppKt`
  - Depends on `tools` and `utils-embeds` modules
- `tools/` - Internal tools module
  - Built-in tools integrated directly in code (not external MCP)
  - File operations, code exploration, documentation writing
  - RAG search tools
- `utils-embeds/` - RAG components and utilities
  - Entry point: `io.github.devapro.ai.embeds.UtilAppKt`
  - Standalone document indexing application
- `buildSrc/` - Convention plugins for shared build logic
  - Java 21 toolchain, JUnit Platform, test logging

**App module components:**
- `di/AppDi.kt` - Koin dependency injection configuration
- `bot/TelegramBot.kt` - Telegram API integration, commands (/start, /help, /clear)
- `agent/AiAgent.kt` - OpenAI API integration with internal tools and MCP support
  - Function calling for tool execution
  - Multi-turn conversations (max 20 iterations)
  - Response formatting with markdown and statistics
  - Routes tool calls to internal or external (MCP) tools
- `agent/ToolProvider.kt` - Unified tool provider
  - Aggregates internal tools (from tools module) and external tools (from MCP)
  - Converts both to OpenAI function format
  - Routes tool calls to appropriate handler
- `mcp/` - Model Context Protocol integration
  - Multiple transports: Stdio (npx/python), SSE (HTTP), HttpTransport (MCP SDK)
  - Multi-server orchestrator with parallel initialization
  - Tool discovery, caching, and execution
  - See `MCP_GUIDE.md` for detailed setup
- `repository/FileRepository.kt` - Prompts and history management
- `scheduler/DailySummaryScheduler.kt` - Daily conversation summaries

**Tools module components:**
- `Tool.kt` - Interface that all tools implement (createToolDefinition, execute)
- `impl/FindFileTool.kt` - File search with glob patterns
- `impl/ReadFileTool.kt` - File reading with optional line ranges
- `impl/FolderStructureTool.kt` - Display directory tree structure
- `impl/ExploringTool.kt` - AI-powered file summaries using GPT-4o-mini
- `impl/DocumentWriterTool.kt` - Create/modify documentation in doc-source folder
- `rag/RagSearchTool.kt` - Basic RAG semantic search
- `rag/EnhancedRagSearchTool.kt` - Advanced RAG with query expansion and re-ranking
- `rag/TokenCounter.kt`, `rag/QueryExpander.kt`, `rag/RagResultsRefiner.kt`, `rag/ContextCompressor.kt` - RAG support components

**Utils-embeds module components:**
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
4. **Tool calling** - If OpenAI requests tool use:
   - ToolProvider checks if tool is internal (tools module) or external (MCP)
   - Internal tools: Execute directly via Tool.execute()
   - External tools: Route to McpManager
   - Return result → Continue conversation (up to 20 iterations)

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

## Internal Tools

The application includes built-in tools (integrated in code, not via external MCP) that are always available to the AI agent.

**Tool Architecture:**
- All tools implement the `Tool` interface with `createToolDefinition()` and `execute()` methods
- Tools are registered in `AppDi.kt` and provided to the agent via `ToolProvider`
- `ToolProvider` aggregates both internal tools and external MCP tools
- AI agent routes tool calls to the appropriate handler (internal or external)

**Available Internal Tools:**

### File Operations

**find_file** (`FindFileTool.kt`)
- Search for files using glob patterns (*.kt, **/*.java, etc.)
- Filter by path, extension, max depth, max results
- Returns list of matching file paths sorted by modification time
- Example: `find_file(path="src", pattern="*Service.kt", maxDepth=3)`

**read_file** (`ReadFileTool.kt`)
- Read file contents with optional line ranges
- Three modes: `source_code` (project-source), `document` (doc-source), `system` (bot working directory)
- Line-numbered output for code references
- 10MB file size limit for safety
- Examples:
  - `read_file(path="CLAUDE.md", mode="document")` - read from doc-source/
  - `read_file(path="promts/rules.md", mode="system")` - read from bot directory
  - `read_file(path="src/Main.kt", startLine=10, endLine=50, mode="source_code")` - with line range

**folder_structure** (`FolderStructureTool.kt`)
- Display directory tree with visual connectors (├──, └──)
- Configurable: maxDepth, showFiles, showHidden, showSizes, maxItems
- Shows summary statistics (dir count, file count, total size)
- Sorted display (directories first, then files, alphabetically)
- Example: `folder_structure(path="src", maxDepth=2, showFiles=true)`

### Code Exploration

**explore_files** (`ExploringTool.kt`)
- Generate AI-powered summaries for multiple files
- Two modes: specific file list or entire folder (recursive optional)
- Uses GPT-4o-mini for cost-effective summaries (1-3 sentences per file)
- Filter by file extensions, control depth
- Limits: 20 files per request, 50KB per file, 10K chars to LLM
- Example: `explore_files(folderPath="src/agent", recursive=true, fileExtensions=["kt"])`

### Semantic Search

**search_documents** (`RagSearchTool.kt` or `EnhancedRagSearchTool.kt`)
- Semantic search through indexed documentation
- Basic mode: vector similarity search
- Enhanced mode: query expansion, re-ranking, context compression
- Requires RAG setup (LM Studio + indexed embeddings)
- Configurable: topK, minSimilarity
- Example: `search_documents(query="authentication implementation", topK=5)`

### Git Operations

**git_operation** (`GitOperationTool.kt`)
- Perform git operations on the project repository
- Operations:
  * `get_diff` - Get diff between base and PR branch (automatically handles branch switching and cleanup)
  * `get_current_branch` - Get the name of the currently checked out branch
  * `list_branches` - List all available branches (local and remote)
- Automatic branch restoration (returns to original branch even if errors occur)
- Branch existence validation and uncommitted changes detection
- 30-second timeout per operation
- Example: `git_operation(operation="get_diff", prBranch="feature/new-feature", baseBranch="develop")`
- Primary use case: PR review workflow via `/review-pr` command

### GitHub Integration

**github_api** (`GitHubTool.kt`)
- Fetch GitHub repository and Pull Request information via GitHub REST API
- Built-in tool (no MCP server required)
- Operations:
  * `get_pr` - Fetch comprehensive PR details
- Input methods:
  * By URL: `github_api(operation="get_pr", url="https://github.com/owner/repo/pull/123")`
  * By components: `github_api(operation="get_pr", owner="owner", repo="repo", prNumber=123)`
- Returns detailed PR information:
  * Basic: title, description, state (open/closed/merged), author
  * Branches: head branch, base branch
  * Statistics: comments, commits, changed files, additions/deletions
  * Metadata: labels, assignees, reviewers
- Authentication:
  * Works without token for public repos (60 req/hour rate limit)
  * Configure GITHUB_TOKEN in .env for private repos and higher rate limit (5000 req/hour)
- Graceful error handling with helpful messages
- Alternative to GitHub MCP server (simpler setup, works immediately)

### JIRA Integration

**jira_api** (`JiraTool.kt`)
- Fetch and manage JIRA issues via JIRA REST API
- Built-in tool (no MCP server required)
- Operations:
  * `get_issue` - Fetch comprehensive issue details
  * `get_backlog` - Fetch list of tasks from backlog (requires JIRA_PROJECT_KEY)
  * `get_active_sprint` - Fetch tasks in active sprint (requires JIRA_PROJECT_KEY)
  * `create_issue` - Create a new task (requires JIRA_PROJECT_KEY)
- Input methods:
  * Get issue by key: `jira_api(operation="get_issue", issueKey="PROJ-123")`
  * Get issue by URL: `jira_api(operation="get_issue", url="https://company.atlassian.net/browse/PROJ-123")`
  * Get backlog: `jira_api(operation="get_backlog", maxResults=50)`
  * Get active sprint: `jira_api(operation="get_active_sprint", maxResults=50)`
  * Create issue: `jira_api(operation="create_issue", summary="Task title", description="Details", issueType="Task", priority="High", assignee="user@example.com", labels=["bug", "urgent"])`
- Returns detailed issue information:
  * Basic: summary, description, issue type, priority, status, resolution
  * People: assignee, reporter, creator
  * Dates: created, updated, due date, resolved date
  * Hierarchy: parent issue, subtasks
  * Metadata: labels, story points, custom fields
- Authentication:
  * Requires: JIRA_URL, JIRA_EMAIL, JIRA_API_TOKEN in .env
  * Optional: JIRA_PROJECT_KEY (required for backlog/sprint/create operations)
  * Optional: JIRA_BACKLOG_STATUSES (comma-separated list of statuses, default: "Backlog,To Do,Open")
  * Works with both Jira Cloud and Server/Data Center
  * Helpful error messages if not configured
- Features:
  * Supports both Cloud (atlassian.net) and Server/Data Center instances
  * Automatic issue key extraction from PR descriptions
  * Backlog fetches unresolved issues with configurable statuses (not in any sprint)
  * Active sprint fetches issues in currently open sprints
  * Create issue supports custom assignees, priorities, and labels
  * Configurable backlog statuses to match your workflow (e.g., "Backlog,To Do,Open,Ready for Development")
- Alternative to JIRA MCP server (simpler setup, works immediately)

### PR Review Feature

The bot includes a `/review-pr` command that performs comprehensive pull request reviews:

**Command Usage:**
```
/review-pr https://github.com/owner/repo/pull/123
```

**Workflow:**
1. Parse PR URL to extract owner, repo, and PR number
2. Fetch PR details using built-in GitHub API tool (title, description, branch name, author)
   - Uses `github_api` tool (no MCP configuration needed!)
   - Example: `github_api(operation="get_pr", url="https://github.com/owner/repo/pull/123")`
3. Extract JIRA task link from PR description (if present)
   - Looks for issue keys like PROJ-123, PROJECT-456, etc.
4. Fetch JIRA task details using built-in JIRA API tool (requirements, acceptance criteria)
   - Uses `jira_api` tool (no MCP configuration needed if credentials set!)
   - Example: `jira_api(operation="get_issue", issueKey="PROJ-123")`
5. Read project conventions (CLAUDE.md) and code quality rules (promts/rules.md)
6. Use git_operation tool to get diff between base and PR branches
7. Analyze code changes against:
   - JIRA requirements (if available)
   - Project conventions (CLAUDE.md)
   - Code quality rules (rules.md)
8. Generate structured review report with:
   - Requirements compliance check
   - Issues found (with file paths and line numbers)
   - Security analysis
   - Testing coverage assessment
   - Actionable recommendations

**Prerequisites:**
- **REQUIRED:** `PROJECT_SOURCE_DIR` environment variable must point to a valid git repository
  - Set in `.env` file: `PROJECT_SOURCE_DIR=/path/to/your/project`
  - Can be absolute path or relative to bot's working directory
  - Directory must contain a `.git` folder
- **Recommended:** `GITHUB_TOKEN` in `.env` for accessing private repositories and higher rate limits
  - Public repos work without token (60 req/hour)
  - With token: private repos + 5000 req/hour
- **Optional:** `JIRA_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN` in `.env` for JIRA integration
  - Enables automatic JIRA task fetching from PR descriptions
  - Works with both Jira Cloud and Server/Data Center
- `promts/rules.md` file with code quality rules (optional)

**Report Format:**
- Severity levels: Critical (must fix), Major (should fix), Minor (nice to have)
- Each issue includes: file path, line number, code snippet, description, recommendation
- Final conclusion: APPROVE / REQUEST CHANGES / REJECT

**Graceful Degradation:**
- Built-in GitHub API tool works immediately (no MCP setup needed)
- Works without GITHUB_TOKEN (public repos only, rate limited)
- Built-in JIRA API tool works when credentials configured (no MCP setup needed)
- Works without JIRA credentials (skips JIRA task analysis)
- Works without rules.md (general code review without specific guidelines)
- Clear error messages guide configuration if needed

**Tool Registration Pattern:**
```kotlin
// In AppDi.kt
single {
    val tools = mutableListOf<Tool>()

    // Add RAG if enabled
    if (ragEnabled) {
        tools.add(get<RagSearchToolInterface>())
    }

    // Add file tools (always available)
    tools.add(FindFileTool())
    tools.add(ReadFileTool())
    tools.add(FolderStructureTool())
    tools.add(ExploringTool(apiKey = get(...), httpClient = get()))
    tools.add(DocumentWriterTool())
    tools.add(GitOperationTool(workingDirectory = projectSourceDir))
    tools.add(GitHubTool(httpClient = get(), githubToken = get(...)))
    tools.add(JiraTool(httpClient = get(), jiraUrl = get(...), jiraEmail = get(...), jiraToken = get(...)))

    tools
}
```

**Usage in Agent:**
The AI automatically selects and uses appropriate tools based on user queries. No configuration needed - tools are discovered via OpenAI function calling.

## Development Notes

**Adding dependencies:**
- Update `gradle/libs.versions.toml` first
- Reference with `libs.` notation (e.g., `implementation(libs.ktor.client.core)`)

**File organization:**
- Entry points:
  - App: `app/src/main/kotlin/io/github/devapro/ai/App.kt`
  - Utils: `utils-embeds/src/main/kotlin/io/github/devapro/ai/embeds/UtilApp.kt`
- DI config: `app/src/main/kotlin/io/github/devapro/ai/di/AppDi.kt`
- App components: `agent/`, `bot/`, `mcp/`, `repository/`, `scheduler/`
- Tools module: `tools/src/main/kotlin/io/github/devapro/ai/tools/`
  - Tool implementations: `impl/FindFileTool.kt`, `impl/ReadFileTool.kt`, etc.
  - RAG tools: `rag/RagSearchTool.kt`, `rag/EnhancedRagSearchTool.kt`, etc.
- Utils-embeds: `utils-embeds/src/main/kotlin/io/github/devapro/ai/embeds/rag/`
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
