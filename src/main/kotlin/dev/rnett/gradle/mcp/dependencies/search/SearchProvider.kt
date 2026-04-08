package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.tools.PaginationInput
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

interface Indexer : AutoCloseable {
    suspend fun indexFile(path: String, content: String)
    suspend fun finish()
    val documentCount: Int
}

val SearchProvider.markerFileName: String
    get() = ".indexed-$name-$indexVersion"

interface SearchProvider {
    val name: String
    val indexVersion: Int
    fun resolveIndexDir(baseDir: Path): Path = baseDir
    /**
     * Performs a search against multiple individual dependency indices.
     */
    suspend fun search(indexDirs: List<Path>, query: String, pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS): SearchResponse<RelativeSearchResult>

    /**
     * Lists package contents across multiple dependency indices.
     */
    suspend fun listPackageContents(indexDirs: List<Path>, packageName: String): PackageContents? = null

    suspend fun newIndexer(outputDir: Path): Indexer

    suspend fun countDocuments(indexDir: Path): Int

    companion object {
        val SOURCE_EXTENSIONS = setOf("kt", "kts", "java", "groovy")
    }
}

data class Index(val dir: Path)

class IndexEntry(val relativePath: String, private val contentProvider: () -> String) {
    val content: String get() = contentProvider()
}

@Serializable
/**
 * Represents the contents of a dot-separated package path.
 *
 * @property symbols Direct symbols declared in this package (e.g., classes, top-level functions).
 * @property subPackages Immediate sub-package segments (e.g., for package 'org.gradle', 'api' if 'org.gradle.api' exists).
 */
data class PackageContents(
    val symbols: List<String>,
    val subPackages: List<String>
)

/**
 * Contents of a single sub-package at depth 1, fetched for nested listing.
 *
 * @property name The simple segment name of this sub-package.
 * @property symbols Direct symbols declared in this sub-package.
 * @property subPackages Immediate sub-package segments of this sub-package (depth 2).
 */
data class SubPackageContents(
    val name: String,
    val symbols: List<String>,
    val subPackages: List<String>
)

/**
 * A 2-level nested view of a package for richer navigation output.
 *
 * @property symbols Direct symbols in the root package.
 * @property subPackages Expanded sub-packages (depth 1), each containing their own symbols and sub-package names.
 * @property tooManySubPackages True when expansion was skipped because the sub-package count exceeded the cap.
 */
data class NestedPackageContents(
    val symbols: List<String>,
    val subPackages: List<SubPackageContents>,
    val tooManySubPackages: Boolean = false
)

data class SearchResponse<T>(
    val results: List<T>,
    val interpretedQuery: String? = null,
    val error: String? = null,
    val hasMore: Boolean = false
)

data class RelativeSearchResult(
    val relativePath: String,
    val offset: Int,
    val line: Int? = null,
    val score: Float?,
    val snippet: String? = null,
    val skipBoilerplate: Boolean = false
)

fun Collection<RelativeSearchResult>.toSearchResults(sourcesRoot: Path): List<SearchResult> {
    return this.groupBy { it.relativePath }.flatMap { (relativePath, matches) ->
        val file = sourcesRoot.resolve(relativePath)
        if (!file.exists()) {
            throw IllegalStateException("Search result points to non-existent source file: $file")
        }
        val content = file.readText()
        val lines = content.lines()

        // Convert matches to have concrete line numbers
        val matchesWithLines = matches.map { match ->
            val line = match.line ?: if (match.offset in 0..content.length) {
                content.substring(0, match.offset).count { it == '\n' } + 1
            } else {
                1
            }
            match to line
        }.sortedBy { it.second }

        // Cluster matches that are close together (within DEFAULT_SNIPPET_RANGE)
        val clusteringThreshold = SearchResult.DEFAULT_SNIPPET_RANGE
        val clusters = mutableListOf<MutableList<Pair<RelativeSearchResult, Int>>>()
        for ((match, line) in matchesWithLines) {
            if (clusters.isEmpty()) {
                clusters.add(mutableListOf(Pair(match, line)))
            } else {
                val lastCluster = clusters.last()
                val lastLine = lastCluster.last().second
                if (line - lastLine <= clusteringThreshold) {
                    lastCluster.add(Pair(match, line))
                } else {
                    clusters.add(mutableListOf(Pair(match, line)))
                }
            }
        }

        // Convert each cluster to a SearchResult
        clusters.map { cluster ->
            // Cluster is already sorted by line (maintained during construction)
            val firstMatch = cluster.first().first
            val lastMatch = cluster.last().first
            val firstLine = cluster.first().second
            val lastLine = cluster.last().second

            val snippetStart = (firstLine - 1 - SearchResult.DEFAULT_SNIPPET_RANGE).coerceAtLeast(0)
            val snippetEnd = (lastLine - 1 + SearchResult.DEFAULT_SNIPPET_RANGE).coerceAtMost(lines.size - 1)
            val snippet = lines.subList(snippetStart, snippetEnd + 1).joinToString("\n")

            val matchLines = cluster.map { it.second } // Already sorted by construction
            // Find best match (for line) and sum scores
            var bestMatch: RelativeSearchResult? = null
            var bestScore = Float.NEGATIVE_INFINITY
            var sumScore = 0f
            for ((match, _) in cluster) {
                val score = match.score ?: 0f
                sumScore += score
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = match
                }
            }
            val finalScore = if (sumScore == 0f && bestScore == Float.NEGATIVE_INFINITY) 0f else sumScore
            val bestLine = bestMatch?.line ?: if (bestMatch?.offset in 0..content.length) {
                content.substring(0, bestMatch!!.offset).count { it == '\n' } + 1
            } else {
                1
            }

            SearchResult(
                relativePath = relativePath,
                file = file,
                line = bestLine,
                snippet = snippet,
                score = finalScore,
                matchLines = matchLines
            )
        }
    }.sortedByDescending { it.score }
}

/**
 * Finds a high-signal snippet by skipping boilerplate.
 */
fun findHighSignalSnippet(content: String, maxLines: Int = 5): Pair<Int, String> {
    val lines = content.lines()
    var skipUntil = 0
    var inMultilineComment = false
    var multilineEnd = ""

    for (i in lines.indices) {
        val line = lines[i].trim()

        if (inMultilineComment) {
            if (line.contains(multilineEnd)) {
                inMultilineComment = false
            }
            skipUntil = i + 1
            continue
        }

        if (line.startsWith("/*")) {
            if (!line.contains("*/")) {
                inMultilineComment = true
                multilineEnd = "*/"
            }
            skipUntil = i + 1
            continue
        }

        if (line.startsWith("<!--")) {
            if (!line.contains("-->")) {
                inMultilineComment = true
                multilineEnd = "-->"
            }
            skipUntil = i + 1
            continue
        }

        if (line.isEmpty() || line.startsWith("//") || line.startsWith("package ") || line.startsWith("import ")) {
            skipUntil = i + 1
            continue
        }

        break
    }

    val snippetLines = lines.drop(skipUntil).take(maxLines)
    return (skipUntil + 1) to snippetLines.joinToString("\n")
}

data class SearchResult(
    val relativePath: String,
    val file: Path,
    val line: Int,
    val snippet: String,
    val score: Float?,
    val matchLines: List<Int>
) {
    companion object {
        const val DEFAULT_SNIPPET_RANGE = 2
    }
}