# AIgent

A Kotlin-based AI assistant powered by Google Gemini that can run as a CLI application or Telegram bot.

## Features

- **Chat with Gemini AI** - Conversational AI powered by Google's Gemini 2.5 Flash model
- **Telegram Bot Integration** - Deploy as a Telegram bot for mobile access
- **RAG (Retrieval Augmented Generation)** - Query your own documents with semantic search
- **Reminders** - Schedule reminders that trigger at specific times
- **Web Search** - Agent can search the web for up-to-date information via Google grounding
- **GitHub Notes** - Read and write markdown documents to a GitHub repository
- **Sub-agent Spawning** - Delegate self-contained tasks to isolated sub-agents
- **Skills System** - Per-domain behavior injected into the system prompt based on context
- **Persistent Chat History** - Conversations are saved and restored between sessions
- **Customizable Personality** - Configure the AI's behavior via `soul.md`

## Requirements

- JDK 25+
- Google Gemini API key

## Setup

1. Set your Gemini API key:
   ```bash
   export GOOGLE_API_KEY=your_key_here
   ```

2. (Optional) Set the Telegram bot token to run as a Telegram bot:
   ```bash
   export TELEGRAM_BOT_TOKEN=your_token_here
   ```

3. (Optional) Set GitHub credentials for notes functionality:
   ```bash
   export GITHUB_NOTES_REPO=https://github.com/your-username/your-notes-repo
   export GITHUB_TOKEN=your_token_here
   ```

4. (Optional) Add documents to the `documents/` directory for RAG (supports `.txt` and `.md` files)

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
├── Main.kt                    # Entry point
├── AgentService.kt            # Core agent loop with dynamic skill injection
├── SubAgentService.kt         # Isolated sub-agent ReAct loop
├── SkillLoader.kt             # Loads and selects skills from skills/
├── TelegramBot.kt             # Telegram bot integration
├── ChatHistoryRepository.kt   # Chat persistence
├── DocumentRepository.kt      # RAG/embeddings
└── tools/
    ├── AgentTool.kt           # Tool interface
    ├── CurrentTimeTool.kt     # Time awareness
    ├── GoogleSearchTool.kt    # Web search via Gemini grounding
    ├── GitHubDocumentService.kt  # GitHub notes management
    ├── SchedulerService.kt    # Reminder scheduling
    └── SpawnAgentTool.kt      # Sub-agent spawning

skills/
├── reminders.md               # Behavioral guidance for reminder tasks
└── github-notes.md            # Behavioral guidance for notes tasks
```

## Configuration

### Personality
Edit `soul.md` to customize the AI assistant's personality and behavior.

### Skills
Add `.md` files to the `skills/` directory to define domain-specific behavior. Each skill file requires YAML frontmatter:

```markdown
---
name: my-skill
description: What this skill is for (used for relevance matching)
tools: [tool_name_1, tool_name_2]
---

Instructions for the agent when this skill is active...
```

Skills are automatically selected per request based on keyword matching against the user's input.

## Agentic Architecture

The agent uses a **ReAct loop** (Reason → Act → Observe) built on Gemini function calling. Key patterns implemented:

- **Tool interface** — all capabilities implement `AgentTool` for uniform registration and dispatch
- **Skills system** — domain-specific instructions injected dynamically into the system prompt
- **Sub-agent spawning** — the `spawn_agent` tool lets the main agent delegate self-contained tasks to isolated agents with a restricted set of tools; sub-agents cannot spawn further sub-agents
