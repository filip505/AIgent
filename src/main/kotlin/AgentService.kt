import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Tool
import tools.AgentTool
import tools.CurrentTimeTool
import tools.Reminder
import tools.SchedulerService
import tools.GitHubDocumentService
import tools.SpawnAgentTool
import java.io.File

class AgentService(onReminder: (Reminder) -> Unit) {
    private val client = Client.builder()
        .apiKey(System.getenv("GOOGLE_API_KEY"))
        .build()

    private val scheduler = SchedulerService(onReminder)
    private val githubDocs = GitHubDocumentService()

    // baseTools excludes spawn_agent so sub-agents cannot spawn further sub-agents
    private val baseTools: List<AgentTool> = scheduler.tools + githubDocs.tools + listOf(CurrentTimeTool())
    private val tools: List<AgentTool> = baseTools + listOf(SpawnAgentTool(client, baseTools))

    private val soul = File("soul.md").readText()
    private val skillLoader = SkillLoader()
    private val allSkills = skillLoader.loadAll()

    private fun buildConfig(input: String): GenerateContentConfig {
        val relevantSkills = skillLoader.selectRelevant(input, allSkills)
        val systemPrompt = buildString {
            append(soul)
            if (relevantSkills.isNotEmpty()) {
                append("\n\n# Active Skills\n")
                relevantSkills.forEach { skill ->
                    append("\n## ${skill.name}\n")
                    append(skill.content)
                    append("\n")
                }
            }
        }
        return GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            .tools(listOf(
                Tool.builder().functionDeclarations(tools.map { it.declaration }).build()
            ))
            .build()
    }

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

        val scheduled = scheduler.init()
        if (scheduled > 0) {
            messages.add("Restored $scheduled pending reminders.")
        }

        val githubStatus = githubDocs.init()
        messages.add(githubStatus)

        return messages.joinToString("\n")
    }

    fun chat(input: String, chatId: Long? = null): String {
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

        val config = buildConfig(input)
        var response = client.models.generateContent("gemini-2.5-flash", history, config)

        while (response.functionCalls()?.isNotEmpty() == true) {
            val functionCalls = response.functionCalls()!!
            val responseParts = mutableListOf<Part>()

            for (fc in functionCalls) {
                val name = fc.name().orElse("")
                val args = fc.args().orElse(emptyMap())
                val tool = tools.find { it.declaration.name().orElse("") == name }
                val result = tool?.handle(args, chatId) ?: mapOf("error" to "Unknown function: $name")
                responseParts.add(Part.fromFunctionResponse(name, result))
            }

            val modelContent = Content.builder()
                .role("model")
                .parts(response.parts())
                .build()
            history.add(modelContent)

            val functionResponseContent = Content.builder()
                .role("user")
                .parts(responseParts)
                .build()
            history.add(functionResponseContent)

            response = client.models.generateContent("gemini-2.5-flash", history, config)
        }

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
