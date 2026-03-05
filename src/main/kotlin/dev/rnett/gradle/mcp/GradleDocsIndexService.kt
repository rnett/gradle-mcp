package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import dev.rnett.gradle.mcp.lucene.LuceneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.uhighlight.UnifiedHighlighter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

interface GradleDocsIndexService : AutoCloseable {
    suspend fun ensureIndexed(version: String)
    suspend fun search(query: String, version: String, maxResults: Int = 20): List<DocsSearchResult>
}

class DefaultGradleDocsIndexService(
    private val extractor: ContentExtractorService,
    private val environment: GradleMcpEnvironment,
    private val readerCache: LuceneReaderCache
) : GradleDocsIndexService {

    private val analyzer = StandardAnalyzer()

    override suspend fun ensureIndexed(version: String) {
        val versionDir = environment.cacheDir.resolve("gradle-docs").resolve(version)
        val indexDir = versionDir.resolve("index")
        val doneMarker = indexDir.resolve(".done")

        if (doneMarker.exists()) {
            return
        }

        extractor.ensureProcessed(version)
        val convertedDir = versionDir.resolve("converted")

        withContext(Dispatchers.IO) {
            Files.createDirectories(indexDir)
            LuceneUtils.writeIndex(indexDir, analyzer) { writer ->
                convertedDir.walk().filter { it.isRegularFile() && it.extension == "md" }.forEach { file ->
                    val relativePath = convertedDir.relativize(file).toString().replace("\\", "/")
                    val tag = detectTag(relativePath)
                    val content = file.readText()
                    val title = extractTitle(file, content)

                    val doc = Document().apply {
                        add(TextField("tag", tag, Field.Store.YES))
                        add(StringField("path", relativePath, Field.Store.YES))
                        add(TextField("title", title, Field.Store.YES))
                        add(TextField("body", content, Field.Store.YES))
                    }
                    writer.addDocument(doc)
                }
            }
            readerCache.invalidate(indexDir)
            doneMarker.writeText(System.currentTimeMillis().toString())
        }
    }

    override suspend fun search(query: String, version: String, maxResults: Int): List<DocsSearchResult> {
        ensureIndexed(version)
        val indexDir = environment.cacheDir.resolve("gradle-docs").resolve(version).resolve("index")

        val reader = readerCache.get(indexDir)
        val searcher = IndexSearcher(reader)
        val fields = arrayOf("title", "body", "tag")
        val parser = MultiFieldQueryParser(fields, analyzer)
        val luceneQuery = try {
            parser.parse(query)
        } catch (_: Exception) {
            // Fallback for potentially malformed queries from LLM
            parser.parse(MultiFieldQueryParser.escape(query))
        }

        val topDocs = searcher.search(luceneQuery, maxResults)

        @Suppress("DEPRECATION")
        val highlighter = UnifiedHighlighter(searcher, analyzer)
        val snippets = highlighter.highlight("body", luceneQuery, topDocs)

        return topDocs.scoreDocs.mapIndexed { i, scoreDoc ->
            val luceneDoc = searcher.storedFields().document(scoreDoc.doc)
            val snippet = snippets.getOrNull(i) ?: luceneDoc.get("body")?.take(200) ?: ""
            DocsSearchResult(
                title = luceneDoc.get("title") ?: "",
                path = luceneDoc.get("path") ?: "",
                snippet = snippet,
                tag = luceneDoc.get("tag") ?: ""
            )
        }
    }

    override fun close() {
        analyzer.close()
    }

    private fun detectTag(relativePath: String): String {
        return when {
            relativePath.startsWith("userguide/") -> "userguide"
            relativePath.startsWith("dsl/") -> "dsl"
            relativePath.startsWith("kotlin-dsl/") -> "dsl"
            relativePath.startsWith("javadoc/") -> "javadoc"
            relativePath.startsWith("samples/") -> "samples"
            relativePath == "release-notes.md" -> "release-notes"
            else -> "other"
        }
    }

    private fun extractTitle(file: Path, content: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.startsWith("# ") }
        return firstLine?.removePrefix("# ")?.trim() ?: file.nameWithoutExtension
    }
}
