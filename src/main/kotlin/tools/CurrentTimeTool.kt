package tools

import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CurrentTimeTool : AgentTool {
    override val declaration: FunctionDeclaration = FunctionDeclaration.builder()
        .name("get_current_time")
        .description("Get the current date and time. Use this before scheduling reminders to know what 'today', 'tonight', 'tomorrow', etc. mean.")
        .parameters(Schema.builder().type(Type.Known.OBJECT).properties(emptyMap<String, Schema>()).build())
        .build()

    override fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any> {
        val now = LocalDateTime.now()
        return mapOf(
            "datetime" to now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "formatted" to now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm, EEEE"))
        )
    }
}
