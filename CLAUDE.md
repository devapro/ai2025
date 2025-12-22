# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System

This is a Gradle-based Kotlin JVM project using the Gradle Wrapper. Always use `./gradlew` (not `gradle`) for commands.

**Core commands:**
- `./gradlew run` - Build and run the Telegram bot application
- `./gradlew :utils:run` - Run the RAG utilities application for document indexing
- `./gradlew build` - Build the project (includes fat JAR creation)
- `./gradlew check` - Run all checks including tests
- `./gradlew test` - Run tests only
- `./gradlew clean` - Clean build outputs
- `./gradlew jar` - Create fat JAR with all dependencies in `app/build/libs/`

**Test commands:**
- `./gradlew test` - Run all tests
- `./gradlew :utils:test` - Run tests for utils module only
- `./gradlew :app:test` - Run tests for app module only

**RAG utilities commands:**
- `./gradlew :utils:run` - Index embeddings.md to embeddings.db (default settings)
- `./gradlew :utils:run --args="input.md output.db"` - Index custom file
- `./gradlew :utils:run --args="docs.md vectors.db 800 150"` - Custom chunk size and overlap
- Requires LM Studio running at http://127.0.0.1:1234 with text-embedding-nomic-embed-text-v1.5 model

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

**RAG utilities configuration:**
- Uses same `OPENAI_API_KEY` from `.env` (required but can be dummy value for LM Studio)
- Requires LM Studio server running at http://127.0.0.1:1234
- Model: text-embedding-nomic-embed-text-v1.5 must be loaded in LM Studio
- Default input: `embeddings.md` (markdown file to index)
- Default output: `embeddings.db` (SQLite database)
- Configurable via command-line arguments (see Build System section)

**Important files:**
- `.env` - Configuration file with secrets (git-ignored)
- `promts/system.md` - System prompt for AI agent (customizable)
- `promts/assistant.md` - Assistant prompt shown at start of conversation (customizable)
- `history/` - User conversation history (git-ignored, auto-created)
- `mcp-config.json` - MCP server configuration (git-ignored, contains API keys/credentials)
- `mcp-config.json.example` - Example MCP configuration file
- `embeddings.db` - Vector database for RAG (created by :utils:run, git-ignored)

## Project Architecture

This is an AI Assistant Telegram Bot implemented with a modular architecture:

**Module structure:**
- `app/` - Main application module containing all bot logic
  - Entry point: `io.github.devapro.ai.AppKt` (compiled from `App.kt`)
  - Main class: `io.github.devapro.ai.main()`
  - Depends on: `utils` module
- `utils/` - Shared utilities module with RAG components
  - Entry point: `io.github.devapro.ai.utils.UtilAppKt` (compiled from `UtilApp.kt`)
  - Main class: `io.github.devapro.ai.utils.main()`
  - RAG components in `utils/src/main/kotlin/io/github/devapro/ai/utils/rag/`
  - Standalone application for document indexing and embedding generation
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
  - `AiAgent.kt` - Manages AI conversations using OpenAI API with MCP and RAG tool support
  - Uses shared Ktor HTTP client for API communication
  - Handles conversation context and history
  - **MCP Tool Integration**: Automatically uses external tools when needed
  - **RAG Integration**: Built-in `search_documents` tool for semantic document search
    - Query → Embedding → Vector Search → Results
    - Automatic context retrieval from indexed documents
    - Configurable via environment variables
    - Uses local LM Studio for embeddings (no API costs)
  - **Function Calling**: Implements OpenAI function calling for tool execution
  - **Multi-turn conversations**: Handles tool calls across multiple API requests (max 20 iterations)
  - **Response format**: Answer response with fields:
    - `type`: Always "answer"
    - `text`: Full answer text with markdown formatting
    - `summary`: Optional one-line summary
  - Parses JSON responses and formats with markdown
  - **General AI Assistant**: Answers questions, provides explanations, offers advice
  - **Performance tracking**: Measures response time and token usage
  - Includes data classes for OpenAI request/response serialization
  - Injected as singleton via Koin with dependencies: apiKey, FileRepository, McpManager, HttpClient, VectorDatabase?, EmbeddingGenerator?
- `mcp/` - Model Context Protocol integration
  - `model/McpModels.kt` - JSON-RPC and MCP protocol data structures
  - `transport/McpTransport.kt` - Transport interface for all communication protocols
  - `transport/StdioTransport.kt` - Local process transport via stdin/stdout (npx, python scripts)
  - `transport/SseTransport.kt` - **[ACTIVE]** Server-Sent Events transport (GET /sse + POST /message)
  - `transport/HttpTransport.kt` - Official MCP Kotlin SDK transport (single POST endpoint)
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

**Component architecture (within utils module):**
- `rag/` - RAG (Retrieval-Augmented Generation) components
  - `TextChunker.kt` - Text chunking with smart boundary detection
    - **Sentence-first strategy**: Prioritizes semantic coherence
    - **Markdown-aware chunking**: Structure-preserving for documentation
    - Smart boundary detection (sentence, word, code block)
    - Configurable chunk size and overlap
    - Metadata preservation for each chunk
  - `MarkdownParser.kt` - Markdown structure parser
    - Parses headings, code blocks, lists, paragraphs
    - Groups content by sections
    - Preserves document hierarchy
    - Enables structure-aware chunking
  - `EmbeddingGenerator.kt` - Vector embedding generation
    - Uses local LM Studio server (text-embedding-nomic-embed-text-v1.5)
    - Batch processing (up to 100 texts per request)
    - Token usage tracking
    - Rate limiting support
    - Async/await with coroutines
  - `VectorDatabase.kt` - SQLite vector storage
    - Cosine similarity search
    - Top-K retrieval with similarity threshold
    - Metadata storage (JSON)
    - Transaction management with Exposed ORM
- `UtilApp.kt` - Standalone RAG pipeline orchestrator
  - Document indexing workflow
  - Automatic Markdown detection
  - Command-line argument support
  - Demo search functionality

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
- Ktor 3.3.0 - HTTP client
  - CIO engine for async I/O
  - Content negotiation for JSON
  - SSE plugin for Server-Sent Events support
- MCP Kotlin SDK 0.8.1 - Official Model Context Protocol implementation
  - io.modelcontextprotocol:kotlin-sdk
  - StreamableHttpClientTransport for HTTP-based MCP servers
  - Session management and resumption tokens
  - Multiple response modes (JSON, SSE inline, SSE separate)

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

**RAG & Vector Storage:**
- Exposed Framework 0.45.0 - Kotlin SQL framework with ORM
  - DSL for database operations
  - Transaction management
  - Type-safe queries
- SQLite JDBC 3.45.1.0 - Embedded database
  - Vector storage for embeddings
  - Cosine similarity search
  - No external database server required
- Local LM Studio - Local embedding model server
  - text-embedding-nomic-embed-text-v1.5 model
  - OpenAI-compatible API at http://127.0.0.1:1234/v1/embeddings
  - 1536-dimensional embeddings
  - No API costs for embedding generation

**Repositories:**
- Maven Central - Primary dependency source
- JitPack - Required for kotlin-telegram-bot library

## Recent Development History

Based on git commit history, recent features include:
- **RAG Integration into Main Bot** (latest): Built-in document search tool
  - Automatic semantic search via `search_documents` tool
  - Query → embedding → vector similarity → contextualized response
  - Configurable via environment variables
  - Graceful degradation if not enabled
- **RAG Implementation**: Complete RAG pipeline in utils module
  - Document indexing with text chunking
  - Local embedding generation (LM Studio)
  - Vector database with similarity search
  - Markdown-aware chunking for documentation
  - Sentence-first chunking strategy for semantic coherence
- **MCP SSE Integration**: Server-Sent Events transport for HTTP-based MCP servers
- **MCP Kotlin SDK Integration**: Official SDK v0.8.1 with HttpTransport implementation (alternative to SSE)
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
- Abstraction for different communication protocols via `McpTransport` interface
- **StdioTransport**: Launches external processes (npx, python), communicates via stdin/stdout with newline-delimited JSON-RPC
- **SseTransport** **[Currently Active]**: Server-Sent Events based transport
  - Uses two endpoints: GET /sse (for receiving) + POST /message (for sending)
  - Message endpoint discovered dynamically from SSE stream
  - Async connection management with request/response correlation
  - 10-second initialization timeout
- **HttpTransport**: Official MCP Kotlin SDK implementation
  - Uses `StreamableHttpClientTransport` from `io.modelcontextprotocol.kotlin.sdk`
  - Single POST endpoint for all operations
  - Supports session management, resumption tokens, multiple response modes
  - Currently implemented but not active (SseTransport is used for HTTP servers)
- Timeout handling (default: 30s), error recovery, graceful failure for all transports

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
      "url": "http://localhost:8080/sse",
      "headers": {"Authorization": "Bearer token"},
      "timeout": 15000
    }
  ]
}
```

**Note**: HTTP servers currently use `SseTransport` which requires:
- GET endpoint for SSE stream (receives messages from server)
- POST endpoint for sending messages (discovered via SSE "endpoint" event)
- Both endpoints typically share the same base URL (e.g., `/sse`)

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

## RAG (Retrieval-Augmented Generation) Architecture

### Overview

The `utils` module contains a complete, production-ready RAG pipeline for document indexing and semantic search. The implementation processes text documents, generates vector embeddings, and stores them in a searchable database.

### RAG Workflow

```
Input Document
  ↓
TextChunker (Markdown-aware or Sentence-based)
  ↓
Text Chunks with Metadata
  ↓
EmbeddingGenerator (Local LM Studio)
  ↓
Vector Embeddings (1536 dimensions)
  ↓
VectorDatabase (SQLite + Cosine Similarity)
  ↓
Searchable Knowledge Base
```

### Component Details

#### 1. TextChunker

**Two Chunking Strategies:**

**A. Sentence-First Strategy** (for plain text):
- Splits text into sentences using regex pattern matching
- Groups sentences together until reaching target chunk size
- Maintains complete sentences for semantic coherence
- Size-based fallback for sentences exceeding chunk size
- Sentence-aware overlap between chunks

**B. Markdown-Aware Strategy** (for documentation):
- Parses Markdown structure (headings, code blocks, lists, paragraphs)
- Groups content by document sections
- **Never splits code blocks** - keeps them intact
- Includes heading context in all chunks
- Respects document hierarchy
- Automatic detection based on `.md` file extension

**Key Features:**
- Configurable chunk size (default: 500 characters)
- Configurable overlap (default: 100 characters / 20%)
- Smart boundary detection (sentence → word → character)
- Metadata preservation (heading, type, position)
- Comprehensive logging

**Configuration:**
```kotlin
val chunker = TextChunker(
    chunkSize = 500,      // Target characters per chunk
    chunkOverlap = 100    // Overlap in characters
)
```

#### 2. MarkdownParser

**Purpose:** Parse Markdown into structured elements for structure-aware chunking

**Supported Elements:**
- Headings (`#`, `##`, `###`, etc.)
- Code blocks (` ```language ... ``` `)
- Lists (ordered and unordered)
- Paragraphs (regular text)
- Horizontal rules (`---`, `***`, `___`)

**Element Hierarchy:**
```kotlin
sealed class MarkdownElement {
    data class Heading(text, level)
    data class CodeBlock(text, language)
    data class List(text, ordered)
    data class Paragraph(text)
    data class HorizontalRule(text)
}
```

**Section Grouping:**
- Groups elements by heading boundaries
- Preserves parent heading for context
- Tracks position in original document

#### 3. EmbeddingGenerator

**Purpose:** Generate vector embeddings using local LM Studio server

**Configuration:**
- **Model:** text-embedding-nomic-embed-text-v1.5
- **Dimensions:** 1536
- **Endpoint:** http://127.0.0.1:1234/v1/embeddings
- **Batch size:** 100 texts per request
- **Rate limiting:** 100ms delay between batches

**Features:**
- Batch processing for efficiency
- Token usage tracking
- Async/await with coroutines
- Comprehensive error handling
- OpenAI-compatible API

**Benefits of Local Embeddings:**
- ✅ No API costs
- ✅ No rate limits
- ✅ Data privacy (no external API calls)
- ✅ Fast processing (local server)
- ✅ Offline capability

**Usage:**
```kotlin
val generator = EmbeddingGenerator(apiKey, httpClient)
val embeddings = generator.generateEmbeddings(chunks)
val queryEmbedding = generator.generateEmbedding("search query")
```

#### 4. VectorDatabase

**Purpose:** Store and search embeddings with semantic similarity

**Storage Backend:**
- SQLite database with Exposed ORM
- JSON serialization for vectors and metadata
- Transaction management
- Timestamp tracking

**Database Schema:**
```sql
CREATE TABLE embeddings (
    id INTEGER PRIMARY KEY,
    text TEXT NOT NULL,
    vector TEXT NOT NULL,        -- JSON array [1536 floats]
    model VARCHAR(100) NOT NULL,
    chunk_index INTEGER NOT NULL,
    metadata TEXT,                -- JSON object
    created_at INTEGER NOT NULL   -- Unix timestamp
);
```

**Search Algorithm:**
- **Cosine similarity** for semantic matching
- Top-K retrieval (configurable)
- Similarity threshold filtering
- Result ranking by relevance

**Key Methods:**
```kotlin
fun storeEmbeddings(embeddings: List<Embedding>)
fun search(queryEmbedding: List<Double>, topK: Int = 5, minSimilarity: Double = 0.0): List<SearchResult>
fun getCount(): Long
fun clear()
```

**Cosine Similarity Formula:**
```
similarity = (A · B) / (||A|| * ||B||)
where A and B are embedding vectors
```

#### 5. UtilApp - RAG Pipeline Orchestrator

**Purpose:** Command-line application for document indexing

**Workflow:**
1. Load environment variables (OPENAI_API_KEY for LM Studio)
2. Read input text file
3. Detect file type (Markdown vs plain text)
4. Chunk text using appropriate strategy
5. Generate embeddings in batches
6. Store embeddings in SQLite database
7. Run demo search to verify functionality
8. Display statistics and results

**Command-Line Usage:**
```bash
# Default settings (embeddings.md -> embeddings.db)
./gradlew :utils:run

# Custom input file and database
./gradlew :utils:run --args="docs.md vectors.db"

# Custom chunk size and overlap
./gradlew :utils:run --args="docs.md vectors.db 1000 200"
```

**Arguments:**
1. Input file (default: `embeddings.md`)
2. Database path (default: `embeddings.db`)
3. Chunk size (default: `500`)
4. Chunk overlap (default: `100`)

**Environment Variables:**
```bash
OPENAI_API_KEY=dummy  # LM Studio doesn't require real key, but field is checked
```

### RAG Best Practices Implemented

**Text Chunking:**
- ✅ Optimal chunk size (500 chars) balances context and specificity
- ✅ 20% overlap prevents information loss at boundaries
- ✅ Sentence boundary detection maintains coherence
- ✅ Markdown structure preservation for documentation
- ✅ Code block integrity (never split)

**Embedding Generation:**
- ✅ Local model (no API costs or rate limits)
- ✅ Batch processing for efficiency
- ✅ Proper error handling
- ✅ Token usage tracking

**Vector Storage:**
- ✅ Cosine similarity for semantic search
- ✅ Efficient serialization (JSON)
- ✅ Metadata support for filtering
- ✅ Timestamp tracking for freshness

**Retrieval Strategy:**
- ✅ Top-K selection
- ✅ Similarity thresholding
- ✅ Result ranking by relevance

### Performance Characteristics

**Chunking Performance:**
- 50KB document: ~50-100ms
- 500KB document: ~500ms-1s
- Suitable for real-time processing

**Embedding Generation:**
- Model: text-embedding-nomic-embed-text-v1.5 (1536 dim)
- Speed: Depends on local GPU/CPU
- No API costs
- No rate limits

**Vector Search:**
- Algorithm: Cosine similarity
- Complexity: O(n) for n stored vectors
- Typical latency: <100ms for 10K vectors
- Scalability: Suitable for up to ~100K embeddings in SQLite

### Usage Example

**Document Indexing:**
```bash
# Index technical documentation
./gradlew :utils:run --args="docs/api-reference.md embeddings.db 800 150"

# Index plain text
./gradlew :utils:run --args="knowledge.txt embeddings.db"
```

**Programmatic Usage:**
```kotlin
// Initialize components
val vectorDb = VectorDatabase("embeddings.db")
val embeddingGenerator = EmbeddingGenerator(apiKey, httpClient)

// User asks a question
val userQuery = "How do I authenticate users?"

// Generate query embedding
val queryEmbedding = embeddingGenerator.generateEmbedding(userQuery)

// Search for relevant chunks
val results = vectorDb.search(
    queryEmbedding = queryEmbedding.vector,
    topK = 5,
    minSimilarity = 0.7
)

// Build context for LLM
val context = results.joinToString("\n\n") { result ->
    "From: ${result.metadata["heading"]}\n${result.text}"
}

// Send to LLM with context
val prompt = """
Context from documentation:
$context

User question: $userQuery

Please answer based on the context provided.
""".trimIndent()
```

### Integration with Telegram Bot

**✅ ACTIVE: Built-in RAG Search Tool**

The RAG system is now fully integrated into the main Telegram bot application as a built-in tool. When enabled, the AI agent can automatically search indexed documents to answer user questions.

**How it Works:**

1. **Enable RAG in `.env`:**
   ```bash
   RAG_ENABLED=true
   RAG_DATABASE_PATH=embeddings.db
   RAG_EMBEDDING_API_URL=http://127.0.0.1:1234/v1/embeddings
   RAG_EMBEDDING_MODEL=text-embedding-nomic-embed-text-v1.5
   RAG_TOP_K=5
   RAG_MIN_SIMILARITY=0.7
   ```

2. **Index Documents First:**
   ```bash
   # Create embeddings database using utils module
   ./gradlew :utils:run --args="docs/knowledge-base.md embeddings.db"
   ```

3. **Start Bot with RAG:**
   ```bash
   # Ensure LM Studio is running at http://127.0.0.1:1234
   ./gradlew run
   ```

**Automatic Tool Usage:**

When a user asks a question, the AI agent:
1. Evaluates if document search would be helpful
2. Automatically calls `search_documents` tool with appropriate query
3. Receives relevant document chunks from vector database
4. Generates answer based on retrieved context
5. Returns contextualized response to user

**Example Conversation:**

```
User: How do I authenticate users in the application?

[Agent internally calls: search_documents("user authentication")]
[Searches embeddings.db, finds relevant chunks]
[Receives: Authentication documentation chunks with 0.85+ similarity]

Bot: Based on the documentation, user authentication is handled using...
[Answer includes information from retrieved chunks]
```

**Tool Definition:**

The `search_documents` tool is automatically available when RAG is enabled:
- **Name:** `search_documents`
- **Description:** Search through indexed documents using semantic similarity
- **Parameters:**
  - `query` (required): The search query to find relevant documents
- **Returns:** Formatted text with top-K most similar document chunks

**Architecture:**

```
User Question
  ↓
AiAgent.processMessage()
  ↓
OpenAI API (with search_documents tool available)
  ↓
[AI decides to use search_documents]
  ↓
AiAgent.executeSearchDocuments()
  ├─ Extract query from parameters
  ├─ EmbeddingGenerator.generateEmbedding(query)
  ├─ VectorDatabase.search(embedding, topK, minSimilarity)
  └─ Format results with metadata
  ↓
Results returned to OpenAI as tool output
  ↓
OpenAI generates final answer
  ↓
User receives contextualized response
```

**Configuration Options:**

All RAG settings are configurable via environment variables:
- `RAG_ENABLED`: Enable/disable RAG functionality (default: false)
- `RAG_DATABASE_PATH`: Path to embeddings database (default: embeddings.db)
- `RAG_EMBEDDING_API_URL`: LM Studio API endpoint (default: http://127.0.0.1:1234/v1/embeddings)
- `RAG_EMBEDDING_MODEL`: Embedding model name (default: text-embedding-nomic-embed-text-v1.5)
- `RAG_TOP_K`: Number of results to return (default: 5)
- `RAG_MIN_SIMILARITY`: Minimum similarity threshold 0.0-1.0 (default: 0.7)

**Requirements:**

1. LM Studio running with text-embedding-nomic-embed-text-v1.5 model loaded
2. Pre-indexed documents in embeddings database (created via `:utils:run`)
3. `RAG_ENABLED=true` in `.env` file
4. Sufficient context window for retrieved chunks + conversation

**Performance Considerations:**

- Embedding generation: ~100-500ms per query (depends on local GPU/CPU)
- Vector search: <100ms for 10K vectors
- Total overhead: ~200-600ms per RAG-enabled query
- Retrieved chunks included in OpenAI API context (counts toward token limit)

**Graceful Degradation:**

- If RAG not enabled: Bot works normally without document search
- If database not found: Warning logged, tool not added
- If LM Studio not running: Error returned to LLM, which handles gracefully
- If no results found: LLM informed, generates answer without context

**Future Enhancements:**

1. **Document Management Commands:**
   - `/index <file>` - Index new documents from chat
   - `/search <query>` - Direct semantic search without conversation
   - `/stats` - Show indexed document statistics

2. **Advanced Features:**
   - Multiple knowledge bases with namespacing
   - Automatic re-ranking with cross-encoder
   - Hybrid search (keyword + semantic)
   - Document metadata filtering

### Configuration Recommendations

**For Software Documentation:**
```kotlin
TextChunker(chunkSize = 800, chunkOverlap = 150)  // Larger for code blocks
```

**For API Documentation:**
```kotlin
TextChunker(chunkSize = 1000, chunkOverlap = 200)  // Full API examples
```

**For Tutorial Content:**
```kotlin
TextChunker(chunkSize = 600, chunkOverlap = 100)  // Standard size
```

**For General Text:**
```kotlin
TextChunker(chunkSize = 500, chunkOverlap = 100)  // Default balanced
```

### Testing

**Comprehensive Test Coverage:**
- TextChunker tests (sentence-based and Markdown-aware)
- MarkdownParser tests (element detection and section grouping)
- Integration tests with real documents
- Edge case handling (empty input, large code blocks, etc.)

**Built-in Demo Search:**
After indexing, the application runs a test search using the first chunk to verify:
- Embedding generation works correctly
- Database storage is functional
- Search returns expected results
- Similarity calculation is accurate

### Logging Output Example

```
INFO: Starting Embeddings Utility Application
INFO: Configuration:
  Input file: docs/api-guide.md
  Database: embeddings.db
  Chunk size: 500
  Chunk overlap: 100

INFO: === Step 1: Chunking text ===
INFO: Detected Markdown file, using structure-aware chunking
INFO: Parsed 15 sections from markdown
INFO: Created 42 chunks from markdown (structure-aware chunking)

INFO: === Step 2: Generating embeddings ===
INFO: Processing batch 1/1 (42 chunks)
INFO: Successfully generated 42 embeddings in batch 1

INFO: === Step 3: Storing embeddings in database ===
INFO: Total embeddings in database: 42

INFO: === Step 4: Testing search functionality ===
INFO: Search results:
  1. Similarity: 1.0000 - ## Authentication Overview
  2. Similarity: 0.8834 - ### JWT Tokens
  3. Similarity: 0.8512 - ## Authorization

INFO: === Process completed successfully ===
```

### Documentation Files

- **`RAG_IMPLEMENTATION.md`** - Complete implementation details and architecture
- **`MARKDOWN_CHUNKING.md`** - Markdown-aware chunking documentation
- **`TEXTCHUNKER_IMPROVEMENTS.md`** - Sentence-first strategy documentation
- **`utils/README.md`** - Usage guide for RAG utilities

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
- Components in dedicated packages: `agent/`, `bot/`, `repository/`, `mcp/`
- RAG utilities entry point: `utils/src/main/kotlin/io/github/devapro/ai/utils/UtilApp.kt`
- RAG components: `utils/src/main/kotlin/io/github/devapro/ai/utils/rag/`
- Each component in its own file
- Tests mirror source structure
- Build outputs:
  - `app/build/libs/app.jar` (fat JAR with all dependencies for bot)
  - `utils/build/libs/utils.jar` (utilities module)

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
- **RAG Search Instructions** (when search_documents is available):
  - **Search before saying "I don't know"** - Prioritize using knowledge base
  - **Proactive search** - Use for domain-specific, technical, or "how to" questions
  - **Synthesize and cite** - Combine search results with general knowledge
  - **Multiple searches** - Rephrase query if first attempt doesn't find relevant info
  - **Use specific queries** - Match search terms to user's question
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

**When debugging RAG utilities:**
- Ensure LM Studio is running at http://127.0.0.1:1234
- Verify text-embedding-nomic-embed-text-v1.5 model is loaded in LM Studio
- Check input file exists and is readable
- Review console output for chunking and embedding statistics
- Verify `embeddings.db` is created after successful run
- Use demo search output to verify similarity scores are reasonable (0.7-1.0 for relevant results)
- Check logs for batch processing progress and any API errors

**Project documentation files:**
- `README.md` - User-facing documentation with setup and usage instructions
- `CLAUDE.md` - This file - developer guidance for Claude Code
- `DI_IMPLEMENTATION.md` - Detailed dependency injection implementation notes
- `IMPLEMENTATION_SUMMARY.md` - Summary of implementation decisions and architecture
- `MARKDOWN_IMPLEMENTATION.md` - Details about markdown formatting implementation
- `PLANNING_AGENT.md` - Planning and agent architecture documentation
- `HTTP_TRANSPORT_SDK_IMPLEMENTATION.md` - MCP Kotlin SDK HttpTransport implementation details
- `HTTP_TRANSPORT_TROUBLESHOOTING.md` - Troubleshooting guide for HTTP transport issues
- `KOTLIN_SDK_RESEARCH.md` - Research notes on MCP Kotlin SDK integration
- `RAG_IMPLEMENTATION.md` - Complete RAG pipeline implementation and architecture
- `MARKDOWN_CHUNKING.md` - Markdown-aware chunking for documentation
- `TEXTCHUNKER_IMPROVEMENTS.md` - Sentence-first chunking strategy documentation
- `results.md` - AI model benchmarking results and performance data
- `utils/README.md` - RAG utilities usage guide

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
