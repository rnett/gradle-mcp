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
    return this.groupBy { it.relativePath }.flatMap { (relativePath, results) ->
        val file = sourcesRoot.resolve(relativePath)
        if (!file.exists()) {
            throw IllegalStateException("Search result points to non-existent source file: $file")
        }
        val content = file.readText()
        val lines = content.lines()

        results.map { res ->
            if (res.snippet != null) {
                return@map SearchResult(relativePath, file, res.line ?: 1, res.snippet, res.score)
            }
            if (res.skipBoilerplate) {
                val (actualLine, snippet) = findHighSignalSnippet(content)
                return@map SearchResult(relativePath, file, actualLine, snippet, res.score)
            }
            val actualLine = if (res.offset in 0..content.length) {
                content.substring(0, res.offset).count { it == '\n' } + 1
            } else {
                res.line ?: 1
            }
            val lineIndex = (actualLine - 1).coerceIn(lines.indices)
            val startLine = (lineIndex - SearchResult.DEFAULT_SNIPPET_RANGE).coerceAtLeast(0)
            val endLine = (lineIndex + SearchResult.DEFAULT_SNIPPET_RANGE).coerceAtMost(lines.size - 1)
            val snippet = lines.subList(startLine, endLine + 1).joinToString("\n")
            SearchResult(relativePath, file, actualLine, snippet, res.score)
        }.distinctBy { it.line }
    }
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
    val score: Float?
) {
    companion object {
        const val DEFAULT_SNIPPET_RANGE = 2
    }
}