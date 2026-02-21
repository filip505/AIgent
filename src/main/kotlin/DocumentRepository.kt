import com.google.genai.Client
import com.google.genai.types.EmbedContentConfig
import java.io.File
import kotlin.math.sqrt

data class DocumentChunk(
    val source: String,
    val text: String,
    val embedding: List<Float>
)

class DocumentRepository(
    private val client: Client,
    private val documentsDir: File = File("documents"),
    private val embeddingsFile: File = File("embeddings.json")
) {
    private var chunks = mutableListOf<DocumentChunk>()

    fun index(): Int {
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
            return 0
        }

        val files = documentsDir.listFiles { f -> f.extension in listOf("txt", "md") } ?: return 0
        if (files.isEmpty()) return 0

        // Check if embeddings cache is up to date
        if (embeddingsFile.exists() && embeddingsFile.lastModified() > newestFileTime(files)) {
            chunks = loadEmbeddings()
            return chunks.size
        }

        chunks.clear()
        for (file in files) {
            val text = file.readText()
            val fileChunks = splitIntoChunks(text)
            for (chunk in fileChunks) {
                val embedding = embed(chunk, "RETRIEVAL_DOCUMENT")
                chunks.add(DocumentChunk(file.name, chunk, embedding))
            }
        }

        saveEmbeddings()
        return chunks.size
    }

    fun search(query: String, topK: Int = 3): List<DocumentChunk> {
        if (chunks.isEmpty()) return emptyList()

        val queryEmbedding = embed(query, "RETRIEVAL_QUERY")

        return chunks
            .map { it to cosineSimilarity(queryEmbedding, it.embedding) }
            .sortedByDescending { it.second }
            .take(topK)
            .filter { it.second > 0.3 }
            .map { it.first }
    }

    private fun embed(text: String, taskType: String): List<Float> {
        val config = EmbedContentConfig.builder()
            .taskType(taskType)
            .outputDimensionality(768)
            .build()

        val response = client.models.embedContent("gemini-embedding-001", text, config)
        return response.embeddings().orElse(listOf()).firstOrNull()?.values()?.orElse(listOf()) ?: listOf()
    }

    private fun splitIntoChunks(text: String, chunkSize: Int = 500, overlap: Int = 100): List<String> {
        if (text.length <= chunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            chunks.add(text.substring(start, end))
            start += chunkSize - overlap
        }
        return chunks
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    private fun newestFileTime(files: Array<File>): Long {
        return files.maxOf { it.lastModified() }
    }

    private fun saveEmbeddings() {
        val entries = chunks.map { chunk ->
            val embStr = chunk.embedding.joinToString(",")
            """  {"source": ${escapeJson(chunk.source)}, "text": ${escapeJson(chunk.text)}, "embedding": [$embStr]}"""
        }
        embeddingsFile.writeText("[\n${entries.joinToString(",\n")}\n]\n")
    }

    private fun loadEmbeddings(): MutableList<DocumentChunk> {
        val json = embeddingsFile.readText()
        val result = mutableListOf<DocumentChunk>()

        val pattern = Regex(""""source"\s*:\s*"((?:[^"\\]|\\.)*)"\s*,\s*"text"\s*:\s*"((?:[^"\\]|\\.)*)"\s*,\s*"embedding"\s*:\s*\[([^\]]*)]""")
        for (match in pattern.findAll(json)) {
            val source = unescapeJson(match.groupValues[1])
            val text = unescapeJson(match.groupValues[2])
            val embedding = match.groupValues[3].split(",").map { it.trim().toFloat() }
            result.add(DocumentChunk(source, text, embedding))
        }

        return result
    }

    private fun escapeJson(s: String): String {
        val escaped = s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
