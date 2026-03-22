package tools

import SubAgentService
import com.google.genai.Client
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type

class SpawnAgentTool(
    private val client: Client,
    private val availableTools: List<AgentTool>
) : AgentTool {

    private val availableToolNames = availableTools.map { it.declaration.name().orElse("") }

    override val declaration: FunctionDeclaration = FunctionDeclaration.builder()
        .name("spawn_agent")
        .description("Delegate a self-contained task to a sub-agent that runs independently with a specific set of tools. Use this for tasks that don't need the current conversation history. Available tools to assign: $availableToolNames")
        .parameters(
            Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(mapOf(
                    "task" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("A clear, self-contained description of the task for the sub-agent to complete")
                        .build(),
                    "tools" to Schema.builder()
                        .type(Type.Known.ARRAY)
                        .items(Schema.builder().type(Type.Known.STRING).build())
                        .description("Names of tools to give the sub-agent. Must be from the available tools list.")
                        .build()
                ))
                .required("task", "tools")
                .build()
        )
        .build()

    override fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any> {
        val task = args["task"]?.toString() ?: return mapOf("error" to "Missing task")

        @Suppress("UNCHECKED_CAST")
        val toolNames = (args["tools"] as? List<String>) ?: return mapOf("error" to "Missing tools")

        val selectedTools = availableTools.filter { it.declaration.name().orElse("") in toolNames }
        if (selectedTools.isEmpty()) return mapOf("error" to "No valid tools found from: $toolNames")

        val result = SubAgentService(client, selectedTools, task).run()
        return mapOf("result" to result)
    }
}
