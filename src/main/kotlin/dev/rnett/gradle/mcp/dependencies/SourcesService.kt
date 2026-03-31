package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.markerFileName
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.FileUtils
import dev.rnett.gradle.mcp.utils.unorderedParallelMap
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * High-level service for managing and searching dependency sources.
 * Acts as the primary entry point for tool handlers.
 */
interface SourcesService {
    /**
     * Resolves and processes sources for all dependencies in the specified [projectRoot].
     * If [dependency] is provided, filters the result to only include that dependency.
     * Optionally indexes the sources and manages cache freshness.
     */
    context(progress: ProgressReporter)
    suspend fun resolveAndProcessAllSources(
        projectRoot: GradleProjectRoot,
        dependency: String? = null,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): SourcesDir

    /**
     * Resolves and processes sources for dependencies within a specific Gradle project.
     */
    context(progress: ProgressReporter)
    suspend fun resolveAndProcessProjectSources(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        dependency: String? = null,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): SourcesDir

    /**
     * Resolves and processes sources for dependencies within a specific Gradle configuration.
     */
    context(progress: ProgressReporter)
    suspend fun resolveAndProcessConfigurationSources(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String? = null,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): SourcesDir

    /**
     * Resolves and processes sources for dependencies within a specific Gradle source set.
     */
    context(progress: ProgressReporter)
    suspend fun resolveAndProcessSourceSetSources(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String? = null,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): SourcesDir
}

/**
 * Default implementation of [SourcesService].
 * Manages the resolution, extraction, and indexing of dependency sources.
 * Uses a project-level cache to reuse session views and avoid redundant Gradle builds.
 * Ensures safe concurrent access to CAS entries using filesystem-level locks.
 */
@OptIn(ExperimentalPathApi::class)
class DefaultSourcesService(
    private val depService: GradleDependencyService,
    private val storageService: SourceStorageService,
    private val indexService: dev.rnett.gradle.mcp.dependencies.search.IndexService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SourcesService {
    private val logger = LoggerFactory.getLogger(DefaultSourcesService::class.java)

    private data class CacheKey(
        val scope: SourceScope,
        val dependencyFilter: String?
    )

    private data class CachedView(
        val sourcesDir: SourcesDir,
        val depToCasDir: Map<GradleDependency, CASDependencySourcesDir>
    )

    private val cache = ConcurrentHashMap<CacheKey, CachedView>()
    private val keyLocks = ConcurrentHashMap<CacheKey, Mutex>()

    private sealed interface SourceScope {
        data class All(val projectRoot: GradleProjectRoot) : SourceScope
        data class Project(val projectRoot: GradleProjectRoot, val projectPath: String) : SourceScope
        data class Configuration(val projectRoot: GradleProjectRoot, val configurationPath: String) : SourceScope
        data class SourceSet(val projectRoot: GradleProjectRoot, val sourceSetPath: String) : SourceScope
    }

    context(progress: ProgressReporter)
    private suspend fun resolveAndProcessSourcesInternal(
        scope: SourceScope,
        forceDownload: Boolean,
        fresh: Boolean,
        matcher: DependencyFilterMatcher,
        providerToIndex: SearchProvider? = null,
        resolve: suspend () -> Sequence<GradleDependency>
    ): SourcesDir {
        val key = CacheKey(scope, matcher.dependencyFilter)
        val lock = keyLocks.computeIfAbsent(key) { Mutex() }

        return lock.withLock {
            if (fresh || forceDownload) {
                cache.remove(key)
            }

            val cached = cache[key]
            if (cached != null && cached.sourcesDir.sources.exists()) {
                if (providerToIndex != null) {
                    runProcessingLoop(cached.depToCasDir, forceDownload, providerToIndex)
                }
                return@withLock cached.sourcesDir
            }

            storageService.pruneSessionViews()

            val allDepsSeq = resolve()
            val filteredDepsSeq = if (matcher.dependencyFilter != null) {
                allDepsSeq.filter { matcher.matchesDependency(it) }
            } else allDepsSeq

            val filteredDeps = filteredDepsSeq.filter { it.hasSources }.toList()

            if (filteredDeps.isEmpty()) {
                if (matcher.dependencyFilter != null) {
                    throw IllegalArgumentException("Dependency filter '${matcher.dependencyFilter}' matched zero dependencies in scope with sources.")
                } else {
                    throw IllegalArgumentException("Matched zero dependencies in scope with sources.")
                }
            }

            val depToCasDir = filteredDeps.associateWith { dep ->
                val hash = storageService.calculateHash(dep)
                storageService.getCASDependencySourcesDir(hash)
            }

            runProcessingLoop(depToCasDir, forceDownload, providerToIndex)

            val result = storageService.createSessionView(depToCasDir, force = forceDownload)
            cache[key] = CachedView(result, depToCasDir)
            result
        }
    }

    context(progress: ProgressReporter)
    private suspend fun runProcessingLoop(
        depToCasDir: Map<GradleDependency, CASDependencySourcesDir>,
        forceDownload: Boolean,
        providerToIndex: SearchProvider?
    ) {
        val processingProgress = progress.withPhase("PROCESSING")
        val tracker = ParallelProgressTracker(processingProgress, depToCasDir.size.toDouble())

        depToCasDir.entries.unorderedParallelMap(context = dispatcher) { (dep, casDir) ->
            val taskId = dep.id
            val subProgress = ProgressReporter { p, t, m ->
                tracker.reportProgress(taskId, p, t, m)
            }

            try {
                tracker.onStart(taskId)
                with(subProgress) {
                    processCasDependency(dep, casDir, forceDownload, providerToIndex)
                }
            } finally {
                tracker.onComplete(taskId, count = 1)
            }
        }
    }

    context(progress: ProgressReporter)
    private suspend fun processCasDependency(
        dep: GradleDependency,
        casDir: CASDependencySourcesDir,
        forceDownload: Boolean,
        providerToIndex: SearchProvider?
    ) {
        if (!forceDownload && casDir.completionMarker.exists()) {
            if (providerToIndex == null || casDir.index.resolve(providerToIndex.markerFileName).exists()) {
                progress.report(1.0, 1.0, "Already processed ${dep.id}")
                return
            }
        }

        val lock = FileLockManager.tryLockAdvisory(casDir.advisoryLockFile)
        if (lock == null) {
            progress.report(0.0, 1.0, "Waiting for another process to complete ${dep.id}")
            storageService.waitForCAS(casDir)
            return
        }

        lock.use {
            // Check again after acquiring lock in case we raced
            if (!forceDownload && casDir.completionMarker.exists()) {
                if (providerToIndex == null || casDir.index.resolve(providerToIndex.markerFileName).exists()) {
                    progress.report(1.0, 1.0, "Already processed ${dep.id}")
                    return
                }
            }

            val tempDir = casDir.baseDir.resolveSibling("${casDir.hash}.${java.util.UUID.randomUUID()}.tmp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.createDirectories()

            casDir.baseDir.createDirectories()
            try {
                if (forceDownload) {
                    casDir.sources.deleteRecursively()
                    casDir.index.deleteRecursively()
                    casDir.completionMarker.deleteIfExists()
                }

                if (providerToIndex != null) {
                    logger.info("Indexing ${dep.id} with provider ${providerToIndex.name}")
                    orchestrateExtractionAndIndexing(dep, tempDir, forceDownload, providerToIndex, casDir)

                    val tempIndexBaseDir = tempDir.resolve("index")
                    val tempProviderDir = tempIndexBaseDir.resolve(providerToIndex.name)
                    if (tempProviderDir.exists()) {
                        val targetProviderDir = casDir.index.resolve(providerToIndex.name)
                        logger.info("Moving index for ${dep.id} from $tempProviderDir to $targetProviderDir")
                        targetProviderDir.parent.createDirectories()
                        FileUtils.atomicReplaceDirectory(tempProviderDir, targetProviderDir)

                        val markerFile = tempIndexBaseDir.resolve(providerToIndex.markerFileName)
                        if (markerFile.exists()) {
                            val targetMarker = casDir.index.resolve(providerToIndex.markerFileName)
                            targetMarker.parent.createDirectories()
                            Files.move(markerFile, targetMarker, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        }
                    } else {
                        logger.warn("Index directory $tempProviderDir does not exist after indexing ${dep.id}")
                    }
                } else {
                }

                val tempSourcesDir = tempDir.resolve("sources")
                if (tempSourcesDir.exists()) {
                    logger.info("Moving extracted sources from $tempSourcesDir to ${casDir.sources}")
                    FileUtils.atomicMoveIfAbsent(tempSourcesDir, casDir.sources)
                }

                try {
                    casDir.completionMarker.createFile()
                } catch (e: java.nio.file.FileAlreadyExistsException) {
                    logger.info("Completion marker already exists for ${dep.id}, another process might have finished it.")
                }
            } catch (e: Exception) {
                logger.error("Failed to process CAS entry for ${dep.id}", e)
                throw e
            } finally {
                if (tempDir.exists()) tempDir.deleteRecursively()
            }
        }
    }

    context(progress: ProgressReporter)
    private suspend fun orchestrateExtractionAndIndexing(
        dep: GradleDependency,
        tempDir: Path,
        forceDownload: Boolean,
        providerToIndex: SearchProvider,
        casDir: CASDependencySourcesDir? = null
    ) = coroutineScope {
        val channel = Channel<IndexEntry>(capacity = 20)
        val relativePrefix = dep.relativePrefix?.let { kotlin.io.path.Path(it) } ?: kotlin.io.path.Path("")

        val indexJob = launch(dispatcher) {
            try {
                val indexResult = indexService.indexFiles(tempDir, channel.consumeAsFlow(), providerToIndex)
                if (indexResult == null) {
                    throw IllegalStateException("Indexing failed for ${dep.id} with provider ${providerToIndex.name}")
                }
            } finally {
                for (entry in channel) {
                }
            }
        }

        try {
            val sourcesDir = casDir?.sources?.takeIf { !forceDownload && it.exists() }
            if (sourcesDir != null) {
                // Already extracted, just walk and index
                Files.walk(sourcesDir).use { stream ->
                    for (file in stream) {
                        if (Files.isRegularFile(file)) {
                            val ext = file.fileName.toString().substringAfterLast('.', "")
                            if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                                val pathWithinDep = sourcesDir.relativize(file).toString().replace('\\', '/')
                                val fullRelativePath = storageService.normalizeRelativePath(relativePrefix, pathWithinDep)
                                channel.send(IndexEntry(fullRelativePath) { file.readText() })
                            }
                        }
                    }
                }
            } else {
                storageService.extractSources(tempDir.resolve("sources"), dep) { path, contentBytes ->
                    val fullRelativePath = storageService.normalizeRelativePath(relativePrefix, path)
                    val ext = fullRelativePath.substringAfterLast('.', "")
                    if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                        channel.send(IndexEntry(fullRelativePath) { contentBytes.decodeToString() })
                    }
                }
            }
        } catch (e: Exception) {
            indexJob.cancel()
            throw e
        } finally {
            channel.close()
        }

        indexJob.join()
    }


    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessAllSources(projectRoot: GradleProjectRoot, dependency: String?, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return resolveAndProcessSourcesInternal(SourceScope.All(projectRoot), forceDownload, fresh, DependencyFilterMatcher(dependency), providerToIndex) {
            depService.downloadAllSources(projectRoot, dependency, fresh = fresh || forceDownload).projects.asSequence().flatMap { it.allDependencies() }
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessProjectSources(projectRoot: GradleProjectRoot, projectPath: String, dependency: String?, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return resolveAndProcessSourcesInternal(SourceScope.Project(projectRoot, projectPath), forceDownload, fresh, DependencyFilterMatcher(dependency), providerToIndex) {
            depService.downloadProjectSources(projectRoot, projectPath, dependency, fresh = fresh || forceDownload).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessConfigurationSources(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String?,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider?
    ): SourcesDir {
        return resolveAndProcessSourcesInternal(SourceScope.Configuration(projectRoot, configurationPath), forceDownload, fresh, DependencyFilterMatcher(dependency), providerToIndex) {
            depService.downloadConfigurationSources(projectRoot, configurationPath, dependency, fresh = fresh || forceDownload).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, dependency: String?, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return resolveAndProcessSourcesInternal(SourceScope.SourceSet(projectRoot, sourceSetPath), forceDownload, fresh, DependencyFilterMatcher(dependency), providerToIndex) {
            depService.downloadSourceSetSources(projectRoot, sourceSetPath, dependency, fresh = fresh || forceDownload).configurations.asSequence().flatMap { it.allDependencies() }
        }
    }
}