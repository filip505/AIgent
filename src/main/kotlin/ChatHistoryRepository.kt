import com.google.genai.types.Content
import com.google.genai.types.Part
import java.io.File

class ChatHistoryRepository(private val file: File = File("chat_history.json")) {

    fun save(history: List<Content>) {
        val messages = history.map { content ->
            val role = content.role().orElse("user")
            val text = content.parts().orElse(listOf())
                .mapNotNull { it.text().orElse(null) }
                .joinToString("")
            """  {"role": "$role", "text": ${escapeJson(text)}}"""
        }
        file.writeText("[\n${messages.joinToString(",\n")}\n]\n")
    }

    fun load(): MutableList<Content> {
        if (!file.exists()) return mutableListOf()

        val json = file.readText().trim()
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

    private fun escapeJson(s: String): String {
        val escaped = s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
