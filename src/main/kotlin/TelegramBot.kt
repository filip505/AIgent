import tools.Reminder
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId

fun startTelegramBot(token: String) {
    lateinit var botInstance: Bot

    val agent = AgentService { reminder ->
        val chatId = reminder.chatId
        if (chatId != null) {
            botInstance.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "🔔 Reminder: ${reminder.message}"
            )
        }
    }

    botInstance = bot {
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
                    val reply = agent.chat(text, chatId = message.chat.id)
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

    val status = agent.init()
    if (status.isNotEmpty()) println(status)

    println("Telegram bot started. Send a message to your bot!")
    botInstance.startPolling()
}
