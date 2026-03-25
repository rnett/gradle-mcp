package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.unorderedParallelForEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.atomics.AtomicInt
import kotlin.io.path.Path
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
    override val indexVersion: Int = 2

    private const val v2FileName = "filenames-v2.txt"

    override suspend fun search(indexDir: Path, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (results, duration) = measureTimedValue {
            val file = indexDir.resolve(v2FileName)
            if (!file.exists()) {
                return@withContext SearchResponse(emptyList(), error = "Glob index file does not exist: $file")
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
                            matcher.matches(Path(it))
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
                interpretedQuery = if (matcher != null) "glob:$query" else "substring:$query",
                totalResults = matches.size
            )
        }
        val response = results
        LOGGER.info("Glob search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${response.results.size} results)")
        return@withContext response
    }

    override suspend fun newIndexer(outputDir: Path): Indexer = GlobIndexer(outputDir)

    class GlobIndexer(private val outputDir: Path) : Indexer {
        private val file = outputDir.resolve(v2FileName)
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

    context(progress: ProgressReporter)
    override suspend fun mergeIndices(
        indexDirs: Map<Path, Path>,
        outputDir: Path,
        withLock: suspend (Path, suspend () -> Unit) -> Unit
    ) = withContext(Dispatchers.IO) {
        val totalDocs = indexDirs.keys.sumOf { countDocuments(it) }
        val completedDocs = AtomicInt(0)

        val (fileCount, duration) = measureTimedValue {
            val file = outputDir.resolve(v2FileName)
            outputDir.createDirectories()

            val allPaths = ConcurrentLinkedQueue<String>()
            indexDirs.entries.unorderedParallelForEach(context = Dispatchers.IO) { (idxDir, _) ->
                withLock(idxDir) {
                    val srcFile = idxDir.resolve(v2FileName)
                    if (srcFile.exists()) {
                        val paths = srcFile.readLines()
                        val updatedDocs = completedDocs.addAndFetch(paths.size)
                        progress.report(updatedDocs.toDouble(), totalDocs.toDouble(), null)
                        allPaths.addAll(paths)
                    } else {
                        throw IllegalStateException("Glob index does not exist at $idxDir during merge!")
                    }
                }
            }

            val resultList = allPaths.toList()
            file.writeLines(resultList)
            outputDir.resolve(".count").writeText(resultList.size.toString())
            resultList.size
        }
        LOGGER.info("Glob index merging took $duration ($fileCount files across ${indexDirs.size} indices)")
    }

    override suspend fun countDocuments(indexDir: Path): Int = withContext(Dispatchers.IO) {
        val countFile = indexDir.resolve(".count")
        if (countFile.exists()) {
            return@withContext countFile.readText().toIntOrNull() ?: 0
        }

        val file = indexDir.resolve(v2FileName)
        if (!file.exists()) return@withContext 0
        file.useLines { it.count() }.also { count ->
            countFile.writeText(count.toString())
        }
    }
}
