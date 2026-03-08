package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.dependencies.search.toSearchResults
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class SourcesDir(val path: Path) {
    val sources = path.resolve("sources")
    val metadata = path.resolve("metadata")
    val index = metadata.resolve("index")
    val lastRefreshFile = metadata.resolve(".last_refresh")

    fun lastRefresh(): java.time.Instant? {
        if (lastRefreshFile.exists()) {
            return try {
                java.time.Instant.parse(lastRefreshFile.readText())
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}

interface SourcesService {

    /**
     * Download sources for all dependencies in the project.
     */
    context(progress: ProgressReporter)
    suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific project.
     */
    context(progress: ProgressReporter)
    suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific configuration.
     */
    context(progress: ProgressReporter)
    suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific source set.
     */
    context(progress: ProgressReporter)
    suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    suspend fun search(sources: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS): SearchResponse<SearchResult>

    suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents?
}

class DefaultSourcesService(private val depService: GradleDependencyService, environment: GradleMcpEnvironment, private val indexService: IndexService) : SourcesService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultSourcesService::class.java)
    }

    val sourcesDir = environment.cacheDir.resolve("sources")
    val globalSourcesDir = environment.cacheDir.resolve("extracted-sources")
    val gradleSourcesDir = environment.cacheDir.resolve("gradle-sources")

    init {
        sourcesDir.createDirectories()
        globalSourcesDir.createDirectories()
    }

    private fun sourcesDirectory(projectRoot: GradleProjectRoot, path: String, kind: String): SourcesDir {
        return SourcesDir(sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + path.hashCode().toString() + kind.hashCode().toString()))
    }

    @OptIn(ExperimentalPathApi::class)
    private fun dependencyHash(deps: Sequence<GradleDependency>): String {
        return deps
            .filter { it.sourcesFile != null && it.group != null && it.version != null }
            .map { "${it.id}:${it.sourcesFile}" }
            .sorted()
            .joinToString("\n")
            .hashCode()
            .toString()
    }

    @OptIn(ExperimentalPathApi::class)
    private fun checkCached(dir: SourcesDir, currentHash: String, forceDownload: Boolean): Boolean {
        val hashFile = dir.path.resolve(".dependencies.hash")
        if (forceDownload) {
            dir.path.deleteRecursively()
            return false
        }
        return hashFile.exists() && hashFile.readText() == currentHash
    }

    @OptIn(ExperimentalPathApi::class)
    private fun saveCache(dir: SourcesDir, currentHash: String) {
        dir.path.createDirectories()
        dir.metadata.createDirectories()
        dir.path.resolve(".dependencies.hash").writeText(currentHash)
        dir.lastRefreshFile.writeText(java.time.Instant.now().toString())
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, "", "root")
        val lockFile = dir.metadata.resolve(".lock")

        // 1. Try shared lock first to see if we can just return the cached dir
        if (!fresh && !forceDownload) {
            val cached = FileLockManager.withLock(lockFile, shared = true) {
                if (dir.sources.exists()) dir else null
            }
            if (cached != null) return cached
        }

        // 2. Need to update, acquire exclusive lock
        return FileLockManager.withLock(lockFile, shared = false) {
            if (!fresh && !forceDownload && dir.sources.exists()) return@withLock dir

            val deps = depService.downloadAllSources(projectRoot)
            val allDeps = deps.projects.asSequence().flatMap { it.allDependencies() }
            val currentHash = dependencyHash(allDeps)

            if (checkCached(dir, currentHash, forceDownload)) return@withLock dir

            processDependencies(allDeps, dir, index)
            saveCache(dir, currentHash)
            dir
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, projectPath, "project")
        val lockFile = dir.metadata.resolve(".lock")

        if (!fresh && !forceDownload) {
            val cached = FileLockManager.withLock(lockFile, shared = true) {
                if (dir.sources.exists()) dir else null
            }
            if (cached != null) return cached
        }

        return FileLockManager.withLock(lockFile, shared = false) {
            if (!fresh && !forceDownload && dir.sources.exists()) return@withLock dir

            val deps = depService.downloadProjectSources(projectRoot, projectPath)
            val allDeps = deps.allDependencies()
            val currentHash = dependencyHash(allDeps)

            if (checkCached(dir, currentHash, forceDownload)) return@withLock dir

            processDependencies(allDeps, dir, index)
            saveCache(dir, currentHash)
            dir
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, configurationPath, "configuration")
        val lockFile = dir.metadata.resolve(".lock")

        if (!fresh && !forceDownload) {
            val cached = FileLockManager.withLock(lockFile, shared = true) {
                if (dir.sources.exists()) dir else null
            }
            if (cached != null) return cached
        }

        return FileLockManager.withLock(lockFile, shared = false) {
            if (!fresh && !forceDownload && dir.sources.exists()) return@withLock dir

            val deps = depService.downloadConfigurationSources(projectRoot, configurationPath)
            val allDeps = deps.allDependencies()
            val currentHash = dependencyHash(allDeps)

            if (checkCached(dir, currentHash, forceDownload)) return@withLock dir

            processDependencies(allDeps, dir, index)
            saveCache(dir, currentHash)
            dir
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, sourceSetPath, "sourceSet")
        val lockFile = dir.metadata.resolve(".lock")

        if (!fresh && !forceDownload) {
            val cached = FileLockManager.withLock(lockFile, shared = true) {
                if (dir.sources.exists()) dir else null
            }
            if (cached != null) return cached
        }

        return FileLockManager.withLock(lockFile, shared = false) {
            if (!fresh && !forceDownload && dir.sources.exists()) return@withLock dir

            val deps = depService.downloadSourceSetSources(projectRoot, sourceSetPath)
            val allDeps = deps.configurations.asSequence().flatMap { it.allDependencies() }
            val currentHash = dependencyHash(allDeps)

            if (checkCached(dir, currentHash, forceDownload)) return@withLock dir

            processDependencies(allDeps, dir, index)
            saveCache(dir, currentHash)
            dir
        }
    }

    override suspend fun search(
        sources: SourcesDir,
        provider: SearchProvider,
        query: String,
        pagination: PaginationInput
    ): SearchResponse<SearchResult> {
        val response = indexService.search(sources, provider, query, pagination)
        val root = if (sources.path.startsWith(gradleSourcesDir)) {
            sources.sources
        } else {
            globalSourcesDir
        }
        return SearchResponse(
            results = response.results.toSearchResults(root),
            interpretedQuery = response.interpretedQuery,
            error = response.error
        )
    }

    override suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents? {
        return indexService.listPackageContents(sources, packageName)
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    private suspend fun processDependencies(deps: Sequence<GradleDependency>, target: SourcesDir, index: Boolean) {
        val validDeps = deps.filter { it.sourcesFile != null && it.group != null && it.version != null }.toSet()
        if (validDeps.isEmpty()) {
            LOGGER.info("No dependencies with sources to extract")
            return
        }
        LOGGER.info("Extracting sources for ${validDeps.size} dependencies")

        val targetSources = target.sources
        if (targetSources.exists()) {
            targetSources.deleteRecursively()
        }
        targetSources.createDirectories()

        val processingProgress = progress.withPhase("PROCESSING")
        val total = validDeps.size.toDouble()
        val current = AtomicInteger(0)

        val chunkSize = maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
        val indices = coroutineScope {
            validDeps.chunked(chunkSize).flatMap { chunk ->
                chunk.map { dep ->
                    async(Dispatchers.IO) {
                        val count = current.incrementAndGet().toDouble()
                        processingProgress(count, total, "Processing sources for ${dep.id}")

                        val ext = dep.sourcesFile!!.extension
                        val relativePath = Path(dep.group!!).resolve(dep.sourcesFile.nameWithoutExtension)
                        val dir = globalSourcesDir.resolve(relativePath)
                        val lockFile = dir.resolve(".lock")
                        val extractionMarker = dir.resolve(".extracted")

                        val success = FileLockManager.withLock(lockFile) {
                            if (!extractionMarker.exists()) {
                                dir.deleteRecursively()
                                dir.createDirectories()
                                try {
                                    if (index) {
                                        val filesChannel = kotlinx.coroutines.channels.Channel<IndexEntry>(capacity = 10)
                                        coroutineScope {
                                            launch(Dispatchers.IO) {
                                                val flow = filesChannel.receiveAsFlow()
                                                indexService.index(dep, flow)
                                            }
                                            try {
                                                with(ProgressReporter.NONE) {
                                                    ArchiveExtractor.extractInto(dir, dep.sourcesFile, skipSingleFirstDir = true, writeFiles = true) { path, contentBytes ->
                                                        filesChannel.send(IndexEntry(path, String(contentBytes, Charsets.UTF_8)))
                                                    }
                                                }
                                            } finally {
                                                filesChannel.close()
                                            }
                                        }
                                    } else {
                                        with(ProgressReporter.NONE) {
                                            ArchiveExtractor.extractInto(dir, dep.sourcesFile, skipSingleFirstDir = true, writeFiles = true)
                                        }
                                    }
                                    extractionMarker.createFile()
                                    true
                                } catch (e: Exception) {
                                    LOGGER.error("Failed to extract sources for $dep", e)
                                    dir.deleteRecursively()
                                    false
                                }
                            } else true
                        }

                        if (!success) return@async null

                        // Sync to target.sources
                        val scopeLink = targetSources.resolve(relativePath)
                        scopeLink.createParentDirectories()
                        try {
                            // Try to create a symbolic link (incremental sync)
                            // Note: On Windows this might require developer mode or admin rights
                            Files.createSymbolicLink(scopeLink, dir)
                        } catch (e: Exception) {
                            // Fallback to copy or just ignore (search will still work via globalSourcesDir)
                            LOGGER.warn("Failed to create symbolic link for $dep in $targetSources. Falling back to recursive copy.", e)
                            dir.toFile().copyRecursively(scopeLink.toFile(), overwrite = true)
                        }

                        if (index) {
                            indexService.index(dep, dir)?.let { relativePath to it }
                        } else null
                    }
                }.awaitAll().filterNotNull()
            }
        }
        if (indices.isNotEmpty()) {
            indexService.mergeIndices(
                target,
                indices.toMap()
            )
        }
    }

}