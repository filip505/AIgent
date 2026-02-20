import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam

fun main() {
    val client = AnthropicOkHttpClient.fromEnv()
    val messages = mutableListOf<MessageParam>()

    println("Chat with Claude (Ctrl+D to quit)")
    while (true) {
        print("> ")
        val input = readlnOrNull() ?: break

        messages.add(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(input)
                .build()
        )

        val response = client.messages().create(
            MessageCreateParams.builder()
                .model("claude-opus-4-6")
                .maxTokens(1024)
                .messages(messages)
                .build()
        )

        val text = response.content()
            .filter { it.isText() }
            .joinToString("") { it.asText().text() }

        println(text)

        messages.add(
            MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(text)
                .build()
        )
    }
}
