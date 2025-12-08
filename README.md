# Bash Script Creation Assistant - Telegram Bot

An AI-powered Telegram bot that helps you create production-ready bash scripts. The bot collects requirements through interactive questioning and generates complete, well-documented bash scripts with proper error handling and best practices.

## Features

- **AI-Powered Script Generation**: Uses OpenAI's GPT models via direct API integration with JSON mode
- **Interactive Requirements Gathering**: Asks targeted questions to understand your automation needs
- **Production-Ready Scripts**: Generates bash scripts with proper error handling, input validation, and security best practices
- **Markdown Formatting**: All responses support rich markdown formatting (bold, italic, code blocks, lists, links)
- **Structured Responses**: AI returns structured JSON with response type and content
- **Conversation History**: Maintains separate conversation history for each user
- **Persistent Storage**: Stores conversation history and prompts in markdown files
- **Docker Support**: Fully containerized with Docker and Docker Compose
- **Modular Architecture**: Clean separation of concerns with dedicated packages for each component

## Prerequisites

- Java 21 or higher
- Gradle 8.7 or higher (or use the included Gradle Wrapper)
- OpenAI API key
- Telegram Bot token (get it from [@BotFather](https://t.me/BotFather))
- Docker and Docker Compose (for containerized deployment)

## Project Structure

```
Ai1/
├── app/                          # Main application module
│   └── src/main/kotlin/io/github/devapro/ai/
│       ├── agent/                # AI agent component
│       │   └── AiAgent.kt
│       ├── bot/                  # Telegram bot component
│       │   └── TelegramBot.kt
│       ├── repository/           # File storage component
│       │   └── FileRepository.kt
│       ├── di/                   # Dependency injection
│       │   └── AppDi.kt
│       └── App.kt                # Application entry point
├── utils/                        # Shared utilities module
├── buildSrc/                     # Gradle convention plugins
├── docker/                       # Docker configuration
│   ├── Dockerfile
│   └── docker-compose.yml
├── promts/                       # Prompt templates (markdown files)
│   ├── system.md                 # System prompt for AI behavior
│   └── assistant.md              # Assistant greeting for new conversations
└── history/                      # User conversation history (git-ignored)
```

## Setup

### 1. Clone the repository

```bash
cd Ai1
```

### 2. Configure environment variables

Copy the example environment file and fill in your credentials:

```bash
cp .env.example .env
```

Edit `.env` and add your credentials:

```env
OPENAI_API_KEY=your_openai_api_key_here
TELEGRAM_BOT_TOKEN=your_telegram_bot_token_here
```

### 3. Customize the prompts (optional)

Edit `promts/system.md` to customize the AI assistant's behavior and personality.
Edit `promts/assistant.md` to customize the initial greeting message shown to new users.

## Running the Application

### Option 1: Run with Gradle

```bash
./gradlew run
```

### Option 2: Build and run the JAR

```bash
# Build the application
./gradlew build

# Run the JAR
java -jar app/build/libs/app.jar
```

### Option 3: Run with Docker Compose (Recommended)

```bash
# Build and start the container
cd docker
docker-compose up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the container
docker-compose down
```

## Using the Bot

1. Start a conversation with your bot on Telegram
2. Send `/start` to initialize the bot
3. Describe what task you want to automate or send any message
4. The bot will ask questions to understand your requirements
5. Answer the questions to provide necessary details
6. Receive a complete bash script with documentation
7. Use `/clear` to clear your conversation history
8. Use `/help` to see available commands

### Available Commands

- `/start` - Start conversation with the bot
- `/help` - Show help message with available commands
- `/script` - Activate script creation mode to create bash scripts
- `/clear` - Clear conversation history

### Script Creation Mode

The bot includes a specialized **Script Creation Agent** that helps you create production-ready bash scripts:

1. **Activate Script Mode**: Send `/script` or ask "create a bash script that..."
2. **Describe Your Task**: Tell the bot what you want to automate
3. **Answer Questions**: The agent will ask clarifying questions about:
   - Task purpose and requirements
   - Inputs and outputs
   - Environment (OS, available tools)
   - Error handling preferences
   - Security considerations
   - Edge cases to handle
4. **Receive Your Script**: Once enough information is gathered, receive a complete bash script including:
   - Proper shebang and error handling (set -euo pipefail)
   - Input validation and argument parsing
   - Usage documentation and help function
   - Color-coded logging functions
   - Inline comments for complex logic
   - Security best practices (proper quoting, validation)
   - Testing examples

## Development

### Build the project

```bash
./gradlew build
```

### Run tests

```bash
./gradlew test
```

### Run checks (includes tests)

```bash
./gradlew check
```

### Clean build outputs

```bash
./gradlew clean
```

## Architecture

The application follows a modular architecture with clear separation of concerns:

- **DI Layer** (`di/`): Koin dependency injection configuration in `AppDi.kt`
- **Bot Component** (`bot/`): Handles Telegram API interactions and user commands
- **Agent Component** (`agent/`): Manages AI conversations using OpenAI API directly via Ktor
- **Repository Component** (`repository/`): Handles file-based storage for prompts and history

All components are managed as singletons by Koin DI framework with automatic dependency resolution.

### Technology Stack

- **Kotlin 2.2.0**: Primary programming language
- **Koin 4.0.0**: Lightweight dependency injection framework
- **OpenAI API**: Direct integration for AI-powered conversations (gpt-4o-mini with JSON mode)
- **kotlin-telegram-bot 6.3.0**: Telegram Bot API client
- **Ktor 3.3.0**: HTTP client for OpenAI API communication
- **kotlinx-coroutines**: Async/await support
- **kotlinx-serialization**: JSON serialization for API requests/responses
- **dotenv-kotlin**: Environment variable management
- **Gradle**: Build system with multi-module setup

### AI Capabilities

- **General Assistant**: Answers questions, provides information, engages in conversation
- **Script Creation Agent**: Creates production-ready bash scripts through interactive questioning
- **Context Awareness**: Maintains conversation history for coherent multi-turn dialogues
- **Structured Output**: Returns responses as JSON with type classification (answer/question/script)
- **Best Practices**: Generates scripts following bash best practices (shellcheck compatible)
- **Security Focus**: Includes input validation, proper quoting, and error handling

## Configuration

### Environment Variables

- `OPENAI_API_KEY` (required): Your OpenAI API key
- `TELEGRAM_BOT_TOKEN` (required): Your Telegram bot token
- `PROMPTS_DIR` (optional): Directory for prompt files (default: `promts`)
- `HISTORY_DIR` (optional): Directory for history files (default: `history`)

### Conversation History

Conversation history is stored in markdown files in the `history/` directory:
- Each user gets a separate file: `user_{userId}.md`
- Files are automatically created on first message
- Use the `/clear` command to delete a user's history

## Docker Deployment

The application is fully containerized and production-ready:

### Multi-stage Build
- Stage 1: Builds the application using Gradle
- Stage 2: Creates a minimal runtime image with only the JRE

### Volumes
- `/app/history`: Mounted to persist conversation history
- `/app/promts`: Mounted to allow updating prompts without rebuilding

### Logging
- JSON file driver with rotation (max 10MB, 3 files)

## Troubleshooting

### Bot not responding

1. Check that your bot token is correct
2. Verify the bot is running: `docker-compose logs telegram-bot`
3. Ensure your OpenAI API key is valid and has credits

### Build errors

```bash
# Clean and rebuild
./gradlew clean build
```

### Docker issues

```bash
# Rebuild from scratch
docker-compose down
docker-compose build --no-cache
docker-compose up
```

## License

This project uses [Gradle](https://gradle.org/) and follows the suggested multi-module setup.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks)
