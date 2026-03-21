package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.utils.FileUtils
import dev.rnett.gradle.mcp.withMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Handles the extraction, indexing, and syncing of dependency sources.
 */
@OptIn(ExperimentalPathApi::class)
class SourcesProcessor(private val indexService: IndexService) {
    private val logger = LoggerFactory.getLogger(SourcesProcessor::class.java)

    /**
     * Extracts sources for a dependency and optionally indexes them during extraction.
     */
    context(extractionProgress: ProgressReporter)
    suspend fun extractAndIndex(
        dir: Path,
        dep: GradleDependency,
        index: Boolean,
        indexingProgress: ProgressReporter,
        forceDownload: Boolean = false,
        providerToIndex: SearchProvider? = null
    ) {
        val extractionMarker = dir.resolve(".extracted")
        if (!forceDownload && extractionMarker.exists()) {
            extractionProgress.report(1.0, 1.0, "Processing sources for ${dep.id}")
            return
        }

        dir.deleteRecursively()
        dir.toFile().mkdirs()

        try {
            if (index) {
                extractWithIndexing(dir, dep, indexingProgress, forceDownload, providerToIndex)
            } else {
                extractOnly(dir, dep)
            }
            extractionMarker.createFile()
        } catch (e: Exception) {
            logger.error("Failed to extract sources for $dep", e)
            dir.deleteRecursively()
            throw e
        }
    }

    private suspend fun extractOnly(dir: Path, dep: GradleDependency) {
        with(ProgressReporter.NONE) {
            ArchiveExtractor.extractInto(dir, requireNotNull(dep.sourcesFile), skipSingleFirstDir = true, writeFiles = true)
        }
    }

    private suspend fun extractWithIndexing(
        dir: Path,
        dep: GradleDependency,
        indexingProgress: ProgressReporter,
        forceDownload: Boolean,
        providerToIndex: SearchProvider? = null
    ) = coroutineScope {
        if (providerToIndex == null) {
            extractOnly(dir, dep)
            return@coroutineScope
        }

        val channel = Channel<IndexEntry>(capacity = 20)

        val currentIndexingProgress = indexingProgress.withMessage { "Indexing sources for ${dep.id}" }
        val job = launch(Dispatchers.IO) {
            with(currentIndexingProgress) {
                indexService.indexFiles(dep, channel.consumeAsFlow(), providerToIndex, forceIndex = forceDownload)
            }
        }

        try {
            with(ProgressReporter.NONE) {
                ArchiveExtractor.extractInto(dir, requireNotNull(dep.sourcesFile), skipSingleFirstDir = true, writeFiles = true) { path, contentBytes ->
                    val entry = IndexEntry(path, String(contentBytes, Charsets.UTF_8))
                    channel.send(entry)
                }
            }
        } finally {
            channel.close()
        }
        job.join()
    }

    /**
     * Ensures that the sources for a dependency are indexed. 
     * Uses [IndexService]'s internal caching to avoid redundant work.
     */
    context(indexingProgress: ProgressReporter)
    suspend fun ensureIndexed(
        dep: GradleDependency,
        dir: Path,
        forceDownload: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): Index? {
        if (providerToIndex == null) return null

        // Check cache before creating the flow to avoid unnecessary I/O on cache hits
        if (!forceDownload && indexService.isIndexed(dep, providerToIndex)) {
            return indexService.getIndex(dep, providerToIndex)
        }

        val currentIndexingProgress = indexingProgress.withMessage { "Indexing sources for ${dep.id}" }

        return with(currentIndexingProgress) {
            indexService.indexFiles(dep, flow {
                dir.walk().filter { it.isRegularFile() && it.fileName.toString() != ".extracted" }.forEach { file ->
                    val rel = file.relativeTo(dir).toString().replace('\\', '/')
                    emit(IndexEntry(rel, file.readText()))
                }
            }, providerToIndex, forceIndex = forceDownload)
        }
    }

    /**
     * Syncs extracted sources to a project-specific scope using symbolic links, 
     * falling back to recursive copying with a warning if links are not supported.
     */
    fun syncTo(scopeLink: Path, dir: Path, dep: GradleDependency, targetSources: Path) {
        scopeLink.createParentDirectories()
        if (!FileUtils.createSymbolicLink(scopeLink, dir)) {
            logger.warn("Failed to create symbolic link for {} to {}. Falling back to recursive copy. This will consume more disk space and time. Consider enabling 'Developer Mode' on Windows.", dep.id, targetSources)
            try {
                dir.copyToRecursively(scopeLink, followLinks = false, overwrite = true)
            } catch (e2: Exception) {
                logger.error("Failed to sync sources for $dep to $targetSources.", e2)
            }
        }
    }
}
