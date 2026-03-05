package dev.rnett.gradle.mcp.gradle.dependencies.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeLines
import kotlin.time.measureTimedValue

object GlobSearch : SearchProvider {
    private val LOGGER = LoggerFactory.getLogger(GlobSearch::class.java)
    override val name: String = "glob"
    override val indexVersion: Int = 1

    private const val v1FileName = "filenames-v1.txt"

    override suspend fun search(indexDir: Path, query: String): List<RelativeSearchResult> = withContext(Dispatchers.IO) {
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

            paths.filter { path ->
                if (matcher != null) {
                    try {
                        // Java's glob matcher on Windows expects the Path object, but it works correctly with the default Path representation.
                        // However, we should make sure we create the Path correctly.
                        matcher.matches(Path.of(path))
                    } catch (e: Exception) {
                        path.contains(query, ignoreCase = true)
                    }
                } else {
                    path.contains(query, ignoreCase = true)
                }
            }.map { relativePath ->
                RelativeSearchResult(
                    relativePath = relativePath,
                    offset = 0,
                    line = null,
                    score = 1.0f,
                    snippet = null,
                    skipBoilerplate = true
                )
            }
        }
        LOGGER.info("Glob search for \"$query\" took $duration (${results.size} results)")
        return@withContext results
    }

    override suspend fun index(dependencyDir: Path, outputDir: Path) = withContext(Dispatchers.IO) {
        LOGGER.info("Starting glob indexing for $dependencyDir")
        val (fileCount, duration) = measureTimedValue {
            val file = outputDir.resolve(v1FileName)
            outputDir.createDirectories()

            val paths = dependencyDir.walk()
                .filter { it.isRegularFile() }
                .map { it.relativeTo(dependencyDir).toString().replace('\\', '/') }
                .toList()

            file.writeLines(paths)
            paths.size
        }
        LOGGER.info("Glob indexing for $dependencyDir took $duration ($fileCount files)")
    }

    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path) = withContext(Dispatchers.IO) {
        val (fileCount, duration) = measureTimedValue {
            val file = outputDir.resolve(v1FileName)
            outputDir.createDirectories()

            val allPaths = indexDirs.flatMap { (idxDir, relativePrefix) ->
                val srcFile = idxDir.resolve(v1FileName)
                if (!srcFile.exists()) return@flatMap emptyList()
                srcFile.readLines().map { relativePrefix.resolve(it).toString().replace('\\', '/') }
            }

            file.writeLines(allPaths)
            allPaths.size
        }
        LOGGER.info("Glob index merging took $duration ($fileCount files across ${indexDirs.size} indices)")
    }
}
