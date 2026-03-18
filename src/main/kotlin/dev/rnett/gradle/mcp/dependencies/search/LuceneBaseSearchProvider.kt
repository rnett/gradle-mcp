package dev.rnett.gradle.mcp.dependencies.search

import com.github.benmanes.caffeine.cache.Caffeine
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.lucene.LuceneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.measureTime

abstract class LuceneBaseSearchProvider : SearchProvider {
    protected abstract val logger: Logger

    protected open fun resolveIndexDir(baseDir: Path): Path = baseDir

    protected abstract fun createAnalyzer(): Analyzer

    protected abstract fun copyDocument(oldDoc: Document, newPath: String): Document

    protected val readerCache = Caffeine.newBuilder()
        .maximumSize(10)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .removalListener<Path, DirectoryReader> { _, reader, _ ->
            reader?.close()
        }
        .build<Path, DirectoryReader> { path ->
            val dir = FSDirectory.open(path)
            val reader = DirectoryReader.open(dir)
            reader.readerCacheHelper?.addClosedListener { _ ->
                try {
                    dir.close()
                } catch (e: Exception) {
                    logger.error("Failed to close directory for reader at $path", e)
                }
            }
            reader
        }

    protected inline fun <T> withReader(idxDir: Path, action: (DirectoryReader) -> T): T {
        val reader = readerCache.get(idxDir)
        reader.incRef()
        try {
            return action(reader)
        } finally {
            reader.decRef()
        }
    }

    fun invalidateCache(path: Path) {
        readerCache.invalidate(path)
    }

    abstract inner class LuceneBaseIndexer(protected val outputDir: Path) : Indexer {
        protected val analyzer = createAnalyzer()
        protected val directory = FSDirectory.open(resolveIndexDir(outputDir).apply { createDirectories() })
        protected val config = IndexWriterConfig(analyzer).apply { openMode = IndexWriterConfig.OpenMode.CREATE }
        protected val writer = IndexWriter(directory, config)

        protected open val doForceMerge: Boolean = true

        override val documentCount: Int get() = writer.docStats.numDocs

        override suspend fun finish() {
            if (doForceMerge) {
                writer.forceMerge(1)
            }
            writer.commit()
            val count = documentCount
            resolveIndexDir(outputDir).resolve(".count").writeText(count.toString())
            invalidateCache(resolveIndexDir(outputDir))
        }

        override fun close() {
            writer.close()
            directory.close()
            analyzer.close()
        }
    }

    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path, progress: ProgressReporter) = withContext(Dispatchers.IO) {
        val totalDocs = indexDirs.keys.sumOf { countDocuments(it) }
        var completedDocs = 0

        val duration = measureTime {
            val resolvedOutputDir = resolveIndexDir(outputDir)
            resolvedOutputDir.createDirectories()

            createAnalyzer().use { analyzer ->
                LuceneUtils.writeIndex(resolvedOutputDir, analyzer) { writer ->
                    indexDirs.forEach { (idxDir, relativePrefix) ->
                        val resolvedIdxDir = resolveIndexDir(idxDir)
                        FSDirectory.open(resolvedIdxDir).use { directory ->
                            if (DirectoryReader.indexExists(directory)) {
                                DirectoryReader.open(directory).use { reader ->
                                    val storedFields = reader.storedFields()
                                    val count = reader.numDocs()
                                    for (i in 0 until reader.maxDoc()) {
                                        val oldDoc = try {
                                            storedFields.document(i)
                                        } catch (e: Exception) {
                                            continue
                                        }
                                        val oldPath = oldDoc.get("path")
                                        val newPath = relativePrefix.resolve(oldPath).toString().replace('\\', '/')

                                        val newDoc = copyDocument(oldDoc, newPath)
                                        writer.addDocument(newDoc)
                                    }
                                    completedDocs += count
                                    progress.report(completedDocs.toDouble(), totalDocs.toDouble(), null)
                                }
                            }
                        }
                    }
                    writer.forceMerge(1)
                    resolvedOutputDir.resolve(".count").writeText(writer.docStats.numDocs.toString())
                }
            }
            invalidateCache(resolvedOutputDir)
        }
        logger.info("$name index merging took $duration (${indexDirs.size} indices)")
    }

    override suspend fun countDocuments(indexDir: Path): Int = withContext(Dispatchers.IO) {
        val resolvedIdxDir = resolveIndexDir(indexDir)
        if (!resolvedIdxDir.exists()) return@withContext 0
        val countFile = resolvedIdxDir.resolve(".count")
        if (countFile.exists()) {
            return@withContext countFile.readText().toIntOrNull() ?: 0
        }

        FSDirectory.open(resolvedIdxDir).use { directory ->
            if (DirectoryReader.indexExists(directory)) {
                DirectoryReader.open(directory).use { reader ->
                    reader.numDocs().also { count ->
                        countFile.writeText(count.toString())
                    }
                }
            } else 0
        }
    }
}
