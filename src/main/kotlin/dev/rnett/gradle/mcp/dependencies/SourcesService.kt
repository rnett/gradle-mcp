package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SingleDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.DeclarationSearch
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.dependencies.search.GlobSearch
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
import kotlin.io.path.readText

interface SourcesService {

    /**
     * Downloads and extracts sources for all dependencies in the project.
     *
     * @param projectRoot The root of the Gradle project.
     * @param dependency Optional filter for a specific dependency.
     * @param index If true, ensures that the sources are indexed after extraction.
     * @param forceDownload If true, bypasses caches and forces a fresh download/extraction.
     * @param fresh If true, requests a fresh dependency list from Gradle.
     * @param providerToIndex Optional: Specific search provider to index. If indexing is enabled, this specifies
     * which provider's index should be prioritized or created.
     * @return A [SourcesDir] pointing to the local source and index directories.
     */
    context(progress: ProgressReporter)
    suspend fun downloadAllSources(projectRoot: GradleProjectRoot, dependency: String? = null, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false, providerToIndex: SearchProvider? = null): SourcesDir

    /**
     * Downloads and extracts sources for all dependencies in a specific project.
     *
     * @param projectRoot The root of the Gradle project.
     * @param projectPath The Gradle path of the project (e.g., ':app').
     * @param dependency Optional filter for a specific dependency.
     * @param index If true, ensures that the sources are indexed after extraction.
     * @param forceDownload If true, bypasses caches and forces a fresh download/extraction.
     * @param fresh If true, requests a fresh dependency list from Gradle.
     * @param providerToIndex Optional: Specific search provider to index.
     * @return A [SourcesDir] pointing to the local source and index directories.
     */
    context(progress: ProgressReporter)
    suspend fun downloadProjectSources(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        dependency: String? = null,
        index: Boolean = true,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): SourcesDir

    /**
     * Downloads and extracts sources for all dependencies in a specific configuration.
     *
     * @param projectRoot The root of the Gradle project.
     * @param configurationPath The Gradle path of the configuration (e.g., ':app:implementation').
     * @param dependency Optional filter for a specific dependency.
     * @param index If true, ensures that the sources are indexed after extraction.
     * @param forceDownload If true, bypasses caches and forces a fresh download/extraction.
     * @param fresh If true, requests a fresh dependency list from Gradle.
     * @param providerToIndex Optional: Specific search provider to index.
     * @return A [SourcesDir] pointing to the local source and index directories.
     */
    context(progress: ProgressReporter)
    suspend fun downloadConfigurationSources(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String? = null,
        index: Boolean = true,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): SourcesDir

    /**
     * Downloads and extracts sources for all dependencies in a specific source set.
     *
     * @param projectRoot The root of the Gradle project.
     * @param sourceSetPath The Gradle path of the source set (e.g., ':app:main').
     * @param dependency Optional filter for a specific dependency.
     * @param index If true, ensures that the sources are indexed after extraction.
     * @param forceDownload If true, bypasses caches and forces a fresh download/extraction.
     * @param fresh If true, requests a fresh dependency list from Gradle.
     * @param providerToIndex Optional: Specific search provider to index.
     * @return A [SourcesDir] pointing to the local source and index directories.
     */
    context(progress: ProgressReporter)
    suspend fun downloadSourceSetSources(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String? = null,
        index: Boolean = true,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): SourcesDir

    /**
     * Performs a search across the extracted sources using the specified provider.
     *
     * @param sources The sources directory to search.
     * @param provider The search provider to use (e.g., [DeclarationSearch], [FullTextSearch], [GlobSearch]).
     * @param query The search query.
     * @param pagination Optional pagination parameters.
     * @return A [SearchResponse] containing the search results and optional interpreted query/error.
     */
    suspend fun search(sources: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS): SearchResponse<SearchResult>

    /**
     * Lists the contents of a specific package in the extracted sources.
     *
     * @param sources The sources directory to list from.
     * @param packageName The fully qualified package name.
     * @return [PackageContents] if found, null otherwise.
     */
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
        providerToIndex: SearchProvider? = null,
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
            return processSingleDependency(filteredDeps.first(), index, forceDownload, providerToIndex)
        }

        val currentHash = cacheManager.dependencyHash(filteredDeps.asSequence())
        if (cacheManager.checkCached(dir, currentHash, forceDownload)) {
            if (index && providerToIndex != null) {
                val upToDate = indexService.isMergeUpToDate(dir.index, providerToIndex, currentHash)

                if (!upToDate) {
                    val cachedIndices = cacheManager.loadCachedIndices(dir)
                    if (cachedIndices != null) {
                        indexService.mergeIndices(dir, cachedIndices.mapValues { Index(it.value) }, providerToIndex, currentHash)
                    } else {
                        processDependencies(filteredDeps.asSequence(), dir, index, forceDownload, providerToIndex)
                    }
                }
            }
            return dir
        }

        processDependencies(filteredDeps.asSequence(), dir, index, forceDownload, providerToIndex)
        return dir
    }

    /**
     * Orchestrates the extraction, indexing, and merging of sources for a set of dependencies.
     *
     * This method manages the high-level project lock. It uses a two-stage approach:
     * 1. Acquire a shared lock to check for a valid project-level cache hit.
     * 2. If a cache miss occurs, release the shared lock and acquire an exclusive lock to
     *    perform extraction and indexing.
     *
     * Individual dependency extraction and indexing are further protected by granular locks
     * inside the resolution chain.
     */
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
        val dir = cacheManager.sourcesDirectory(projectRoot, path + (dependency ?: ""), kind)
        val lockFile = dir.lockFile
        val matcher = DependencyFilterMatcher(dependency)

        // 1. Try shared lock first (cache hit)
        val cached = if (!matcher.isVersionLess) {
            FileLockManager.withLock(lockFile, shared = true) {
                cacheManager.tryGetCachedMergedSources(dir, fresh, forceDownload)
            }
        } else null

        if (cached != null) {
            if (index && providerToIndex != null) {
                val expectedHash = try {
                    dir.storagePath.resolve(".dependencies.hash").readText()
                } catch (e: Exception) {
                    ""
                }
                val upToDate = indexService.isMergeUpToDate(dir.index, providerToIndex, expectedHash)

                if (!upToDate) {
                    // Cache hit but index needs merging.
                    val cachedIndices = FileLockManager.withLock(lockFile, shared = true) {
                        cacheManager.loadCachedIndices(dir)
                    }
                    if (cachedIndices != null) {
                        FileLockManager.withLock(lockFile, shared = false) {
                            indexService.mergeIndices(dir, cachedIndices.mapValues { Index(it.value) }, providerToIndex, expectedHash)
                        }
                        return cached
                    }
                } else {
                    return cached
                }
            } else {
                return cached
            }
        }

        // 2. Exclusive lock for update (cache miss or explicit refresh)
        return FileLockManager.withLock(lockFile, shared = false) {
            // Re-check cache under exclusive lock
            if (!matcher.isVersionLess) {
                cacheManager.tryGetCachedMergedSources(dir, fresh, forceDownload)?.let { mergedDir ->
                    if (index && providerToIndex != null) {
                        val expectedHash = try {
                            mergedDir.storagePath.resolve(".dependencies.hash").readText()
                        } catch (e: Exception) {
                            ""
                        }
                        val upToDate = indexService.isMergeUpToDate(mergedDir.index, providerToIndex, expectedHash)

                        if (upToDate) {
                            return@withLock mergedDir
                        }

                        val cachedIndices = cacheManager.loadCachedIndices(mergedDir)
                        if (cachedIndices != null) {
                            indexService.mergeIndices(mergedDir, cachedIndices.mapValues { Index(it.value) }, providerToIndex, expectedHash)
                            return@withLock mergedDir
                        }
                    } else {
                        return@withLock mergedDir
                    }
                }
            }

            resolveAndProcessSources(dir, index, forceDownload, matcher, providerToIndex, resolve)
        }
    }

    context(progress: ProgressReporter)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return withSources(projectRoot, "", "root", dependency, index, forceDownload, fresh, providerToIndex) {
            depService.downloadAllSources(projectRoot, dependency).projects.asSequence().flatMap { it.allDependencies() }
        }
    }

    context(progress: ProgressReporter)
    override suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return withSources(projectRoot, projectPath, "project", dependency, index, forceDownload, fresh, providerToIndex) {
            depService.downloadProjectSources(projectRoot, projectPath, dependency).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return withSources(projectRoot, configurationPath, "configuration", dependency, index, forceDownload, fresh, providerToIndex) {
            depService.downloadConfigurationSources(projectRoot, configurationPath, dependency).allDependencies()
        }
    }

    context(progress: ProgressReporter)
    override suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, dependency: String?, index: Boolean, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        return withSources(projectRoot, sourceSetPath, "sourceSet", dependency, index, forceDownload, fresh, providerToIndex) {
            depService.downloadSourceSetSources(projectRoot, sourceSetPath, dependency).configurations.asSequence().flatMap { it.allDependencies() }
        }
    }

    context(progress: ProgressReporter)
    private suspend fun processSingleDependency(dep: GradleDependency, index: Boolean, forceDownload: Boolean, providerToIndex: SearchProvider?): SingleDependencySourcesDir {
        if (!dep.hasSources) {
            throw IllegalArgumentException("Targeted dependency ${dep.id} does not have sources available.")
        }

        val dirModel = cacheManager.singleDependencyDirectory(dep)
        val dir = dirModel.sources
        val lockFile = dirModel.lockFile

        FileLockManager.withLock(lockFile) {
            with(progress) {
                processor.extractAndIndex(dir, dep, index, progress, forceDownload, providerToIndex)
            }
        }

        val indexDir = if (index) {
            val indexInfo = FileLockManager.withLock(lockFile, shared = true) {
                with(progress) {
                    processor.ensureIndexed(dep, dir, forceDownload, providerToIndex)
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
    private suspend fun processDependencies(deps: Sequence<GradleDependency>, target: SourcesDir, index: Boolean, forceDownload: Boolean, providerToIndex: SearchProvider?) {
        val depsList = deps.toList()
        val validDeps = depsList.filter { it.hasSources }.toSet()

        val indices: List<Pair<Path, Index>> = if (validDeps.isEmpty()) {
            handleNoDependencies(depsList)
            emptyList()
        } else {
            extractAndIndexDependencies(validDeps, target, index, forceDownload, providerToIndex)
        }

        val currentHash = cacheManager.dependencyHash(depsList.asSequence())
        if (index && providerToIndex != null) {
            indexService.mergeIndices(target as MergedSourcesDir, indices.toMap(), providerToIndex, currentHash)
        }

        cacheManager.saveCache(target as MergedSourcesDir, currentHash, indices.associate { it.first to it.second.dir })
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
                    processor.extractAndIndex(dir, dep, index, subProgress, forceDownload, providerToIndex)
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
                        processor.ensureIndexed(dep, dir, forceDownload, providerToIndex)?.let { index ->
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
