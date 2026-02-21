import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GoogleSearch
import com.google.genai.types.Part
import com.google.genai.types.Tool
import java.io.File

fun main() {
    val client = Client.builder()
        .apiKey(System.getenv("GOOGLE_API_KEY"))
        .build()

    val googleSearch = Tool.builder()
        .googleSearch(GoogleSearch.builder().build())
        .build()

    val config = GenerateContentConfig.builder()
        .systemInstruction(Content.fromParts(Part.fromText(File("soul.md").readText())))
        .tools(listOf(googleSearch))
        .build()

    val repository = ChatHistoryRepository()
    val history = repository.load()

    if (history.isNotEmpty()) {
        println("Loaded ${history.size} messages from history.")
    }

    println("Chat with Gemini (Ctrl+D to quit)")
    while (true) {
        print("> ")
        val input = readlnOrNull() ?: break

        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.fromText(input)))
            .build()
        history.add(userContent)

        val response = client.models.generateContent(
            "gemini-2.5-flash",
            history,
            config
        )

        val text = response.text() ?: "(no response)"
        println(text)

        val assistantContent = Content.builder()
            .role("model")
            .parts(listOf(Part.fromText(text)))
            .build()
        history.add(assistantContent)

        repository.save(history)
    }
}
