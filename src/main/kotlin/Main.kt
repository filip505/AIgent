import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part

fun main() {
    val client = Client.builder()
        .apiKey(System.getenv("GOOGLE_API_KEY"))
        .build()

    val history = mutableListOf<Content>()

    println("Chat with Gemini (Ctrl+D to quit)")
    while (true) {
        print("> ")
        val input = readlnOrNull() ?: break

        val userContent = Content.fromParts(Part.fromText(input))
        history.add(userContent)

        val response = client.models.generateContent(
            "gemini-2.5-flash",
            history,
            null
        )

        val text = response.text() ?: "(no response)"
        println(text)

        val assistantContent = Content.fromParts(Part.fromText(text))
        history.add(assistantContent)
    }
}
