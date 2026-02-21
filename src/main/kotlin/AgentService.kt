import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GoogleSearch
import com.google.genai.types.Part
import com.google.genai.types.Tool
import java.io.File

class AgentService {
    private val client = Client.builder()
        .apiKey(System.getenv("GOOGLE_API_KEY"))
        .build()

    private val config = GenerateContentConfig.builder()
        .systemInstruction(Content.fromParts(Part.fromText(File("soul.md").readText())))
        .tools(listOf(Tool.builder().googleSearch(GoogleSearch.builder().build()).build()))
        .build()

    private val chatHistory = ChatHistoryRepository()
    private val history = chatHistory.load()

    private val documents = DocumentRepository(client)

    fun init(): String {
        val messages = mutableListOf<String>()

        if (history.isNotEmpty()) {
            messages.add("Loaded ${history.size} messages from history.")
        }

        val indexed = documents.index()
        if (indexed > 0) {
            messages.add("Indexed $indexed chunks from documents.")
        }

        return messages.joinToString("\n")
    }

    fun chat(input: String): String {
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

        val assistantContent = Content.builder()
            .role("model")
            .parts(listOf(Part.fromText(text)))
            .build()
        history.add(assistantContent)

        chatHistory.save(history)

        return text
    }

    fun clearHistory() {
        history.clear()
        chatHistory.save(history)
    }
}
