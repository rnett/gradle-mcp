package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SingleDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.utils.FileLockManager
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
        index: Boolean = true,
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
        index: Boolean = true,
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
        index: Boolean = true,
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
        index: Boolean = true,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): SourcesDir
}

@OptIn(ExperimentalPathApi::class)
class DefaultSourcesService(
    private val depService: GradleDependencyService,
    private val storageService: SourceStorageService,
    private val indexService: SourceIndexService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SourcesService {
    private val logger = LoggerFactory.getLogger(DefaultSourcesService::class.java)

    context(progress: ProgressReporter)
    private suspend fun resolveAndProcessSourcesInternal(
        dir: MergedSourcesDir,
        index: Boolean,
        forceDownload: Boolean,
        matcher: DependencyFilterMatcher,
        providerToIndex: SearchProvider? = null,
        resolve: suspend () -> Sequence<GradleDependency>
    ): SourcesDir {
        val allDepsSeq = resolve()
        val filteredDepsSeq = if (matcher.filter != null) {
            allDepsSeq.filter { matcher.matches(it) }
        } else allDepsSeq

        val filteredDeps = filteredDepsSeq.toList()

        if (filteredDeps.isEmpty() && matcher.filter != null) {
            throw IllegalArgumentException("Dependency filter '${matcher.filter}' matched zero dependencies in scope.")
        }

        if (filteredDeps.size == 1 && matcher.filter != null) {
            return processSingleDependency(filteredDeps.first(), index, forceDownload, providerToIndex)
        }

        val currentHash = storageService.calculateDependencyHash(filteredDeps.asSequence())
        if (storageService.isMergedSourcesCached(dir, currentHash, forceDownload)) {
            if (index && providerToIndex != null) {
                // If we cannot ensure the merge is up to date (e.g. corrupt cache), re-process
                if (indexService.ensureMergeUpToDate(dir, providerToIndex, currentHash)) {
                    return dir
                }
            } else {
                return dir
            }
        }

        processDependencies(filteredDeps.asSequence(), dir, index, forceDownload, providerToIndex)
        return dir
    }

    context(progress: ProgressReporter)
    private suspend fun tryGetValidCachedSources(
        dir: MergedSourcesDir,
        matcher: DependencyFilterMatcher,
        fresh: Boolean,
        forceDownload: Boolean,
        index: Boolean,
        providerToIndex: SearchProvider?
    ): MergedSourcesDir? {
        if (matcher.isVersionLess) return null

        val cached = storageService.tryGetCachedMergedSources(dir, fresh, forceDownload) ?: return null

        if (!index || providerToIndex == null) return cached

        val currentHash = storageService.getDependencyHash(cached) ?: return null

        if (indexService.ensureMergeUpToDate(cached, providerToIndex, currentHash)) {
            return cached
        }

        return null
    }

    context(progress: ProgressReporter)
    private suspend fun withSources(
        projectRoot: GradleProjectRoot,
        path: String,
        kind: String,
        dependency: String?,
        index: Boolean,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider? = null,
        resolve: suspend () -> Sequence<GradleDependency>
    ): SourcesDir {
        val dir = storageService.getMergedSourcesDir(projectRoot, path + (dependency ?: ""), kind)
        val lockFile = dir.lockFile
        val matcher = DependencyFilterMatcher(dependency)

        // 1. Try cache hit first
        tryGetValidCachedSources(dir, matcher, fresh, forceDownload, index, providerToIndex)?.let {
            return it
        }

        // 2. Exclusive lock for update (cache miss or explicit refresh)
        return FileLockManager.withLock(lockFile, shared = false) {
            // Re-check cache under exclusive lock
            tryGetValidCachedSources(dir, matcher, fresh, forceDownload, index, providerToIndex)?.let {
                return@withLock it
            }

            resolveAndProcessSourcesInternal(dir, index, forceDownload, matcher, providerToIndex, resolve)
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessAllSources(projectRoot: GradleProjectRoot, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return withSources(projectRoot, "", "root", dependency, index, forceDownload, fresh, providerToIndex) {
            depService.downloadAllSources(projectRoot, dependency).projects.asSequence().flatMap { it.allDependencies() }
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessProjectSources(projectRoot: GradleProjectRoot, projectPath: String, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return withSources(projectRoot, projectPath, "project", dependency, index, forceDownload, fresh, providerToIndex) {
            depService.downloadProjectSources(projectRoot, projectPath, dependency).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessConfigurationSources(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String?,
        index: Boolean,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider?
    ): SourcesDir {
        return withSources(projectRoot, configurationPath, "configuration", dependency, index, forceDownload, fresh, providerToIndex) {
            depService.downloadConfigurationSources(projectRoot, configurationPath, dependency).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return withSources(projectRoot, sourceSetPath, "sourceSet", dependency, index, forceDownload, fresh, providerToIndex) {
            depService.downloadSourceSetSources(projectRoot, sourceSetPath, dependency).configurations.asSequence().flatMap { it.allDependencies() }
        }
    }

    context(progress: ProgressReporter)
    private suspend fun processSingleDependency(dep: GradleDependency, index: Boolean, forceDownload: Boolean, providerToIndex: SearchProvider?): SingleDependencySourcesDir {
        if (!dep.hasSources) {
            throw IllegalArgumentException("Targeted dependency ${dep.id} does not have sources available.")
        }

        val dirModel = storageService.getSingleDependencySourcesDir(dep)
        val dir = dirModel.sources
        val lockFile = dirModel.lockFile

        val indexInfo = FileLockManager.withLock(lockFile) {
            if (providerToIndex != null) {
                if (!forceDownload && storageService.isExtracted(dir)) {
                    indexService.ensureIndexed(dep, dir, forceDownload, providerToIndex)
                } else {
                    orchestrateExtractionAndIndexing(dep, dir, forceDownload, providerToIndex)
                }
            } else {
                storageService.extractSources(dir, dep, forceDownload)
                null
            }
        }

        val indexDir = if (index && providerToIndex != null) {
            indexInfo?.dir ?: throw IllegalStateException("Indexing failed to return an index directory for ${dep.id}.")
        } else {
            kotlin.io.path.Path("")
        }

        return dirModel.copy(index = indexDir)
    }

    context(progress: ProgressReporter)
    private suspend fun orchestrateExtractionAndIndexing(
        dep: GradleDependency,
        dir: Path,
        forceDownload: Boolean,
        providerToIndex: SearchProvider
    ): Index? = coroutineScope {
        val relativePrefix = dep.relativePrefix?.let { kotlin.io.path.Path(it) } ?: return@coroutineScope null
        val channel = Channel<IndexEntry>(capacity = 20)

        val indexJob = launch(dispatcher) {
            try {
                indexService.indexDependency(dep, channel.consumeAsFlow(), providerToIndex, forceIndex = forceDownload)
            } finally {
                for (entry in channel) {
                }
            }
        }

        try {
            storageService.extractSources(dir, dep, forceDownload) { path, contentBytes ->
                val fullRelativePath = storageService.normalizeRelativePath(relativePrefix, path)
                val ext = fullRelativePath.substringAfterLast('.', "")
                if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                    channel.send(IndexEntry(fullRelativePath, contentBytes.decodeToString()))
                }
            }
        } catch (e: Exception) {
            indexJob.cancel()
            throw e
        } finally {
            channel.close()
        }

        indexJob.join()
        indexService.ensureIndexed(dep, dir, false, providerToIndex)
    }

    context(progress: ProgressReporter)
    private suspend fun processDependencies(deps: Sequence<GradleDependency>, target: SourcesDir, index: Boolean, forceDownload: Boolean, providerToIndex: SearchProvider?) {
        val depsList = deps.toList()
        val validDeps = depsList.filter { it.hasSources }.toSet()

        val indices: List<Pair<Path, Index>> = if (validDeps.isEmpty()) {
            handleNoDependencies(depsList)
            emptyList()
        } else {
            extractAndIndexDependencies(validDeps, target, index, forceDownload, providerToIndex)
        }

        val currentHash = storageService.calculateDependencyHash(depsList.asSequence())
        if (index && providerToIndex != null) {
            indexService.mergeIndices(target as MergedSourcesDir, indices, providerToIndex, currentHash)
        }

        storageService.saveMergedSourcesCache(target as MergedSourcesDir, currentHash, indices.associate { it.first to it.second.dir })
    }

    context(progress: ProgressReporter)
    private fun handleNoDependencies(depsList: List<GradleDependency>) {
        if (depsList.isNotEmpty()) {
            throw IllegalArgumentException("Matched ${depsList.size} dependencies, but none have sources available.")
        }
        logger.info("No dependencies with sources to extract")
        progress.report(1.0, 1.0, "[DETECTING] No dependencies found")
    }

    context(progress: ProgressReporter)
    private suspend fun extractAndIndexDependencies(
        validDeps: Set<GradleDependency>,
        target: SourcesDir,
        index: Boolean,
        forceDownload: Boolean,
        providerToIndex: SearchProvider?
    ): List<Pair<Path, Index>> {
        progress.report(0.0, 1.0, "[DETECTING] Finding dependencies with sources")
        logger.info("Extracting sources for ${validDeps.size} dependencies")

        storageService.prepareTargetSources(target)
        progress.report(1.0, 1.0, "[DETECTING] Found ${validDeps.size} dependencies")

        val processingProgress = progress.withPhase("PROCESSING")

        // Group dependencies by extraction directory to avoid redundant extractions
        val depsByDir = validDeps.groupBy { dep ->
            val relativePrefix = requireNotNull(dep.relativePrefix)
            storageService.globalSourcesDir.resolve(relativePrefix)
        }

        val tracker = ParallelProgressTracker(processingProgress, validDeps.size.toDouble())
        val targetSources = target.sources

        return depsByDir.entries.unorderedParallelMap(context = dispatcher) { entry ->
            val (dir, depsForDir) = entry
            processSingleDependencyTask(
                dir = dir,
                depsForDir = depsForDir,
                targetSources = targetSources,
                index = index,
                tracker = tracker,
                forceDownload = forceDownload,
                providerToIndex = providerToIndex
            )
        }.flatten()
    }

    private suspend fun processSingleDependencyTask(
        dir: Path,
        depsForDir: List<GradleDependency>,
        targetSources: Path,
        index: Boolean,
        tracker: ParallelProgressTracker,
        forceDownload: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): List<Pair<Path, Index>> {
        val dep = depsForDir.first()
        // Use a combined ID for the tracker to represent the directory-level task
        val taskId = if (depsForDir.size > 1) "${dep.id} (+${depsForDir.size - 1} more)" else dep.id

        val subProgress = ProgressReporter { p, t, m ->
            tracker.reportProgress(taskId, p, t, m)
        }

        try {
            val lockFile = storageService.getGlobalLockFile(dir)
            return FileLockManager.withLock(lockFile) {
                tracker.onStart(taskId)
                val indexInfo = with(subProgress) {
                    if (providerToIndex != null) {
                        if (!forceDownload && storageService.isExtracted(dir)) {
                            indexService.ensureIndexed(dep, dir, forceDownload, providerToIndex)
                        } else {
                            orchestrateExtractionAndIndexing(dep, dir, forceDownload, providerToIndex)
                        }
                    } else {
                        storageService.extractSources(dir, dep, forceDownload)
                        null
                    }
                }

                val relativePath = requireNotNull(dep.relativePrefix)
                val scopeLink = targetSources.resolve(relativePath)

                storageService.syncTo(scopeLink, dir, dep, targetSources)

                if (index) {
                    indexInfo?.let { index ->
                        listOf(kotlin.io.path.Path(relativePath) to index)
                    } ?: emptyList()
                } else emptyList()
            }
        } finally {
            tracker.onComplete(taskId, count = depsForDir.size)
        }
    }
}
