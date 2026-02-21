import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GoogleSearch
import com.google.genai.types.Part
import com.google.genai.types.Tool

fun main() {
    val client = Client.builder()
        .apiKey(System.getenv("GOOGLE_API_KEY"))
        .build()

    val googleSearch = Tool.builder()
        .googleSearch(GoogleSearch.builder().build())
        .build()

    val config = GenerateContentConfig.builder()
        .tools(listOf(googleSearch))
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
            config
        )

        val text = response.text() ?: "(no response)"
        println(text)

        val assistantContent = Content.fromParts(Part.fromText(text))
        history.add(assistantContent)
    }
}
