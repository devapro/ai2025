# MCP Server Configuration Guide

## Overview

The Model Context Protocol (MCP) is an open protocol that enables AI assistants to securely connect to external tools and data sources. This bot supports MCP, allowing the AI agent to use various tools dynamically based on conversation needs.

## How It Works

1. **Configuration**: You define MCP servers in `mcp-config.json`
2. **Automatic Discovery**: On startup, the bot connects to enabled servers and discovers available tools
3. **Smart Execution**: When you ask a question, the AI automatically decides if it needs to use tools
4. **Seamless Integration**: Tool results are incorporated into the conversation naturally

## Quick Start

### 1. Create Configuration File

Copy the example configuration:
```bash
cp mcp-config.json.example mcp-config.json
```

### 2. Enable a Server

Edit `mcp-config.json` and set `"enabled": true` for the servers you want to use.

### 3. Configure Credentials

Add any required API keys or connection strings to the configuration.

### 4. Restart the Bot

The bot will automatically load and connect to the enabled MCP servers.

## Configuration File Structure

```json
{
  "mcpServers": [
    {
      "name": "unique-server-name",
      "description": "Human-readable description",
      "enabled": true,
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-name"],
      "env": {
        "API_KEY": "your-api-key"
      },
      "timeout": 30000
    }
  ]
}
```

### Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier for the server |
| `description` | No | Human-readable description |
| `enabled` | Yes | Whether to load this server (true/false) |
| `type` | Yes | Transport type: "stdio" or "http" |
| `command` | Yes (stdio) | Command to execute (e.g., "npx", "python") |
| `args` | No | Array of command-line arguments |
| `env` | No | Environment variables for the process |
| `url` | Yes (http) | Base URL for HTTP servers |
| `headers` | No | HTTP headers (for authentication, etc.) |
| `timeout` | No | Request timeout in milliseconds (default: 30000) |

## Available MCP Servers

### 1. Filesystem Server

Access and manipulate files on your local filesystem.

**Installation**: Built-in (via npx)

**Configuration**:
```json
{
  "name": "filesystem",
  "enabled": true,
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/directory"],
  "timeout": 30000
}
```

**Capabilities**:
- Read files
- Write files
- List directories
- Search files
- Create/delete files and directories

**Example Usage**:
- "List the files in my Documents folder"
- "Read the contents of todo.txt"
- "Create a new file called notes.md with some content"

### 2. Brave Search Server

Web search capabilities using Brave Search API.

**Installation**: Built-in (via npx)

**Prerequisites**: Brave Search API key (get one at https://brave.com/search/api/)

**Configuration**:
```json
{
  "name": "brave-search",
  "enabled": true,
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-brave-search"],
  "env": {
    "BRAVE_API_KEY": "your_api_key_here"
  },
  "timeout": 30000
}
```

**Capabilities**:
- Web search
- News search
- Local search

**Example Usage**:
- "Search for the latest Kotlin tutorials"
- "Find recent news about AI developments"
- "What's the weather forecast for tomorrow?"

### 3. GitHub Server

Access GitHub repositories, issues, and pull requests.

**Installation**: Built-in (via npx)

**Prerequisites**: GitHub Personal Access Token

**Configuration**:
```json
{
  "name": "github",
  "enabled": true,
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-github"],
  "env": {
    "GITHUB_PERSONAL_ACCESS_TOKEN": "your_token_here"
  },
  "timeout": 30000
}
```

**Capabilities**:
- List repositories
- Read file contents
- Search code
- List issues and PRs
- Create issues

**Example Usage**:
- "Show me my latest GitHub repositories"
- "Search for Kotlin files in my repo"
- "Create an issue for bug tracking"

### 4. PostgreSQL Server

Query and interact with PostgreSQL databases.

**Installation**: Built-in (via npx)

**Prerequisites**: PostgreSQL database connection string

**Configuration**:
```json
{
  "name": "postgres",
  "enabled": true,
  "type": "stdio",
  "command": "npx",
  "args": [
    "-y",
    "@modelcontextprotocol/server-postgres",
    "postgresql://user:password@localhost/database"
  ],
  "timeout": 30000
}
```

**Capabilities**:
- Execute SELECT queries
- List tables and schemas
- Describe table structures

**Example Usage**:
- "Show me all tables in the database"
- "Query the users table for active accounts"
- "What columns does the orders table have?"

### 5. Custom HTTP Server

Connect to your own MCP-compatible HTTP server.

**Configuration**:
```json
{
  "name": "custom-api",
  "enabled": true,
  "type": "http",
  "url": "https://api.example.com/mcp",
  "headers": {
    "Authorization": "Bearer your_token",
    "X-API-Version": "1.0"
  },
  "timeout": 15000
}
```

**Use Case**: Connect to your own custom tools and services via HTTP.

## Security Best Practices

### 1. Protect Configuration File

The `mcp-config.json` file may contain sensitive information:
- API keys
- Database credentials
- Authentication tokens

**Important**: This file is git-ignored by default. Never commit it to version control!

### 2. Use Environment Variables

For extra security, you can reference environment variables in your `.env` file and configure servers to use them:

```bash
# In .env
BRAVE_API_KEY=your_actual_key_here
```

Then use it in configuration (future enhancement).

### 3. Limit Filesystem Access

When using the filesystem server, always specify a restricted directory:
```json
"args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/username/Documents"]
```

Never use:
- Root directory (`/`)
- System directories (`/etc`, `/usr`, etc.)
- Entire home directory without good reason

### 4. Use Read-Only When Possible

For databases and other sensitive resources, use read-only credentials when the AI doesn't need write access.

### 5. Regular Security Audits

- Review enabled servers regularly
- Rotate API keys periodically
- Monitor tool usage in logs

## Troubleshooting

### Server Not Initializing

**Symptoms**: Logs show "Failed to initialize server"

**Solutions**:
1. Check command is installed (`npx` for Node packages)
2. Verify all required `args` are present
3. Check environment variables are set correctly
4. Look at server-specific error messages in logs

### Tools Not Available

**Symptoms**: Bot says "No MCP tools available"

**Solutions**:
1. Verify at least one server has `"enabled": true`
2. Check configuration file syntax (use a JSON validator)
3. Ensure `mcp-config.json` exists in the project root
4. Check file permissions

### Command Not Found

**Symptoms**: Error about missing command (e.g., "npx not found")

**Solutions**:
1. Install Node.js and npm
2. Verify command is in PATH
3. Use full path to command: `/usr/local/bin/npx`

### Tool Execution Timeouts

**Symptoms**: "Request timed out" errors

**Solutions**:
1. Increase `timeout` value in configuration
2. Check network connectivity (for HTTP servers)
3. Verify server is responsive

### Permission Denied

**Symptoms**: File or directory access errors

**Solutions**:
1. Check file/directory permissions
2. Ensure bot process has required access
3. For filesystem server, use accessible directories

## Advanced Configuration

### Multiple Servers of Same Type

You can configure multiple instances of the same server type:

```json
{
  "mcpServers": [
    {
      "name": "work-files",
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/work/documents"]
    },
    {
      "name": "personal-files",
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home/user/personal"]
    }
  ]
}
```

### Custom Timeouts

Adjust timeouts based on server characteristics:
- Fast local tools: 10000 (10 seconds)
- Network APIs: 30000 (30 seconds)
- Heavy processing: 60000 (60 seconds)

### Disabling Servers Temporarily

Instead of deleting configuration, set `"enabled": false`:

```json
{
  "name": "expensive-api",
  "enabled": false,
  "type": "http",
  ...
}
```

## Examples

### Example 1: Research Assistant

Configure filesystem and search servers for research tasks:

```json
{
  "mcpServers": [
    {
      "name": "research-notes",
      "enabled": true,
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home/user/research"]
    },
    {
      "name": "web-search",
      "enabled": true,
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-brave-search"],
      "env": {
        "BRAVE_API_KEY": "your_key"
      }
    }
  ]
}
```

**Usage**: "Search for papers on neural networks and save summaries to my research notes"

### Example 2: Development Assistant

Configure GitHub and filesystem for coding tasks:

```json
{
  "mcpServers": [
    {
      "name": "github",
      "enabled": true,
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "your_token"
      }
    },
    {
      "name": "project-files",
      "enabled": true,
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home/user/projects"]
    }
  ]
}
```

**Usage**: "Check my latest commits and create a changelog file"

### Example 3: Data Analysis Assistant

Configure database access for analytics:

```json
{
  "mcpServers": [
    {
      "name": "analytics-db",
      "enabled": true,
      "type": "stdio",
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-postgres",
        "postgresql://readonly:password@localhost/analytics"
      ],
      "timeout": 60000
    }
  ]
}
```

**Usage**: "Query the sales data for Q4 and summarize trends"

## FAQ

**Q: Do I need to restart the bot when changing configuration?**
A: Yes, MCP servers are initialized at startup. Restart the bot to apply changes.

**Q: Can I use multiple MCP servers simultaneously?**
A: Yes! The AI can use tools from multiple servers in a single conversation.

**Q: How do I know which tools are available?**
A: Check the startup logs. The bot logs all discovered tools from each server.

**Q: Does using MCP increase costs?**
A: Tool calls increase token usage, which may increase OpenAI API costs. Monitor token usage in responses.

**Q: Can I build my own MCP server?**
A: Yes! Follow the MCP specification at https://modelcontextprotocol.io

**Q: What happens if a tool fails?**
A: The AI receives an error message and can explain the issue or try a different approach.

**Q: Is my data sent to OpenAI?**
A: Tool definitions and results are sent to OpenAI as part of the conversation. Don't use tools with highly sensitive data.

## Getting Help

- **Bot Issues**: Check application logs for detailed error messages
- **MCP Protocol**: Visit https://modelcontextprotocol.io
- **Server Issues**: Check the specific MCP server's documentation

## Additional Resources

- [Model Context Protocol Specification](https://modelcontextprotocol.io)
- [Official MCP Servers](https://github.com/modelcontextprotocol/servers)
- [MCP Community](https://github.com/modelcontextprotocol)
