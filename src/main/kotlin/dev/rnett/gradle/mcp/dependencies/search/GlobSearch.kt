package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.tools.PaginationInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.time.measureTimedValue

object GlobSearch : SearchProvider {
    private val LOGGER = LoggerFactory.getLogger(GlobSearch::class.java)
    override val name: String = "glob"
    override val indexVersion: Int = 1

    private const val v1FileName = "filenames-v1.txt"

    override suspend fun search(indexDir: Path, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (results, duration) = measureTimedValue {
            val file = indexDir.resolve(v1FileName)
            if (!file.exists()) {
                throw IllegalStateException("Glob index file does not exist: $file")
            }

            val matcher = try {
                if (!query.any { it in "*?[]{}\\" }) {
                    null // Treat as substring search if no glob characters are present
                } else {
                    FileSystems.getDefault().getPathMatcher("glob:$query")
                }
            } catch (e: Exception) {
                // If it's not a valid glob, treat it as a substring match
                null
            }

            val paths = file.readLines()

            val matches = paths.asSequence()
                .filter {
                    if (matcher != null) {
                        try {
                            // Java's glob matcher on Windows expects the Path object, but it works correctly with the default Path representation.
                            // However, we should make sure we create the Path correctly.
                            matcher.matches(Path.of(it))
                        } catch (e: Exception) {
                            it.contains(query, ignoreCase = true)
                        }
                    } else {
                        it.contains(query, ignoreCase = true)
                    }
                }
                .toList()

            SearchResponse(
                matches.asSequence() 
                    .drop(pagination.offset)
                    .take(pagination.limit)
                    .map {
                        RelativeSearchResult(
                            relativePath = it,
                            offset = 0,
                            line = null,
                            score = 1.0f,
                            snippet = null,
                            skipBoilerplate = true
                        )
                    }.toList(),
                interpretedQuery = if (matcher != null) "glob:$query" else "substring:$query"
            )
        }
        val response = results
        LOGGER.info("Glob search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${response.results.size} results)")
        return@withContext response
    }

    override suspend fun newIndexer(outputDir: Path): Indexer = GlobIndexer(outputDir)

    class GlobIndexer(private val outputDir: Path) : Indexer {
        private val file = outputDir.resolve(v1FileName)
        private val paths = ConcurrentLinkedQueue<String>()

        init {
            outputDir.createDirectories()
        }

        override suspend fun indexFile(path: String, content: String) {
            paths.add(path)
        }

        override val documentCount: Int get() = paths.size

        override suspend fun finish() {
            withContext(Dispatchers.IO) {
                file.writeLines(paths.toList())
                outputDir.resolve(".count").writeText(documentCount.toString())
            }
        }

        override fun close() {}
    }

    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path, progress: ProgressReporter) = withContext(Dispatchers.IO) {
        val totalDocs = indexDirs.keys.sumOf { countDocuments(it) }
        var completedDocs = 0

        val (fileCount, duration) = measureTimedValue {
            val file = outputDir.resolve(v1FileName)
            outputDir.createDirectories()

            val allPaths = indexDirs.flatMap { (idxDir, relativePrefix) ->
                val srcFile = idxDir.resolve(v1FileName)
                if (!srcFile.exists()) return@flatMap emptyList()
                val paths = srcFile.readLines().map { relativePrefix.resolve(it).toString().replace('\\', '/') }
                completedDocs += paths.size
                progress.report(completedDocs.toDouble(), totalDocs.toDouble(), null)
                paths
            }

            file.writeLines(allPaths)
            outputDir.resolve(".count").writeText(allPaths.size.toString())
            allPaths.size
        }
        LOGGER.info("Glob index merging took $duration ($fileCount files across ${indexDirs.size} indices)")
    }

    override suspend fun countDocuments(indexDir: Path): Int = withContext(Dispatchers.IO) {
        val countFile = indexDir.resolve(".count")
        if (countFile.exists()) {
            return@withContext countFile.readText().toIntOrNull() ?: 0
        }

        val file = indexDir.resolve(v1FileName)
        if (!file.exists()) return@withContext 0
        file.useLines { it.count() }.also { count ->
            countFile.writeText(count.toString())
        }
    }
}
