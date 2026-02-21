fun main() {
    val agent = AgentService()
    val status = agent.init()
    if (status.isNotEmpty()) println(status)

    val telegramToken = System.getenv("TELEGRAM_BOT_TOKEN")

    if (!telegramToken.isNullOrBlank()) {
        startTelegramBot(telegramToken, agent)
    } else {
        println("Chat with Gemini (Ctrl+D to quit)")
        while (true) {
            print("> ")
            val input = readlnOrNull() ?: break
            println(agent.chat(input))
        }
    }
}
