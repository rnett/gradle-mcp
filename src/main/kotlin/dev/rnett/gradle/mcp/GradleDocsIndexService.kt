package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import dev.rnett.gradle.mcp.lucene.LuceneUtils
import dev.rnett.gradle.mcp.lucene.addTextAndExact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.uhighlight.DefaultPassageFormatter
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
    context(progress: ProgressReporter)
    suspend fun ensureIndexed(version: String)

    context(progress: ProgressReporter)
    suspend fun search(query: String, version: String, maxResults: Int = 20): DocsSearchResponse
}

class DefaultGradleDocsIndexService(
    private val extractor: ContentExtractorService,
    private val environment: GradleMcpEnvironment,
    private val readerCache: LuceneReaderCache
) : GradleDocsIndexService {

    private val analyzer: Analyzer = LuceneUtils.createBoostedAnalyzer(setOf("title_exact", "body_exact"))

    context(progress: ProgressReporter)
    override suspend fun ensureIndexed(version: String) {
        val versionDir = environment.cacheDir.resolve("reading_gradle_docs").resolve(version)
        val indexDir = versionDir.resolve("index")
        val doneMarker = indexDir.resolve(".done")

        if (doneMarker.exists()) {
            return
        }

        extractor.ensureProcessed(version)
        val convertedDir = versionDir.resolve("converted")

        withContext(Dispatchers.IO) {
            Files.createDirectories(indexDir)
            val filesToIndex = convertedDir.walk().filter { it.isRegularFile() && it.extension == "md" }.toList()
            val totalFiles = filesToIndex.size.toDouble()
            var processedFiles = 0.0

            val indexingProgress = progress.withPhase("INDEXING")

            LuceneUtils.writeIndex(indexDir, analyzer) { writer ->
                filesToIndex.forEach { file ->
                    processedFiles++
                    if (processedFiles % 20 == 0.0 || processedFiles == totalFiles) {
                        indexingProgress(processedFiles, totalFiles, "Indexing documentation")
                    }
                    val relativePath = convertedDir.relativize(file).toString().replace("\\", "/")
                    val tags = detectTags(relativePath)
                    val content = file.readText()
                    val title = extractTitle(file, content)

                    val doc = Document().apply {
                        tags.forEach { tag ->
                            add(TextField("tag", tag, Field.Store.YES))
                        }
                        add(StringField("path", relativePath, Field.Store.YES))
                        addTextAndExact("title", title)
                        addTextAndExact("body", content)
                    }
                    writer.addDocument(doc)
                }
            }
            readerCache.invalidate(indexDir)
            doneMarker.writeText(System.currentTimeMillis().toString())
        }
    }

    context(progress: ProgressReporter)
    override suspend fun search(query: String, version: String, maxResults: Int): DocsSearchResponse {
        ensureIndexed(version)
        val indexDir = environment.cacheDir.resolve("reading_gradle_docs").resolve(version).resolve("index")

        val reader = readerCache.get(indexDir)
        val searcher = IndexSearcher(reader)

        val luceneQuery = try {
            LuceneUtils.parseBoostedQuery(
                query,
                fields = arrayOf("title", "body", "tag"),
                exactFields = arrayOf("title_exact", "body_exact"),
                analyzer = analyzer,
                extraBoosts = mapOf(arrayOf("title_exact") to 5.0f)
            )
        } catch (e: Exception) {
            return DocsSearchResponse(
                emptyList(),
                error = LuceneUtils.formatSyntaxError(e.message)
            )
        }

        val topDocs = searcher.search(luceneQuery, maxResults)

        val highlighter = UnifiedHighlighter.builder(searcher, analyzer)
            .withFormatter(DefaultPassageFormatter("**", "**", "... ", false))
            .build()
        val snippets = highlighter.highlight("body", luceneQuery, topDocs)

        val results = topDocs.scoreDocs.mapIndexed { i, scoreDoc ->
            val luceneDoc = searcher.storedFields().document(scoreDoc.doc)
            val snippet = snippets.getOrNull(i) ?: luceneDoc.get("body")?.take(200) ?: ""
            DocsSearchResult(
                title = luceneDoc.get("title") ?: "",
                path = luceneDoc.get("path") ?: "",
                snippet = snippet,
                tag = luceneDoc.get("tag") ?: ""
            )
        }
        return DocsSearchResponse(results, interpretedQuery = luceneQuery.toString())
    }

    override fun close() {
        analyzer.close()
    }

    private fun detectTags(relativePath: String): List<String> {
        val tags = mutableListOf<String>()
        val baseTag = when {
            relativePath.startsWith("userguide/") -> "userguide"
            relativePath.startsWith("dsl/") -> "dsl"
            relativePath.startsWith("kotlin-dsl/") -> "dsl"
            relativePath.startsWith("javadoc/") -> "javadoc"
            relativePath.startsWith("samples/") -> "samples"
            relativePath == "release-notes.md" -> "release-notes"
            else -> "other"
        }
        tags.add(baseTag)

        if (relativePath.contains("best_practices")) {
            tags.add("best-practices")
        }

        return tags
    }

    private fun extractTitle(file: Path, content: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.startsWith("# ") }
        return firstLine?.removePrefix("# ")?.trim() ?: file.nameWithoutExtension
    }
}
