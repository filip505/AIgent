import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId

fun startTelegramBot(token: String, agent: AgentService) {
    val bot = bot {
        this.token = token
        dispatch {
            command("start") {
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "Hello! I'm your AI assistant. Send me a message!"
                )
            }

            command("clear") {
                agent.clearHistory()
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "Conversation history cleared."
                )
            }

            message {
                val text = message.text ?: return@message
                if (text.startsWith("/")) return@message

                try {
                    val reply = agent.chat(text)
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = reply
                    )
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                    e.printStackTrace()
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "Sorry, something went wrong: ${e.message}"
                    )
                }
            }
        }
    }

    println("Telegram bot started. Send a message to your bot!")
    bot.startPolling()
}
