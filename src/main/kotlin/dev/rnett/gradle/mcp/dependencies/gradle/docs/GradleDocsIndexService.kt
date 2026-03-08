package dev.rnett.gradle.mcp.dependencies.gradle.docs

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import dev.rnett.gradle.mcp.lucene.LuceneUtils
import dev.rnett.gradle.mcp.lucene.addTextAndExact
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import kotlin.io.path.writeText

interface GradleDocsIndexService : AutoCloseable {
    context(progress: ProgressReporter)
    suspend fun ensureIndexed(version: String)

    context(progress: ProgressReporter)
    suspend fun search(query: String, version: String, maxResults: Int = 20): DocsSearchResponse
}

class DefaultGradleDocsIndexService(
    private val extractor: ContentExtractorService,
    private val htmlConverter: HtmlConverter,
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

        val convertedDir = versionDir.resolve("converted")

        withContext(Dispatchers.IO) {
            Files.createDirectories(indexDir)
            Files.createDirectories(convertedDir)

            val filesChannel = kotlinx.coroutines.channels.Channel<Pair<String, ByteArray>>(capacity = 10)

            val indexingProgress = progress.withPhase("PROCESSING")
            var processedFiles = 0.0

            coroutineScope {
                val indexer = DocsIndexer(indexDir, convertedDir)
                val indexJob = launch(Dispatchers.IO) {
                    indexer.use {
                        for (entry in filesChannel) {
                            processedFiles++
                            indexingProgress(processedFiles, null, "Indexing documentation")
                            it.indexFile(entry.first, entry.second)
                        }
                        it.finish()
                    }
                }

                try {
                    extractor.extractEntries(version) { path, bytes ->
                        filesChannel.send(path to bytes)
                    }
                } finally {
                    filesChannel.close()
                }
                indexJob.join()
            }

            readerCache.invalidate(indexDir)
            doneMarker.writeText(System.currentTimeMillis().toString())
        }
    }

    inner class DocsIndexer(indexDir: Path, val convertedDir: Path) : AutoCloseable {
        private val directory = org.apache.lucene.store.FSDirectory.open(indexDir)
        private val config = org.apache.lucene.index.IndexWriterConfig(analyzer).apply { openMode = org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE }
        private val writer = org.apache.lucene.index.IndexWriter(directory, config)

        suspend fun indexFile(path: String, bytes: ByteArray) {
            val isHtml = path.endsWith(".html")
            val targetPath = if (isHtml) path.replace(".html", ".md") else path
            val processedBytes = if (isHtml) htmlConverter.convert(String(bytes), detectKind(path)).toByteArray() else bytes

            val content = String(processedBytes)
            val relativePath = targetPath.replace("\\", "/")
            val tags = detectTags(relativePath)
            val title = extractTitle(targetPath, content)

            val doc = Document().apply {
                tags.forEach { tag ->
                    add(TextField("tag", tag, Field.Store.YES))
                }
                add(StringField("path", relativePath, Field.Store.YES))
                addTextAndExact("title", title)
                addTextAndExact("body", content)
            }
            writer.addDocument(doc)

            val outFile = convertedDir.resolve(targetPath)
            Files.createDirectories(outFile.parent)
            outFile.writeText(content)
        }

        fun finish() {
            writer.commit()
        }

        override fun close() {
            writer.close()
            directory.close()
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

    private fun detectKind(targetPath: String): dev.rnett.gradle.mcp.DocsKind {
        return when {
            targetPath.startsWith("userguide/") -> dev.rnett.gradle.mcp.DocsKind.USERGUIDE
            targetPath.startsWith("dsl/") -> dev.rnett.gradle.mcp.DocsKind.DSL
            targetPath.startsWith("kotlin-dsl/") -> dev.rnett.gradle.mcp.DocsKind.KOTLIN_DSL
            targetPath.startsWith("javadoc/") -> dev.rnett.gradle.mcp.DocsKind.JAVADOC
            targetPath.startsWith("samples/") -> dev.rnett.gradle.mcp.DocsKind.SAMPLES
            targetPath.startsWith("release-notes.") -> dev.rnett.gradle.mcp.DocsKind.RELEASE_NOTES
            else -> dev.rnett.gradle.mcp.DocsKind.USERGUIDE
        }
    }

    private fun extractTitle(path: String, content: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.startsWith("# ") }
        return firstLine?.removePrefix("# ")?.trim() ?: path.substringAfterLast('/').substringBeforeLast('.')
    }
}
