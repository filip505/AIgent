import com.google.genai.Client
import com.google.genai.types.EmbedContentConfig
import java.io.File
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.sqrt

data class DocumentChunk(
    val source: String,
    val text: String,
    val embedding: List<Float>
)

class DocumentRepository(
    private val client: Client,
    private val documentsDir: File = File("documents"),
    private val dbFile: File = File("embeddings.db")
) {
    private var chunks = mutableListOf<DocumentChunk>()

    private fun getConnection(): Connection {
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }

    private fun initDb() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS document_chunks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        source TEXT NOT NULL,
                        text TEXT NOT NULL,
                        embedding BLOB NOT NULL
                    )
                """)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS metadata (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                """)
            }
        }
    }

    fun index(): Int {
        println("[RAG] Initializing database...")
        initDb()

        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
            println("[RAG] Created documents directory: ${documentsDir.absolutePath}")
            return 0
        }

        val files = documentsDir.listFiles { f -> f.extension in listOf("txt", "md") } ?: return 0
        if (files.isEmpty()) {
            println("[RAG] No documents found in ${documentsDir.absolutePath}")
            return 0
        }

        println("[RAG] Found ${files.size} document(s): ${files.map { it.name }}")

        val newestFile = files.maxOf { it.lastModified() }
        val lastIndexed = getLastIndexedTime()

        if (lastIndexed >= newestFile) {
            println("[RAG] Loading embeddings from cache (database up to date)")
            chunks = loadFromDb()
            println("[RAG] Loaded ${chunks.size} chunks from database")
            return chunks.size
        }

        println("[RAG] Re-indexing documents (files have changed)")
        chunks.clear()
        for (file in files) {
            println("[RAG] Processing: ${file.name}")
            val text = file.readText()
            val fileChunks = splitIntoChunks(text)
            println("[RAG]   Split into ${fileChunks.size} chunk(s)")
            for ((i, chunk) in fileChunks.withIndex()) {
                println("[RAG]   Embedding chunk ${i + 1}/${fileChunks.size}...")
                val embedding = embed(chunk, "RETRIEVAL_DOCUMENT")
                chunks.add(DocumentChunk(file.name, chunk, embedding))
            }
        }

        saveToDb()
        setLastIndexedTime(System.currentTimeMillis())
        println("[RAG] Saved ${chunks.size} chunks to database")
        return chunks.size
    }

    fun search(query: String, topK: Int = 3): List<DocumentChunk> {
        println("[RAG] Search query: \"$query\"")
        if (chunks.isEmpty()) {
            println("[RAG] No chunks available for search")
            return emptyList()
        }

        println("[RAG] Embedding query...")
        val queryEmbedding = embed(query, "RETRIEVAL_QUERY")

        val results = chunks
            .map { it to cosineSimilarity(queryEmbedding, it.embedding) }
            .sortedByDescending { it.second }
            .take(topK)
            .filter { it.second > 0.3 }

        println("[RAG] Found ${results.size} relevant chunk(s):")
        results.forEach { (chunk, score) ->
            println("[RAG]   - ${chunk.source} (score: %.3f)".format(score))
        }

        return results.map { it.first }
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

    private fun saveToDb() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM document_chunks")
            }
            conn.prepareStatement("INSERT INTO document_chunks (source, text, embedding) VALUES (?, ?, ?)").use { ps ->
                for (chunk in chunks) {
                    ps.setString(1, chunk.source)
                    ps.setString(2, chunk.text)
                    ps.setBytes(3, floatsToBytes(chunk.embedding))
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

    private fun loadFromDb(): MutableList<DocumentChunk> {
        val result = mutableListOf<DocumentChunk>()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT source, text, embedding FROM document_chunks")
                while (rs.next()) {
                    val source = rs.getString("source")
                    val text = rs.getString("text")
                    val embedding = bytesToFloats(rs.getBytes("embedding"))
                    result.add(DocumentChunk(source, text, embedding))
                }
            }
        }
        return result
    }

    private fun getLastIndexedTime(): Long {
        getConnection().use { conn ->
            conn.prepareStatement("SELECT value FROM metadata WHERE key = ?").use { ps ->
                ps.setString(1, "last_indexed")
                val rs = ps.executeQuery()
                if (rs.next()) {
                    return rs.getString("value").toLongOrNull() ?: 0L
                }
            }
        }
        return 0L
    }

    private fun setLastIndexedTime(time: Long) {
        getConnection().use { conn ->
            conn.prepareStatement("INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)").use { ps ->
                ps.setString(1, "last_indexed")
                ps.setString(2, time.toString())
                ps.executeUpdate()
            }
        }
    }

    private fun floatsToBytes(floats: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        for (f in floats) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    private fun bytesToFloats(bytes: ByteArray): List<Float> {
        val buffer = ByteBuffer.wrap(bytes)
        val floats = mutableListOf<Float>()
        while (buffer.hasRemaining()) {
            floats.add(buffer.getFloat())
        }
        return floats
    }
}
