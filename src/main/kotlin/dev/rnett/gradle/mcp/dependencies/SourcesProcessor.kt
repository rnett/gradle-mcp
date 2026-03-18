package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.IndexService
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
        forceDownload: Boolean = false
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
                extractWithIndexing(dir, dep, indexingProgress, forceDownload)
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
        forceDownload: Boolean
    ) = coroutineScope {
        val filesChannel = Channel<IndexEntry>(capacity = 20)
        val job = launch(Dispatchers.IO) {
            val flow = filesChannel.consumeAsFlow()
            val currentIndexingProgress = indexingProgress.withMessage { "Indexing sources for ${dep.id}" }
            with(currentIndexingProgress) {
                indexService.indexFiles(dep, flow, forceIndex = forceDownload)
            }
        }

        try {
            with(ProgressReporter.NONE) {
                ArchiveExtractor.extractInto(dir, requireNotNull(dep.sourcesFile), skipSingleFirstDir = true, writeFiles = true) { path, contentBytes ->
                    filesChannel.send(IndexEntry(path, String(contentBytes, Charsets.UTF_8)))
                }
            }
        } finally {
            filesChannel.close()
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
        forceDownload: Boolean = false
    ): Index? {
        val currentIndexingProgress = indexingProgress.withMessage { "Indexing sources for ${dep.id}" }
        return with(currentIndexingProgress) {
            indexService.indexFiles(dep, flow {
                dir.walk().filter { it.isRegularFile() && it.fileName.toString() != ".extracted" }.forEach { file ->
                    val rel = file.relativeTo(dir).toString().replace('\\', '/')
                    emit(IndexEntry(rel, file.readText()))
                }
            }, forceIndex = forceDownload)
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
                dir.toFile().copyRecursively(scopeLink.toFile(), overwrite = true)
            } catch (e2: Exception) {
                logger.error("Failed to sync sources for $dep to $targetSources.", e2)
            }
        }
    }
}
