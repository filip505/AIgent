import java.io.File

data class Skill(
    val name: String,
    val description: String,
    val tools: List<String>,
    val content: String
)

class SkillLoader(private val skillsDir: File = File("skills")) {

    fun loadAll(): List<Skill> {
        if (!skillsDir.exists()) return emptyList()
        return skillsDir.listFiles { f -> f.extension == "md" }
            ?.mapNotNull { parseSkillFile(it) }
            ?: emptyList()
    }

    fun selectRelevant(input: String, all: List<Skill>): List<Skill> {
        val words = input.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val matched = all.filter { skill ->
            val skillWords = (skill.name + " " + skill.description).lowercase().split(Regex("\\W+")).toSet()
            words.intersect(skillWords).isNotEmpty()
        }
        return matched.ifEmpty { all }
    }

    private fun parseSkillFile(file: File): Skill? {
        val text = file.readText()
        if (!text.startsWith("---")) return null

        val endOfFrontmatter = text.indexOf("---", 3)
        if (endOfFrontmatter == -1) return null

        val frontmatter = text.substring(3, endOfFrontmatter).trim()
        val body = text.substring(endOfFrontmatter + 3).trim()

        val fields = frontmatter.lines().associate { line ->
            val idx = line.indexOf(':')
            if (idx == -1) return@associate "" to ""
            line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }

        val name = fields["name"] ?: return null
        val description = fields["description"] ?: return null
        val tools = fields["tools"]
            ?.removeSurrounding("[", "]")
            ?.split(",")
            ?.map { it.trim() }
            ?: emptyList()

        return Skill(name, description, tools, body)
    }
}
