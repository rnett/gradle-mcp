package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
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
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

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

@OptIn(ExperimentalPathApi::class)
class DefaultSourcesService(
    private val depService: GradleDependencyService,
    private val storageService: SourceStorageService,
    private val indexService: dev.rnett.gradle.mcp.dependencies.search.IndexService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SourcesService {
    private val logger = LoggerFactory.getLogger(DefaultSourcesService::class.java)

    context(progress: ProgressReporter)
    private suspend fun resolveAndProcessSourcesInternal(
        forceDownload: Boolean,
        matcher: DependencyFilterMatcher,
        providerToIndex: SearchProvider? = null,
        resolve: suspend () -> Sequence<GradleDependency>
    ): SourcesDir {
        storageService.pruneSessionViews()

        val allDepsSeq = resolve()
        val filteredDepsSeq = if (matcher.filter != null) {
            allDepsSeq.filter { matcher.matches(it) }
        } else allDepsSeq

        val filteredDeps = filteredDepsSeq.filter { it.hasSources }.toList()

        if (filteredDeps.isEmpty()) {
            if (matcher.filter != null) {
                throw IllegalArgumentException("Dependency filter '${matcher.filter}' matched zero dependencies in scope with sources.")
            } else {
                throw IllegalArgumentException("Matched zero dependencies in scope with sources.")
            }
        }

        val processingProgress = progress.withPhase("PROCESSING")
        val tracker = ParallelProgressTracker(processingProgress, filteredDeps.size.toDouble())

        val depToCasDir = filteredDeps.associateWith { dep ->
            val hash = storageService.calculateHash(dep)
            storageService.getCASDependencySourcesDir(hash)
        }

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

        return storageService.createSessionView(depToCasDir)
    }

    context(progress: ProgressReporter)
    private suspend fun processCasDependency(
        dep: GradleDependency,
        casDir: CASDependencySourcesDir,
        forceDownload: Boolean,
        providerToIndex: SearchProvider?
    ) {
        if (!forceDownload && casDir.completionMarker.exists()) {
            progress.report(1.0, 1.0, "Already processed ${dep.id}")
            return
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
                progress.report(1.0, 1.0, "Already processed ${dep.id}")
                return
            }

            val tempDir = casDir.baseDir.resolveSibling("${casDir.hash}.tmp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.createDirectories()

            try {
                if (providerToIndex != null) {
                    orchestrateExtractionAndIndexing(dep, tempDir, forceDownload, providerToIndex)
                } else {
                    storageService.extractSources(tempDir.resolve("sources"), dep)
                }

                // If another process completed it while we were working, atomicMoveIfAbsent will handle it
                FileUtils.atomicMoveIfAbsent(tempDir, casDir.baseDir)
                if (!casDir.completionMarker.exists()) {
                    try {
                        casDir.completionMarker.createFile()
                    } catch (e: java.nio.file.FileAlreadyExistsException) {
                        // ignore
                    }
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
        providerToIndex: SearchProvider
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
            storageService.extractSources(tempDir.resolve("sources"), dep) { path, contentBytes ->
                val fullRelativePath = storageService.normalizeRelativePath(relativePrefix, path)
                val ext = fullRelativePath.substringAfterLast('.', "")
                if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                    channel.send(IndexEntry(fullRelativePath) { contentBytes.decodeToString() })
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
        return resolveAndProcessSourcesInternal(forceDownload, DependencyFilterMatcher(dependency), providerToIndex) {
            depService.downloadAllSources(projectRoot, dependency).projects.asSequence().flatMap { it.allDependencies() }
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessProjectSources(projectRoot: GradleProjectRoot, projectPath: String, dependency: String?, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return resolveAndProcessSourcesInternal(forceDownload, DependencyFilterMatcher(dependency), providerToIndex) {
            depService.downloadProjectSources(projectRoot, projectPath, dependency).allDependencies()
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
        return resolveAndProcessSourcesInternal(forceDownload, DependencyFilterMatcher(dependency), providerToIndex) {
            depService.downloadConfigurationSources(projectRoot, configurationPath, dependency).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, dependency: String?, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return resolveAndProcessSourcesInternal(forceDownload, DependencyFilterMatcher(dependency), providerToIndex) {
            depService.downloadSourceSetSources(projectRoot, sourceSetPath, dependency).configurations.asSequence().flatMap { it.allDependencies() }
        }
    }
}