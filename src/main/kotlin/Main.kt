import tools.Reminder

fun main() {
    val telegramToken = System.getenv("TELEGRAM_BOT_TOKEN")

    if (!telegramToken.isNullOrBlank()) {
        startTelegramBot(telegramToken)
    } else {
        val agent = AgentService { reminder ->
            println("\n🔔 Reminder: ${reminder.message}")
            print("> ")
        }
        val status = agent.init()
        if (status.isNotEmpty()) println(status)

        println("Chat with Gemini (Ctrl+D to quit)")
        while (true) {
            print("> ")
            val input = readlnOrNull() ?: break
            println(agent.chat(input))
        }
    }
}
