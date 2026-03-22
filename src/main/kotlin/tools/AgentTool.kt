package tools

import com.google.genai.types.FunctionDeclaration

interface AgentTool {
    val declaration: FunctionDeclaration
    fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any>
}
