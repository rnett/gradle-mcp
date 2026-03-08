package dev.rnett.gradle.mcp.dependencies.search

import com.github.benmanes.caffeine.cache.Caffeine
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
            DirectoryReader.open(FSDirectory.open(path))
        }

    fun invalidateCache(path: Path) {
        readerCache.invalidate(path)
    }

    abstract inner class LuceneBaseIndexer(protected val outputDir: Path) : Indexer {
        protected val analyzer = createAnalyzer()
        protected val directory = FSDirectory.open(resolveIndexDir(outputDir).apply { createDirectories() })
        protected val config = IndexWriterConfig(analyzer).apply { openMode = IndexWriterConfig.OpenMode.CREATE }
        protected val writer = IndexWriter(directory, config)

        protected open val doForceMerge: Boolean = false

        override suspend fun finish() {
            if (doForceMerge) {
                writer.forceMerge(1)
            }
            writer.commit()
            invalidateCache(resolveIndexDir(outputDir))
        }

        override fun close() {
            writer.close()
            directory.close()
            analyzer.close()
        }
    }

    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path) = withContext(Dispatchers.IO) {
        val duration = measureTime {
            val resolvedOutputDir = resolveIndexDir(outputDir)
            resolvedOutputDir.createDirectories()

            createAnalyzer().use { analyzer ->
                LuceneUtils.writeIndex(resolvedOutputDir, analyzer) { writer ->
                    indexDirs.forEach { (idxDir, relativePrefix) ->
                        val resolvedIdxDir = resolveIndexDir(idxDir)
                        DirectoryReader.open(FSDirectory.open(resolvedIdxDir)).use { reader ->
                            val storedFields = reader.storedFields()
                            for (i in 0 until reader.maxDoc()) {
                                val oldDoc = storedFields.document(i)
                                val oldPath = oldDoc.get("path")
                                val newPath = relativePrefix.resolve(oldPath).toString().replace('\\', '/')

                                val newDoc = copyDocument(oldDoc, newPath)
                                writer.addDocument(newDoc)
                            }
                        }
                    }
                    writer.forceMerge(1)
                }
            }
            invalidateCache(resolvedOutputDir)
        }
        logger.info("$name index merging took $duration (${indexDirs.size} indices)")
    }
}
