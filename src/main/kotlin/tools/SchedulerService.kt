package tools

import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Reminder(
    val id: String,
    val message: String,
    val triggerAt: LocalDateTime,
    val chatId: Long? = null
)

class SchedulerService(private val onReminder: (Reminder) -> Unit) {
    private val file = File("reminders.json")
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val reminders = mutableListOf<Reminder>()

    val tools: List<AgentTool> = listOf(
        object : AgentTool {
            override val declaration: FunctionDeclaration = FunctionDeclaration.builder()
                .name("schedule_reminder")
                .description("Schedule a reminder for a specific date and time. Use this when the user asks to be reminded of something.")
                .parameters(
                    Schema.builder()
                        .type(Type.Known.OBJECT)
                        .properties(mapOf(
                            "message" to Schema.builder().type(Type.Known.STRING).description("The reminder message").build(),
                            "datetime" to Schema.builder().type(Type.Known.STRING).description("ISO 8601 local datetime, e.g. 2025-06-15T20:00:00").build()
                        ))
                        .required("message", "datetime")
                        .build()
                )
                .build()

            override fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any> {
                val message = args["message"]?.toString() ?: return mapOf("error" to "Missing message")
                val datetime = args["datetime"]?.toString() ?: return mapOf("error" to "Missing datetime")
                return mapOf("result" to schedule(message, datetime, chatId))
            }
        },
        object : AgentTool {
            override val declaration: FunctionDeclaration = FunctionDeclaration.builder()
                .name("list_reminders")
                .description("List all pending reminders.")
                .parameters(Schema.builder().type(Type.Known.OBJECT).properties(emptyMap<String, Schema>()).build())
                .build()

            override fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any> {
                val list = listReminders()
                return if (list.isEmpty()) {
                    mapOf("result" to "No pending reminders.")
                } else {
                    mapOf("result" to list.joinToString("\n") { "${it.message} at ${it.triggerAt}" })
                }
            }
        }
    )

    fun init(): Int {
        if (!file.exists()) return 0
        val loaded = parseReminders(file.readText())
        val now = LocalDateTime.now()
        val pending = loaded.filter { it.triggerAt.isAfter(now) }
        reminders.addAll(pending)
        pending.forEach { scheduleTimer(it) }
        save()
        return pending.size
    }

    fun schedule(message: String, datetime: String, chatId: Long? = null): String {
        val triggerAt = LocalDateTime.parse(datetime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val now = LocalDateTime.now()
        if (!triggerAt.isAfter(now)) {
            return "Cannot schedule a reminder in the past."
        }
        val id = System.currentTimeMillis().toString()
        val reminder = Reminder(id, message, triggerAt, chatId)
        reminders.add(reminder)
        save()
        scheduleTimer(reminder)
        return "Reminder scheduled for ${triggerAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}."
    }

    fun listReminders(): List<Reminder> {
        val now = LocalDateTime.now()
        reminders.removeAll { it.triggerAt.isBefore(now) || it.triggerAt.isEqual(now) }
        save()
        return reminders.toList()
    }

    private fun scheduleTimer(reminder: Reminder) {
        val now = LocalDateTime.now()
        val delayMs = now.until(reminder.triggerAt, ChronoUnit.MILLIS)
        if (delayMs <= 0) return
        scheduler.schedule({
            onReminder(reminder)
            reminders.removeAll { it.id == reminder.id }
            save()
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun save() {
        val json = buildString {
            append("[\n")
            reminders.forEachIndexed { i, r ->
                append("  {")
                append("\"id\":\"${escapeJson(r.id)}\",")
                append("\"message\":\"${escapeJson(r.message)}\",")
                append("\"triggerAt\":\"${r.triggerAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\",")
                append("\"chatId\":${r.chatId ?: "null"}")
                append("}")
                if (i < reminders.size - 1) append(",")
                append("\n")
            }
            append("]")
        }
        file.writeText(json)
    }

    private fun parseReminders(json: String): List<Reminder> {
        val result = mutableListOf<Reminder>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) return result

        val objectPattern = Regex("\\{[^}]+\\}")
        for (match in objectPattern.findAll(trimmed)) {
            val obj = match.value
            val id = extractJsonString(obj, "id") ?: continue
            val message = extractJsonString(obj, "message") ?: continue
            val triggerAt = extractJsonString(obj, "triggerAt") ?: continue
            val chatIdStr = extractJsonValue(obj, "chatId")
            val chatId = if (chatIdStr != null && chatIdStr != "null") chatIdStr.toLongOrNull() else null
            result.add(Reminder(id, message, LocalDateTime.parse(triggerAt), chatId))
        }
        return result
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.let { unescapeJson(it) }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*([^,}]+)")
        return pattern.find(json)?.groupValues?.get(1)?.trim()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun unescapeJson(s: String): String =
        s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
}
