package tools

import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import java.io.File

class GitHubDocumentService(
    private val repoUrl: String = System.getenv("GITHUB_NOTES_REPO") ?: "https://github.com/filip505/Notes",
    private val githubToken: String? = System.getenv("GITHUB_TOKEN"),
    private val localPath: String = "notes-repo"
) {
    private val repoDir = File(localPath)

    private val authenticatedRepoUrl: String
        get() {
            if (githubToken.isNullOrBlank()) return repoUrl
            return repoUrl.replace("https://", "https://$githubToken@")
        }

    val tools: List<AgentTool> = listOf(
        object : AgentTool {
            override val declaration: FunctionDeclaration = FunctionDeclaration.builder()
                .name("push_document_to_github")
                .description("Push a markdown document to the private GitHub repository. Creates the document if it doesn't exist, updates it if it does. The commit message will be auto-generated based on the content.")
                .parameters(
                    Schema.builder()
                        .type(Type.Known.OBJECT)
                        .properties(mapOf(
                            "document_name" to Schema.builder()
                                .type(Type.Known.STRING)
                                .description("The name of the document (without .md extension, it will be added automatically). Example: 'meeting-notes' or 'project-ideas'")
                                .build(),
                            "content" to Schema.builder()
                                .type(Type.Known.STRING)
                                .description("The markdown content to write to the document")
                                .build()
                        ))
                        .required("document_name", "content")
                        .build()
                )
                .build()

            override fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any> {
                val documentName = args["document_name"]?.toString() ?: return mapOf("error" to "Missing document_name")
                val content = args["content"]?.toString() ?: return mapOf("error" to "Missing content")
                return pushDocument(documentName, content)
            }
        },
        object : AgentTool {
            override val declaration: FunctionDeclaration = FunctionDeclaration.builder()
                .name("list_github_documents")
                .description("List all markdown documents in the repository.")
                .parameters(Schema.builder().type(Type.Known.OBJECT).properties(emptyMap<String, Schema>()).build())
                .build()

            override fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any> = listDocuments()
        },
        object : AgentTool {
            override val declaration: FunctionDeclaration = FunctionDeclaration.builder()
                .name("read_github_document")
                .description("Read the content of a markdown document from the repository.")
                .parameters(
                    Schema.builder()
                        .type(Type.Known.OBJECT)
                        .properties(mapOf(
                            "document_name" to Schema.builder()
                                .type(Type.Known.STRING)
                                .description("The name of the document (without .md extension)")
                                .build()
                        ))
                        .required("document_name")
                        .build()
                )
                .build()

            override fun handle(args: Map<String, Any>, chatId: Long?): Map<String, Any> {
                val documentName = args["document_name"]?.toString() ?: return mapOf("error" to "Missing document_name")
                return readDocument(documentName)
            }
        }
    )

    fun init(): String {
        if (githubToken.isNullOrBlank()) {
            return "Warning: GITHUB_TOKEN not set. Push operations may fail for private repos."
        }

        return if (repoDir.exists() && File(repoDir, ".git").exists()) {
            val pullResult = runGitCommandInRepo("git", "pull")
            if (pullResult.exitCode == 0) {
                "Notes repository synced."
            } else {
                "Warning: Failed to pull latest changes: ${pullResult.error}"
            }
        } else {
            repoDir.deleteRecursively()
            val cloneResult = runGitCommand("git", "clone", authenticatedRepoUrl, localPath)
            if (cloneResult.exitCode == 0) {
                "Notes repository cloned."
            } else {
                "Error: Failed to clone repository: ${cloneResult.error}"
            }
        }
    }

    private fun pushDocument(documentName: String, content: String): Map<String, Any> {
        if (!repoDir.exists()) {
            return mapOf("error" to "Repository not initialized. Please check the configuration.")
        }

        val pullResult = runGitCommandInRepo("git", "pull")
        if (pullResult.exitCode != 0) {
            return mapOf("error" to "Failed to sync repository: ${pullResult.error}")
        }

        val sanitizedName = documentName
            .replace(Regex("[^a-zA-Z0-9-_]"), "-")
            .lowercase()
            .trimEnd('-')

        val fileName = if (sanitizedName.endsWith(".md")) sanitizedName else "$sanitizedName.md"
        val file = File(repoDir, fileName)

        val isNew = !file.exists()

        try {
            file.writeText(content)

            val addResult = runGitCommandInRepo("git", "add", fileName)
            if (addResult.exitCode != 0) {
                return mapOf("error" to "Failed to stage file: ${addResult.error}")
            }

            val statusResult = runGitCommandInRepo("git", "status", "--porcelain", fileName)
            if (statusResult.output.isBlank()) {
                return mapOf("result" to "No changes to commit for '$fileName'. The content is identical to the existing version.")
            }

            val commitMessage = generateCommitMessage(fileName, content, isNew)

            val commitResult = runGitCommandInRepo("git", "commit", "-m", commitMessage)
            if (commitResult.exitCode != 0) {
                return mapOf("error" to "Failed to commit: ${commitResult.error}")
            }

            val pushResult = runGitCommandInRepo("git", "push")
            if (pushResult.exitCode != 0) {
                return mapOf("error" to "Failed to push: ${pushResult.error}")
            }

            val action = if (isNew) "created and pushed" else "updated and pushed"
            return mapOf("result" to "Document '$fileName' was successfully $action to GitHub.")

        } catch (e: Exception) {
            return mapOf("error" to "Failed to save document: ${e.message}")
        }
    }

    private fun generateCommitMessage(fileName: String, content: String, isNew: Boolean): String {
        val action = if (isNew) "Add" else "Update"

        val title = extractTitle(content)
        val summary = extractSummary(content)

        return if (title != null) {
            "$action $fileName: $title"
        } else if (summary.isNotBlank()) {
            "$action $fileName: $summary"
        } else {
            "$action document: $fileName"
        }
    }

    private fun extractTitle(content: String): String? {
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                return trimmed.removePrefix("# ").take(50).trim()
            }
        }
        return null
    }

    private fun extractSummary(content: String): String {
        val cleanContent = content
            .lines()
            .filter { !it.trim().startsWith("#") }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleanContent.length > 50) {
            cleanContent.take(47) + "..."
        } else {
            cleanContent
        }
    }

    private fun listDocuments(): Map<String, Any> {
        if (!repoDir.exists()) {
            return mapOf("error" to "Repository not initialized.")
        }

        val documents = repoDir.listFiles { file ->
            file.isFile && file.extension == "md"
        }?.map { it.nameWithoutExtension } ?: emptyList()

        return if (documents.isEmpty()) {
            mapOf("result" to "No markdown documents found in the repository.")
        } else {
            mapOf(
                "result" to "Found ${documents.size} document(s):",
                "documents" to documents
            )
        }
    }

    private fun readDocument(documentName: String): Map<String, Any> {
        if (!repoDir.exists()) {
            return mapOf("error" to "Repository not initialized.")
        }

        val sanitizedName = documentName
            .replace(Regex("[^a-zA-Z0-9-_]"), "-")
            .lowercase()
            .trimEnd('-')

        val fileName = if (sanitizedName.endsWith(".md")) sanitizedName else "$sanitizedName.md"
        val file = File(repoDir, fileName)

        return if (file.exists()) {
            mapOf(
                "result" to "Content of '$fileName':",
                "content" to file.readText()
            )
        } else {
            mapOf("error" to "Document '$fileName' not found.")
        }
    }

    private data class CommandResult(val output: String, val error: String, val exitCode: Int)

    private fun runGitCommand(vararg command: String): CommandResult {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(false)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            CommandResult(output, error, exitCode)
        } catch (e: Exception) {
            CommandResult("", e.message ?: "Unknown error", -1)
        }
    }

    private fun runGitCommandInRepo(vararg command: String): CommandResult {
        return try {
            val process = ProcessBuilder(*command)
                .directory(repoDir)
                .redirectErrorStream(false)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            CommandResult(output, error, exitCode)
        } catch (e: Exception) {
            CommandResult("", e.message ?: "Unknown error", -1)
        }
    }
}
