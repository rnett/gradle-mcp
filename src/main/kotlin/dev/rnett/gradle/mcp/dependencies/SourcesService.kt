package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.dependencies.search.toSearchResults
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.Concurrency
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.FileUtils
import dev.rnett.gradle.mcp.withMessage
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class SourcesDir(val storagePath: Path) {

    val sources = storagePath.resolve("sources")
    val metadata = storagePath.resolve("metadata")
    val index = metadata.resolve("index")
    val lastRefreshFile = metadata.resolve(".last_refresh")

    fun lastRefresh(): kotlin.time.Instant? {
        if (lastRefreshFile.exists()) {
            return try {
                kotlin.time.Instant.parse(lastRefreshFile.readText())
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

class DefaultSourcesService(private val depService: GradleDependencyService, private val environment: GradleMcpEnvironment, private val indexService: IndexService) : SourcesService {
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
        val storagePath = sourcesDir.resolve("${projectRoot.projectRoot.hashCode()}${path.hashCode()}${kind.hashCode()}")
        return SourcesDir(storagePath)
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
        val hashFile = dir.storagePath.resolve(".dependencies.hash")
        if (forceDownload) {
            dir.storagePath.deleteRecursively()
            return false
        }
        return hashFile.exists() && hashFile.readText() == currentHash
    }

    @OptIn(ExperimentalPathApi::class)
    private fun saveCache(dir: SourcesDir, currentHash: String) {
        dir.storagePath.createDirectories()
        dir.metadata.createDirectories()
        dir.storagePath.resolve(".dependencies.hash").writeText(currentHash)
        dir.lastRefreshFile.writeText(kotlin.time.Clock.System.now().toString())
    }

    private fun lockFile(storagePath: Path): Path {
        return environment.lockFile(storagePath, "sources")
    }

    private fun globalLockFile(dir: Path): Path {
        return environment.cacheDir.resolve(".locks").resolve("extracted-sources").resolve(dir.fileName.toString() + ".lock")
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, "", "root")
        val lockFile = lockFile(dir.storagePath)

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
        val lockFile = lockFile(dir.storagePath)

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
        val lockFile = lockFile(dir.storagePath)

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
        val lockFile = lockFile(dir.storagePath)

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
        val lockFile = lockFile(sources.storagePath)
        return FileLockManager.withLock(lockFile, shared = true) {
            val response = indexService.search(sources, provider, query, pagination)
            val root = if (sources.storagePath.startsWith(gradleSourcesDir)) {
                sources.sources
            } else {
                globalSourcesDir
            }
            SearchResponse(
                results = response.results.toSearchResults(root),
                interpretedQuery = response.interpretedQuery,
                error = response.error
            )
        }
    }

    override suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents? {
        val lockFile = lockFile(sources.storagePath)
        return FileLockManager.withLock(lockFile, shared = true) {
            indexService.listPackageContents(sources, packageName)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    private suspend fun processDependencies(deps: Sequence<GradleDependency>, target: SourcesDir, index: Boolean) {
        val validDeps = deps.filter { it.sourcesFile != null && it.group != null && it.version != null }.toSet()

        val indices: List<Pair<Path, Index>> = if (validDeps.isEmpty()) {
            LOGGER.info("No dependencies with sources to extract")
            progress.report(1.0, 1.0, "[DETECTING] No dependencies found")
            emptyList()
        } else {
            progress.report(0.0, 1.0, "[DETECTING] Finding dependencies with sources")
            LOGGER.info("Extracting sources for ${validDeps.size} dependencies")

            val targetSources = target.sources
            if (targetSources.exists()) {
                targetSources.deleteRecursively()
            }
            targetSources.createDirectories()
            progress.report(1.0, 1.0, "[DETECTING] Found ${validDeps.size} dependencies")

            val processingProgress = progress.withPhase("PROCESSING")

            // Group dependencies by extraction directory to avoid redundant extractions
            val depsByDir = validDeps.groupBy { dep ->
                globalSourcesDir.resolve(Path(requireNotNull(dep.group)).resolve(requireNotNull(dep.sourcesFile).nameWithoutExtension))
            }

            val total = depsByDir.size.toDouble()

            @OptIn(ExperimentalAtomicApi::class)
            val completedCounter = AtomicInt(0)
            val activeTasks = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
            val lastActivity = AtomicReference<String?>(null)

            fun reportProcessing() {
                val completed = completedCounter.load()
                val currentMessage = lastActivity.load() ?: "Processing dependencies"
                val inProgressCount = activeTasks.size
                val message = if (inProgressCount > 0) "$currentMessage ($inProgressCount in progress)" else currentMessage
                processingProgress.report(completed.toDouble(), total, message)
            }

            val concurrency = Concurrency.DEFAULT_IO_CONCURRENCY
            val semaphore = Semaphore(concurrency)
            coroutineScope {
                depsByDir.entries.map { (dir, depsForDir) ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val dep = depsForDir.first()
                            val depId = dep.id
                            activeTasks.add(depId)

                            val subProgress = ProgressReporter { _, _, m ->
                                if (m != null) {
                                    lastActivity.store(m)
                                    reportProcessing()
                                }
                            }

                            try {
                                val lockFile = globalLockFile(dir)
                                FileLockManager.withLock(lockFile) {
                                    with(subProgress) {
                                        extractDependencySources(dir, dep, index, subProgress)
                                    }
                                }

                                // Use shared lock for read sync operations
                                val results = FileLockManager.withLock(lockFile, shared = true) {
                                    // Sync all dependencies that share this directory, but only once per relativePath
                                    depsForDir.distinctBy { d ->
                                        Path(requireNotNull(d.group)).resolve(requireNotNull(d.sourcesFile).nameWithoutExtension)
                                    }.mapNotNull { d ->
                                        val relativePath = Path(requireNotNull(d.group)).resolve(requireNotNull(d.sourcesFile).nameWithoutExtension)
                                        val scopeLink = targetSources.resolve(relativePath)

                                        syncDependencySources(scopeLink, dir, d, target.sources)

                                        if (index) {
                                            indexDependencySources(d, dir, relativePath, subProgress)
                                        } else null
                                    }
                                }
                                completedCounter.addAndFetch(1)
                                reportProcessing()
                                results
                            } finally {
                                activeTasks.remove(depId)
                                reportProcessing()
                            }
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        if (index) {
            indexService.mergeIndices(
                target,
                indices.toMap()
            )
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(extractionProgress: ProgressReporter)
    private suspend fun extractDependencySources(
        dir: Path,
        dep: GradleDependency,
        index: Boolean,
        indexingProgress: ProgressReporter
    ) {
        val extractionMarker = dir.resolve(".extracted")
        if (!extractionMarker.exists()) {
            dir.deleteRecursively()
            dir.createDirectories()
            try {
                val currentExtractionProgress = extractionProgress.withMessage { "Extracting sources for ${dep.id}" }
                currentExtractionProgress.report(0.0, 1.0, "Extracting sources for ${dep.id}")
                if (index) {
                    val filesChannel = Channel<IndexEntry>(capacity = 20)
                    coroutineScope {
                        val job = launch(Dispatchers.IO) {
                            val flow = filesChannel.consumeAsFlow()
                            val currentIndexingProgress = indexingProgress.withMessage { "Indexing sources for ${dep.id}" }
                            with(currentIndexingProgress) {
                                indexService.indexFiles(dep, flow)
                            }
                        }
                        try {
                            with(currentExtractionProgress) {
                                ArchiveExtractor.extractInto(dir, requireNotNull(dep.sourcesFile), skipSingleFirstDir = true, writeFiles = true) { path, contentBytes ->
                                    filesChannel.send(IndexEntry(path, String(contentBytes, Charsets.UTF_8)))
                                }
                            }
                        } finally {
                            filesChannel.close()
                        }
                        job.join()
                    }
                } else {
                    with(currentExtractionProgress) {
                        ArchiveExtractor.extractInto(dir, requireNotNull(dep.sourcesFile), skipSingleFirstDir = true, writeFiles = true)
                    }
                }
                extractionMarker.createFile()
            } catch (e: Exception) {
                LOGGER.error("Failed to extract sources for $dep", e)
                dir.deleteRecursively()
                throw e
            }
        } else {
            extractionProgress.report(1.0, 1.0, "Processing sources for ${dep.id}")
        }
    }

    private fun syncDependencySources(scopeLink: Path, dir: Path, d: GradleDependency, targetSources: Path) {
        scopeLink.createParentDirectories()
        if (!FileUtils.createSymbolicLink(scopeLink, dir)) {
            LOGGER.warn("Failed to create symbolic link for {} to {}. Falling back to recursive copy. This will consume more disk space and time.", d, targetSources)
            try {
                dir.toFile().copyRecursively(scopeLink.toFile(), overwrite = true)
            } catch (e2: Exception) {
                LOGGER.error("Failed to sync sources for $d to $targetSources.", e2)
            }
        }
    }

    private suspend fun indexDependencySources(
        d: GradleDependency,
        dir: Path,
        relativePath: Path,
        indexingProgress: ProgressReporter
    ): Pair<Path, Index>? {
        val currentIndexingProgress = indexingProgress.withMessage { "Indexing sources for ${d.id}" }
        currentIndexingProgress.report(0.0, 1.0, "Indexing sources for ${d.id}")
        val indexDir = with(currentIndexingProgress) {
            indexService.index(d, dir)
        }
        return indexDir?.let { relativePath to it }
    }
}
