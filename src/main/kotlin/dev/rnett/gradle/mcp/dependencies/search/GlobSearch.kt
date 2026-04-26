package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.tools.PaginationInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.time.measureTimedValue

object GlobSearch : SearchProvider {
    private val LOGGER = LoggerFactory.getLogger(GlobSearch::class.java)
    override val name: String = "glob"
    override val indexVersion: Int = 4

    private const val v2FileName = "filenames-v2.txt"

    override suspend fun search(
        indexDirs: Map<Path, Boolean>,
        query: String,
        pagination: PaginationInput
    ): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (results, duration) = measureTimedValue {
            val existingIndexDirs = indexDirs.keys.filter { it.resolve(v2FileName).exists() }
            if (existingIndexDirs.isEmpty()) {
                return@withContext SearchResponse(
                    emptyList(),
                    error = "No valid Glob index files found in provided paths."
                )
            }

            val matcher = try {
                if (!query.any { it in "*?[]{}\\" }) {
                    null // Treat as substring search if no glob characters are present
                } else {
                    val normalizedQuery = query.replace('\\', '/')
                    FileSystems.getDefault().getPathMatcher("glob:$normalizedQuery")
                }
            } catch (e: Exception) {
                // If it's not a valid glob, treat it as a substring match
                null
            }

            var hasMore = false
            var matchesFound = 0
            val paginatedMatches = mutableListOf<String>()

            dirLoop@ for (dir in existingIndexDirs) {
                dir.resolve(v2FileName).useLines { lines ->
                    for (line in lines) {
                        val isMatch = if (matcher != null) {
                            try {
                                matcher.matches(Path(line.replace('\\', '/')))
                            } catch (e: Exception) {
                                line.contains(query, ignoreCase = true)
                            }
                        } else {
                            line.contains(query, ignoreCase = true)
                        }

                        if (isMatch) {
                            matchesFound++
                            if (matchesFound > pagination.offset) {
                                if (paginatedMatches.size < pagination.limit) {
                                    paginatedMatches.add(line)
                                } else {
                                    hasMore = true
                                    break@dirLoop
                                }
                            }
                        }
                    }
                }
            }

            SearchResponse(
                paginatedMatches.map {
                    RelativeSearchResult(
                        relativePath = it.replace('\\', '/'),
                        offset = 0,
                        line = null,
                        score = 1.0f,
                        snippet = null,
                        skipBoilerplate = true
                    )
                },
                interpretedQuery = if (matcher != null) "glob:$query" else "substring:$query",
                hasMore = hasMore
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

        override suspend fun indexFile(entry: IndexEntry) {
            paths.add(entry.relativePath)
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
