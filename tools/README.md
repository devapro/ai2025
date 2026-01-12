# File Tools MCP Server

A Model Context Protocol (MCP) server providing file system tools for AI agents.

## Available Tools

### 1. find_file
Search for files in a directory and its subdirectories using glob patterns.

**Parameters:**
- `path` (required): Directory path to search in
- `pattern` (required): Glob pattern (e.g., `*.kt`, `Test*.java`, `config.*`)
- `maxDepth` (optional): Maximum recursion depth (default: unlimited)
- `maxResults` (optional): Maximum results to return (default: 100)

**Examples:**
```json
{"path": ".", "pattern": "*.kt"}
{"path": "src", "pattern": "*Test.kt", "maxDepth": 5}
{"path": ".", "pattern": "Main.kt", "maxResults": 1}
```

### 2. read_file
Read contents of a file with optional line range.

**Parameters:**
- `path` (required): File path to read
- `startLine` (optional): Starting line number (1-indexed)
- `endLine` (optional): Ending line number (1-indexed, inclusive)
- `includeLineNumbers` (optional): Include line numbers in output (default: true)

**Examples:**
```json
{"path": "src/Main.kt"}
{"path": "README.md", "startLine": 1, "endLine": 10}
{"path": "config.json", "includeLineNumbers": false}
```

**Limits:**
- Maximum file size: 10 MB

## Usage

### Running Directly

```bash
./gradlew :tools:run
```

The server will start and listen for JSON-RPC 2.0 messages on stdin/stdout.

### Integrating with Main Agent

Add to your `mcp-config.json`:

```json
{
  "mcpServers": [
    {
      "name": "file-tools",
      "description": "File system tools for finding and reading files",
      "enabled": true,
      "type": "stdio",
      "command": "./gradlew",
      "args": [":tools:run", "--console=plain", "--quiet"],
      "timeout": 30000
    }
  ]
}
```

### Testing the Server

You can test the server manually using stdin/stdout:

1. Start the server:
   ```bash
   ./gradlew :tools:run --console=plain --quiet
   ```

2. Send initialization request (paste and press Enter):
   ```json
   {"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}
   ```

3. Send initialized notification:
   ```json
   {"jsonrpc":"2.0","id":null,"method":"initialized"}
   ```

4. List available tools:
   ```json
   {"jsonrpc":"2.0","id":"2","method":"tools/list"}
   ```

5. Call find_file tool:
   ```json
   {"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"find_file","arguments":{"path":".","pattern":"*.md"}}}
   ```

6. Call read_file tool:
   ```json
   {"jsonrpc":"2.0","id":"4","method":"tools/call","params":{"name":"read_file","arguments":{"path":"README.md","startLine":1,"endLine":10}}}
   ```

## Architecture

```
tools/
├── src/main/kotlin/io/github/devapro/ai/tools/
│   ├── Tool.kt                    # Tool interface
│   ├── McpServer.kt               # MCP server implementation
│   ├── Main.kt                    # Entry point
│   ├── model/
│   │   └── McpProtocol.kt         # JSON-RPC protocol models
│   └── impl/
│       ├── FindFileTool.kt        # Find file tool
│       └── ReadFileTool.kt        # Read file tool
└── build.gradle.kts               # Build configuration
```

## Implementation Details

### Tool Interface

All tools implement the `Tool` interface:

```kotlin
interface Tool {
    val name: String
    val description: String
    val inputSchema: JsonObject  // JSON Schema
    suspend fun execute(arguments: JsonObject?): String
}
```

### MCP Server

The `McpServer` class:
- Implements JSON-RPC 2.0 protocol over stdin/stdout
- Handles MCP handshake (initialize, initialized)
- Discovers and routes tool calls
- Provides error handling and logging

### Communication Flow

1. Client sends `initialize` request
2. Server responds with capabilities and server info
3. Client sends `initialized` notification
4. Client requests `tools/list` to discover available tools
5. Client calls tools via `tools/call` with tool name and arguments
6. Server executes tool and returns result

## Adding New Tools

To add a new tool:

1. Create a new class implementing the `Tool` interface:
   ```kotlin
   class MyTool : Tool {
       override val name = "my_tool"
       override val description = "..."
       override val inputSchema = buildJsonObject { ... }
       override suspend fun execute(arguments: JsonObject?): String { ... }
   }
   ```

2. Register it in `Main.kt`:
   ```kotlin
   val tools = listOf(
       FindFileTool(workingDirectory = workingDir),
       ReadFileTool(workingDirectory = workingDir),
       MyTool()  // Add your tool here
   )
   ```

3. Rebuild and restart the server:
   ```bash
   ./gradlew :tools:build
   ```

## Dependencies

- Kotlin 2.2.0
- kotlinx-serialization-json
- kotlinx-coroutines
- SLF4J for logging

## Working Directory

The server is configured to run from the project root directory (see `build.gradle.kts`). All file paths are resolved relative to this directory.
