# AIgent

A Kotlin-based AI assistant powered by Google Gemini that can run as a CLI application or Telegram bot.

## Features

- **Chat with Gemini AI** - Conversational AI powered by Google's Gemini model
- **Telegram Bot Integration** - Deploy as a Telegram bot for mobile access
- **RAG (Retrieval Augmented Generation)** - Query your own documents with semantic search
- **Reminders** - Schedule reminders that trigger at specific times
- **Google Search** - AI can search the web for up-to-date information
- **Persistent Chat History** - Conversations are saved and restored between sessions
- **Customizable Personality** - Configure the AI's behavior via `soul.md`

## Requirements

- JDK 25+
- Google Cloud service account with Gemini API access

## Setup

1. Place your Google Cloud service account credentials in `service-account.json`

2. (Optional) Set the Telegram bot token to run as a Telegram bot:
   ```bash
   export TELEGRAM_BOT_TOKEN=your_token_here
   ```

3. (Optional) Add documents to the `documents/` directory for RAG functionality (supports `.txt` and `.md` files)

## Running

### CLI Mode
```bash
./gradlew run
```

### Telegram Bot Mode
```bash
TELEGRAM_BOT_TOKEN=your_token ./gradlew run
```

## Project Structure

```
src/main/kotlin/
├── Main.kt              # Entry point
├── AgentService.kt      # Core AI agent logic
├── TelegramBot.kt       # Telegram bot integration
├── ChatHistoryRepository.kt  # Chat persistence
├── DocumentRepository.kt     # RAG/embeddings
└── tools/
    ├── CurrentTimeTool.kt    # Time awareness
    ├── GoogleSearchTool.kt   # Web search
    └── SchedulerService.kt   # Reminder scheduling
```

## Configuration

Edit `soul.md` to customize the AI assistant's personality and behavior.