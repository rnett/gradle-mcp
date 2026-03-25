package dev.rnett.gradle.mcp.fixtures.dependencies.search

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.withPhase
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

@OptIn(ExperimentalPathApi::class)
context(progress: ProgressReporter)
suspend fun SearchProvider.index(dependencyDir: Path, outputDir: Path, prefix: Path? = null) {
    val entries = dependencyDir.walk()
        .filter { it.isRegularFile() }
        .map { file ->
            val relativePath = file.relativeTo(dependencyDir)
            val fullRelativePath = if (prefix != null) prefix.resolve(relativePath) else relativePath
            IndexEntry(fullRelativePath.toString().replace('\\', '/')) { file.readText() }
        }
    index(entries, outputDir)
}

context(progress: ProgressReporter)
suspend fun SearchProvider.index(entries: Sequence<IndexEntry>, outputDir: Path) {
    val indexingProgress = progress.withPhase("INDEXING")
    var count = 0
    newIndexer(outputDir).use { indexer ->
        for (entry in entries) {
            count++
            indexingProgress.report(count.toDouble(), null, "Indexing ${this@index.name} for ${entry.relativePath}")
            indexer.indexFile(entry.relativePath, entry.content)
        }
        indexer.finish()
    }
}
