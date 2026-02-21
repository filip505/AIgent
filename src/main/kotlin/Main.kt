import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GoogleSearch
import com.google.genai.types.Part
import com.google.genai.types.Tool
import java.io.File

data class Message(val role: String, val text: String)

private val historyFile = File("chat_history.json")

fun saveHistory(history: List<Content>) {
    val messages = history.map { content ->
        val role = content.role().orElse("user")
        val text = content.parts().orElse(listOf())
            .mapNotNull { it.text().orElse(null) }
            .joinToString("")
        """  {"role": "$role", "text": ${escapeJson(text)}}"""
    }
    historyFile.writeText("[\n${messages.joinToString(",\n")}\n]\n")
}

fun loadHistory(): MutableList<Content> {
    if (!historyFile.exists()) return mutableListOf()

    val json = historyFile.readText().trim()
    if (json.isEmpty() || json == "[]") return mutableListOf()

    val history = mutableListOf<Content>()
    val pattern = Regex(""""role"\s*:\s*"([^"]+)"\s*,\s*"text"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    for (match in pattern.findAll(json)) {
        val role = match.groupValues[1]
        val text = unescapeJson(match.groupValues[2])
        history.add(
            Content.builder()
                .role(role)
                .parts(listOf(Part.fromText(text)))
                .build()
        )
    }

    return history
}

fun escapeJson(s: String): String {
    val escaped = s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}

fun unescapeJson(s: String): String {
    return s.replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

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

    val history = loadHistory()

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

        saveHistory(history)
    }
}
