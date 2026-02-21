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

    val chatHistory = ChatHistoryRepository()
    val history = chatHistory.load()

    if (history.isNotEmpty()) {
        println("Loaded ${history.size} messages from history.")
    }

    val documents = DocumentRepository(client)
    val indexed = documents.index()
    if (indexed > 0) {
        println("Indexed $indexed chunks from documents.")
    }

    println("Chat with Gemini (Ctrl+D to quit)")
    while (true) {
        print("> ")
        val input = readlnOrNull() ?: break

        // Search for relevant document chunks
        val relevantChunks = documents.search(input)
        val message = if (relevantChunks.isNotEmpty()) {
            val context = relevantChunks.joinToString("\n\n") { "[${it.source}]\n${it.text}" }
            "Context from documents:\n---\n$context\n---\nUser question: $input"
        } else {
            input
        }

        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.fromText(message)))
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

        chatHistory.save(history)
    }
}
