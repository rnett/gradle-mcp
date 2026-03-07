package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.tools.PaginationInput
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

interface SearchProvider {
    val name: String
    val indexVersion: Int

    suspend fun search(indexDir: Path, query: String, pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS): SearchResponse<RelativeSearchResult>

    context(progress: ProgressReporter)
    suspend fun index(dependencyDir: Path, outputDir: Path)

    /**
     * [indexDirs] a map of index dirs to the relative path of that origin in the combined set
     */
    context(progress: ProgressReporter)
    suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path)

    companion object {
        val SOURCE_EXTENSIONS = setOf("kt", "kts", "java", "groovy")
    }
}

data class SearchResponse<T>(
    val results: List<T>,
    val interpretedQuery: String? = null,
    val error: String? = null
)

data class RelativeSearchResult(
    val relativePath: String,
    val offset: Int,
    val line: Int? = null,
    val score: Float?,
    val snippet: String? = null,
    val skipBoilerplate: Boolean = false
) {
    fun toSearchResult(sourcesRoot: Path): SearchResult {
        return snippet?.let { SearchResult(relativePath, sourcesRoot.resolve(relativePath), line ?: 1, it, score) }
            ?: SearchResult.fromFile(relativePath, sourcesRoot.resolve(relativePath), offset, line, score)
    }
}

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
            val actualLine = if (res.line != null) res.line else {
                content.substring(0, res.offset).count { it == '\n' } + 1
            }
            val startLine = (actualLine - 1 - SearchResult.DEFAULT_SNIPPET_RANGE).coerceAtLeast(0)
            val endLine = (actualLine - 1 + SearchResult.DEFAULT_SNIPPET_RANGE).coerceAtMost(lines.size - 1)
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
        const val DEFAULT_SNIPPET_RANGE = 1
        fun fromFile(relativePath: String, file: Path, offset: Int, line: Int? = null, score: Float?, snippetRange: Int = DEFAULT_SNIPPET_RANGE): SearchResult {
            val content = file.readText()
            val actualLine = if (line != null) line else {
                content.substring(0, offset).count { it == '\n' } + 1
            }

            val lines = content.lines()
            val startLine = (actualLine - 1 - snippetRange).coerceAtLeast(0)
            val endLine = (actualLine - 1 + snippetRange).coerceAtMost(lines.size - 1)
            val snippet = lines.subList(startLine, endLine + 1).joinToString("\n")

            return SearchResult(relativePath, file, actualLine, snippet, score)
        }
    }
}