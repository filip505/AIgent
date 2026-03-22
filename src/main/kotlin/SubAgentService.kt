import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Tool
import tools.AgentTool

class SubAgentService(
    private val client: Client,
    private val tools: List<AgentTool>,
    private val task: String
) {
    fun run(): String {
        println("[SubAgent] Task: $task")
        println("[SubAgent] Tools: ${tools.map { it.declaration.name().orElse("") }}")

        val config = GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(
                "You are a focused sub-agent. Complete the given task using the available tools. Be concise and return only the result."
            )))
            .tools(listOf(
                Tool.builder().functionDeclarations(tools.map { it.declaration }).build()
            ))
            .build()

        val history = mutableListOf<Content>()
        history.add(Content.builder().role("user").parts(listOf(Part.fromText(task))).build())

        var response = client.models.generateContent("gemini-2.5-flash", history, config)

        while (response.functionCalls()?.isNotEmpty() == true) {
            val responseParts = mutableListOf<Part>()

            for (fc in response.functionCalls()!!) {
                val name = fc.name().orElse("")
                val args = fc.args().orElse(emptyMap())
                val tool = tools.find { it.declaration.name().orElse("") == name }
                val result = tool?.handle(args, null) ?: mapOf("error" to "Unknown function: $name")
                println("[SubAgent] Tool call: $name($args) → $result")
                responseParts.add(Part.fromFunctionResponse(name, result))
            }

            history.add(Content.builder().role("model").parts(response.parts()).build())
            history.add(Content.builder().role("user").parts(responseParts).build())

            response = client.models.generateContent("gemini-2.5-flash", history, config)
        }

        val result = response.text() ?: "(no response)"
        println("[SubAgent] Result: $result")
        return result
    }
}
