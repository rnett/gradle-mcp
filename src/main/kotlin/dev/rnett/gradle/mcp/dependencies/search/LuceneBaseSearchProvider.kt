package dev.rnett.gradle.mcp.dependencies.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.MultiReader
import org.apache.lucene.store.FSDirectory
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

abstract class LuceneBaseSearchProvider : SearchProvider {
    protected abstract val logger: Logger

    override fun resolveIndexDir(baseDir: Path): Path = baseDir

    protected abstract fun createAnalyzer(): Analyzer

    protected inline fun <T> withReader(idxDir: Path, action: (DirectoryReader) -> T): T {
        FSDirectory.open(idxDir).use { dir ->
            DirectoryReader.open(dir).use { reader ->
                return action(reader)
            }
        }
    }

    protected inline fun <T> withMultiReader(indexDirs: List<Path>, action: (IndexReader) -> T): T? {
        val opened = mutableListOf<Pair<FSDirectory, DirectoryReader>>()
        try {
            for (dir in indexDirs) {
                val resolved = resolveIndexDir(dir)
                if (!resolved.exists()) continue
                val directory = FSDirectory.open(resolved)
                try {
                    opened.add(directory to DirectoryReader.open(directory))
                } catch (e: Exception) {
                    directory.close()
                    throw e
                }
            }
        } catch (e: Exception) {
            opened.asReversed().forEach { (directory, reader) ->
                try {
                    reader.close()
                } finally {
                    directory.close()
                }
            }
            throw e
        }

        if (opened.isEmpty()) {
            return null
        }

        return try {
            val readers = opened.map { it.second }
            if (readers.size == 1) {
                action(readers.first())
            } else {
                MultiReader(readers.toTypedArray(), false).use { multiReader ->
                    action(multiReader)
                }
            }
        } finally {
            opened.asReversed().forEach { (directory, reader) ->
                try {
                    reader.close()
                } finally {
                    directory.close()
                }
            }
        }
    }

    override fun invalidateCache(indexDir: Path) {}

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
            invalidateCache(outputDir)
        }

        override fun close() {
            writer.close()
            directory.close()
            analyzer.close()
        }
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
