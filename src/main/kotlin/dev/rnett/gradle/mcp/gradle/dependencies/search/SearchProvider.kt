package dev.rnett.gradle.mcp.gradle.dependencies.search

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

interface SearchProvider {
    val name: String

    suspend fun search(indexDir: Path, query: String): List<RelativeSearchResult>
    suspend fun index(dependencyDir: Path, outputDir: Path)

    /**
     * [indexDirs] a map of index dirs to the relative path of that origin in the combined set
     */
    suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path)

    companion object {
        val SOURCE_EXTENSIONS = setOf("kt", "kts", "java", "groovy")
    }
}

data class RelativeSearchResult(
    val relativePath: String,
    val offset: Int,
    val line: Int? = null,
    val score: Float?
) {
    fun toSearchResult(sourcesRoot: Path): SearchResult {
        return SearchResult.fromFile(relativePath, sourcesRoot.resolve(relativePath), offset, line, score)
    }
}

fun Collection<RelativeSearchResult>.toSearchResults(sourcesRoot: Path): List<SearchResult> {
    return this.groupBy { it.relativePath }.flatMap { (relativePath, results) ->
        val file = sourcesRoot.resolve(relativePath)
        if (!file.exists()) return@flatMap emptyList()
        val content = file.readText()
        val lines = content.lines()

        results.map { res ->
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