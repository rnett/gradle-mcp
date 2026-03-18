package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SingleDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.dependencies.search.toSearchResults
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.unorderedParallelMap
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

interface SourcesService {

    /**
     * Download sources for all dependencies in the project.
     */
    context(progress: ProgressReporter)
    suspend fun downloadAllSources(projectRoot: GradleProjectRoot, dependency: String? = null, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific project.
     */
    context(progress: ProgressReporter)
    suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, dependency: String? = null, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific configuration.
     */
    context(progress: ProgressReporter)
    suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, dependency: String? = null, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific source set.
     */
    context(progress: ProgressReporter)
    suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, dependency: String? = null, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    suspend fun search(sources: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS): SearchResponse<SearchResult>

    suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents?
}

@OptIn(ExperimentalPathApi::class)
class DefaultSourcesService(private val depService: GradleDependencyService, private val environment: GradleMcpEnvironment, private val indexService: IndexService) : SourcesService {
    private val logger = LoggerFactory.getLogger(DefaultSourcesService::class.java)

    private val cacheManager = SourcesCacheManager(environment)
    private val processor = SourcesProcessor(indexService)

    context(progress: ProgressReporter)
    private suspend fun resolveAndProcessSources(
        dir: MergedSourcesDir,
        index: Boolean,
        forceDownload: Boolean,
        matcher: DependencyFilterMatcher,
        resolve: suspend () -> Sequence<GradleDependency>
    ): SourcesDir {
        val allDeps = resolve().toList()
        val filteredDeps = if (matcher.filter != null) {
            val filtered = allDeps.filter { matcher.matches(it) }
            if (filtered.isEmpty() && allDeps.isNotEmpty()) {
                throw IllegalArgumentException("Dependency filter '${matcher.filter}' matched zero dependencies out of ${allDeps.size} in scope.")
            }
            filtered
        } else allDeps

        if (filteredDeps.size == 1 && matcher.filter != null) {
            return processSingleDependency(filteredDeps.first(), index, forceDownload)
        }

        val currentHash = cacheManager.dependencyHash(filteredDeps.asSequence())
        if (cacheManager.checkCached(dir, currentHash, forceDownload)) return dir

        processDependencies(filteredDeps.asSequence(), dir, index, forceDownload)
        cacheManager.saveCache(dir, currentHash)
        return dir
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
        resolve: suspend () -> Sequence<GradleDependency>
    ): SourcesDir {
        val dir = cacheManager.sourcesDirectory(projectRoot, path + (dependency ?: ""), kind)
        val lockFile = dir.lockFile
        val matcher = DependencyFilterMatcher(dependency)

        // 1. Try shared lock first (cache hit)
        val cached = if (!matcher.isVersionLess) {
            FileLockManager.withLock(lockFile, shared = true) {
                cacheManager.tryGetCachedMergedSources(dir, fresh, forceDownload)
            }
        } else null

        if (cached != null) return cached

        // 2. Exclusive lock for update (cache miss or explicit refresh)
        return FileLockManager.withLock(lockFile, shared = false) {
            // Re-check cache under exclusive lock
            if (!matcher.isVersionLess) {
                cacheManager.tryGetCachedMergedSources(dir, fresh, forceDownload)?.let {
                    if (it is MergedSourcesDir) return@withLock it
                }
            }

            resolveAndProcessSources(dir, index, forceDownload, matcher, resolve)
        }
    }

    context(progress: ProgressReporter)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        return withSources(projectRoot, "", "root", dependency, index, forceDownload, fresh) {
            depService.downloadAllSources(projectRoot, dependency).projects.asSequence().flatMap { it.allDependencies() }
        }
    }

    context(progress: ProgressReporter)
    override suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        return withSources(projectRoot, projectPath, "project", dependency, index, forceDownload, fresh) {
            depService.downloadProjectSources(projectRoot, projectPath, dependency).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        return withSources(projectRoot, configurationPath, "configuration", dependency, index, forceDownload, fresh) {
            depService.downloadConfigurationSources(projectRoot, configurationPath, dependency).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        return withSources(projectRoot, sourceSetPath, "sourceSet", dependency, index, forceDownload, fresh) {
            depService.downloadSourceSetSources(projectRoot, sourceSetPath, dependency).configurations.asSequence().flatMap { it.allDependencies() }
        }
    }

    context(progress: ProgressReporter)
    private suspend fun processSingleDependency(dep: GradleDependency, index: Boolean, forceDownload: Boolean): SingleDependencySourcesDir {
        if (!dep.hasSources) {
            throw IllegalArgumentException("Targeted dependency ${dep.id} does not have sources available.")
        }

        val dirModel = cacheManager.singleDependencyDirectory(dep)
        val dir = dirModel.sources
        val lockFile = dirModel.lockFile

        FileLockManager.withLock(lockFile) {
            with(progress) {
                processor.extractAndIndex(dir, dep, index, progress, forceDownload)
            }
        }

        val indexDir = if (index) {
            val indexInfo = FileLockManager.withLock(lockFile, shared = true) {
                with(progress) {
                    processor.ensureIndexed(dep, dir, forceDownload)
                }
            }
            indexInfo?.dir ?: throw IllegalStateException("Indexing failed to return an index directory for ${dep.id}.")
        } else {
            java.nio.file.Path.of("")
        }

        return dirModel.copy(index = indexDir)
    }

    override suspend fun search(
        sources: SourcesDir,
        provider: SearchProvider,
        query: String,
        pagination: PaginationInput
    ): SearchResponse<SearchResult> {
        val lockFile = sources.lockFile
        return FileLockManager.withLock(lockFile, shared = true) {
            val response = indexService.search(sources, provider, query, pagination)
            SearchResponse(
                results = response.results.toSearchResults(sources.rootForSearch),
                interpretedQuery = response.interpretedQuery,
                error = response.error
            )
        }
    }

    override suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents? {
        val lockFile = sources.lockFile
        return FileLockManager.withLock(lockFile, shared = true) {
            indexService.listPackageContents(sources, packageName)
        }
    }

    context(progress: ProgressReporter)
    private suspend fun processDependencies(deps: Sequence<GradleDependency>, target: SourcesDir, index: Boolean, forceDownload: Boolean) {
        val depsList = deps.toList()
        val validDeps = depsList.filter { it.hasSources }.toSet()

        val indices: List<Pair<Path, Index>> = if (validDeps.isEmpty()) {
            handleNoDependencies(depsList)
            emptyList()
        } else {
            extractAndIndexDependencies(validDeps, target, index, forceDownload)
        }

        if (index) {
            indexService.mergeIndices(target as MergedSourcesDir, indices.toMap())
        }
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
        forceDownload: Boolean
    ): List<Pair<Path, Index>> {
        progress.report(0.0, 1.0, "[DETECTING] Finding dependencies with sources")
        logger.info("Extracting sources for ${validDeps.size} dependencies")

        val targetSources = target.sources
        if (targetSources.exists()) {
            targetSources.deleteRecursively()
        }
        targetSources.createDirectories()
        progress.report(1.0, 1.0, "[DETECTING] Found ${validDeps.size} dependencies")

        val processingProgress = progress.withPhase("PROCESSING")

        // Group dependencies by extraction directory to avoid redundant extractions
        val depsByDir = validDeps.groupBy { dep ->
            cacheManager.globalSourcesDir.resolve(java.nio.file.Path.of(requireNotNull(dep.group)).resolve(requireNotNull(dep.sourcesFile).nameWithoutExtension))
        }

        val tracker = ParallelProgressTracker(processingProgress, depsByDir.size.toDouble())

        return depsByDir.entries.unorderedParallelMap(context = Dispatchers.IO) { entry ->
            val (dir, depsForDir) = entry
            processSingleDependencyTask(
                dir = dir,
                depsForDir = depsForDir,
                targetSources = targetSources,
                index = index,
                tracker = tracker,
                forceDownload = forceDownload
            )
        }.flatten()
    }

    private suspend fun processSingleDependencyTask(
        dir: Path,
        depsForDir: List<GradleDependency>,
        targetSources: Path,
        index: Boolean,
        tracker: ParallelProgressTracker,
        forceDownload: Boolean = false
    ): List<Pair<Path, Index>> {
        val dep = depsForDir.first()
        val depId = dep.id
        tracker.onStart(depId)

        val subProgress = ProgressReporter { _, _, m ->
            if (m != null) {
                tracker.reportMessage(m)
            }
        }

        try {
            val lockFile = cacheManager.globalLockFile(dir)
            FileLockManager.withLock(lockFile) {
                with(subProgress) {
                    processor.extractAndIndex(dir, dep, index, subProgress, forceDownload)
                }
            }

            // Use shared lock for read sync operations
            return FileLockManager.withLock(lockFile, shared = true) {
                val group = requireNotNull(dep.group)
                val sourcesFile = requireNotNull(dep.sourcesFile)
                val relativePath = java.nio.file.Path.of(group).resolve(sourcesFile.nameWithoutExtension)
                val scopeLink = targetSources.resolve(relativePath)

                processor.syncTo(scopeLink, dir, dep, targetSources)

                if (index) {
                    with(subProgress) {
                        processor.ensureIndexed(dep, dir, forceDownload)?.let { index ->
                            listOf(relativePath to index)
                        } ?: emptyList()
                    }
                } else emptyList()
            }
        } finally {
            tracker.onComplete(depId)
        }
    }
}
