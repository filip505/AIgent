package tools

import com.google.genai.Client
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GoogleSearch
import com.google.genai.types.Schema
import com.google.genai.types.Tool
import com.google.genai.types.Type

class GoogleSearchTool(private val client: Client) : AgentTool {

    override val declaration: FunctionDeclaration = FunctionDeclaration.builder()
        .name("search_web")
        .description("Search the web for current information, news, facts, or anything not in the conversation context.")
        .parameters(
            Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(mapOf(
                    "query" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("The search query")
                        .build()
                ))
                .required("query")
                .build()
        )
        .build()

    private val searchConfig = GenerateContentConfig.builder()
        .tools(listOf(
            Tool.builder().googleSearch(GoogleSearch.builder().build()).build()
        ))
        .build()

    override fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any> {
        val query = args["query"]?.toString() ?: return mapOf("error" to "Missing query")
        val response = client.models.generateContent("gemini-2.5-flash", query, searchConfig)
        return mapOf("result" to (response.text() ?: "No results found"))
    }
}
