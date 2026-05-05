@file:OptIn(ExperimentalUuidApi::class)
package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencies
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.markerFileName
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.FileUtils
import dev.rnett.gradle.mcp.utils.FileUtils.deleteRecursivelyWithRetry
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
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Thrown when `calculatePlatformSpecificSets` cannot find the common sibling's source-sets.txt.
 * Distinct from other [IllegalStateException]s so callers can narrow their catch and let other
 * ISEs (e.g., common-sibling failure in `ensureBaseReady`) propagate normally.
 */
private class MissingSourceSetsFileException(message: String) : IllegalStateException(message)

/**
 * High-level service for managing and searching dependency sources.
 * Acts as the primary entry point for tool handlers.
 */
interface SourcesService {

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
    private val jdkSourceService: JdkSourceService,
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

    private data class SourceResolutionInput(
        val dependencies: Sequence<GradleDependency>,
        val jdkHome: String?
    )

    private data class ParsedScopedPath(
        val projectPath: String,
        val name: String
    )

    private val cache = ConcurrentHashMap<CacheKey, CachedView>()
    private val keyLocks = ConcurrentHashMap<CacheKey, Mutex>()

    private sealed interface SourceScope {
        data class Project(val projectRoot: GradleProjectRoot, val projectPath: String) : SourceScope
        data class Configuration(val projectRoot: GradleProjectRoot, val configurationPath: String) : SourceScope
        data class SourceSet(val projectRoot: GradleProjectRoot, val sourceSetPath: String) : SourceScope
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessProjectSources(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        dependency: String?,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider?
    ): SourcesDir {
        val gradleDependencyFilter = dependency.takeUnless { it == "jdk" }
        return resolveAndProcessSourcesInternal(SourceScope.Project(projectRoot, projectPath), forceDownload, fresh, DependencyFilterMatcher(dependency), providerToIndex) {
            val project = depService.downloadProjectSources(projectRoot, projectPath, gradleDependencyFilter, fresh = fresh || forceDownload, includeInternal = true)
            SourceResolutionInput(project.allDependencies(), project.jdkHome.takeIf { project.hasJvmSourceSet() })
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
        val gradleDependencyFilter = dependency.takeUnless { it == "jdk" }
        return resolveAndProcessSourcesInternal(SourceScope.Configuration(projectRoot, configurationPath), forceDownload, fresh, DependencyFilterMatcher(dependency), providerToIndex) {
            val parsed = parseScopedPath(configurationPath, "configurationPath", buildscriptNamespace = true)
            val project = resolveScopedProjectDependencies(
                projectRoot = projectRoot,
                projectPath = parsed.projectPath,
                dependency = gradleDependencyFilter,
                fresh = fresh || forceDownload,
                configuration = parsed.name
            )
            val configuration = project.configurationOrNull(parsed.name)
                ?: throw IllegalArgumentException("Configuration not found in project ${parsed.projectPath}: ${parsed.name}")
            val jdkHome = project.jdkHome.takeIf { project.configurationIsJvm(parsed.name) }
            SourceResolutionInput(configuration.allDependencies(), jdkHome)
        }
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessSourceSetSources(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String?,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider?
    ): SourcesDir {
        val gradleDependencyFilter = dependency.takeUnless { it == "jdk" }
        return resolveAndProcessSourcesInternal(SourceScope.SourceSet(projectRoot, sourceSetPath), forceDownload, fresh, DependencyFilterMatcher(dependency), providerToIndex) {
            val parsed = parseScopedPath(sourceSetPath, "sourceSetPath")
            val sourceSetName = if (parsed.name == "buildscript") DefaultGradleDependencyService.BUILDSCRIPT_SOURCE_SET else parsed.name
            val project = resolveScopedProjectDependencies(
                projectRoot = projectRoot,
                projectPath = parsed.projectPath,
                dependency = gradleDependencyFilter,
                fresh = fresh || forceDownload,
                sourceSet = sourceSetName
            )
            val sourceSet = project.sourceSetOrNull(parsed.name)
                ?: throw IllegalArgumentException("Source set not found in project ${parsed.projectPath}: ${parsed.name}. Available: ${project.sourceSets.map { it.name }}")
            val configurations = project.configurations.filter { it.name in sourceSet.configurations }
            val jdkHome = project.jdkHome.takeIf { sourceSet.isJvm }
            SourceResolutionInput(configurations.asSequence().flatMap { it.allDependencies() }, jdkHome)
        }
    }

    private fun parseScopedPath(path: String, parameterName: String, buildscriptNamespace: Boolean = false): ParsedScopedPath {
        val lastColon = path.lastIndexOf(':')
        require(lastColon != -1) { "$parameterName must be an absolute Gradle path (e.g. :project:foo:main)" }
        val parsedProject = DefaultGradleDependencyService.normalizeProjectPath(if (lastColon == 0) ":" else path.substring(0, lastColon))
        val parsedName = path.substring(lastColon + 1)
        val (projectPath, name) = if (buildscriptNamespace && parsedProject.endsWith(":buildscript")) {
            val p = if (parsedProject == ":buildscript") ":" else parsedProject.substring(0, parsedProject.length - ":buildscript".length)
            p to "buildscript:$parsedName"
        } else {
            parsedProject to parsedName
        }
        return ParsedScopedPath(projectPath, name)
    }

    context(progress: ProgressReporter)
    private suspend fun resolveScopedProjectDependencies(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        dependency: String?,
        fresh: Boolean,
        configuration: String? = null,
        sourceSet: String? = null
    ): GradleProjectDependencies {
        val report = depService.getDependencies(
            projectRoot,
            projectPath = projectPath,
            options = DependencyRequestOptions(
                configuration = configuration,
                sourceSet = sourceSet,
                dependency = dependency,
                downloadSources = true,
                excludeBuildscript = false,
                fresh = fresh,
                includeInternal = true
            )
        )
        return report.projects.find { it.path == projectPath }
            ?: throw IllegalArgumentException("Project not found in report: $projectPath")
    }

    private fun GradleProjectDependencies.hasJvmSourceSet(): Boolean =
        sourceSets.any { it.isJvm && it.name != "buildscript" }

    private fun GradleProjectDependencies.configurationIsJvm(name: String): Boolean =
        name.startsWith("buildscript:") || sourceSets.any { it.isJvm && name in it.configurations }

    private fun GradleProjectDependencies.sourceSetOrNull(name: String): GradleSourceSetDependencies? =
        sourceSets.find { it.name == name }

    private fun GradleProjectDependencies.configurationOrNull(name: String): GradleConfigurationDependencies? =
        configurations.find { it.name == name }

    context(progress: ProgressReporter)
    private suspend fun resolveJdkSourcesDependency(
        jdkHome: String?,
        forceDownload: Boolean,
        fresh: Boolean
    ): Pair<GradleDependency, CASDependencySourcesDir>? {
        val home = jdkHome ?: return null
        val casDir = jdkSourceService.resolveSources(home, forceDownload = forceDownload, fresh = fresh, providerToIndex = null)
            ?: return null
        return GradleDependency.jdkSources(casDir.hash, casDir.sources) to casDir
    }

    context(progress: ProgressReporter)
    private suspend fun resolveAndProcessSourcesInternal(
        scope: SourceScope,
        forceDownload: Boolean,
        fresh: Boolean,
        matcher: DependencyFilterMatcher,
        providerToIndex: SearchProvider? = null,
        resolve: suspend () -> SourceResolutionInput
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

            val resolvedInput = resolve()
            val filteredDepsSeq = if (matcher.dependencyFilter != null) {
                resolvedInput.dependencies.filter { matcher.matchesDependency(it) }
            } else {
                resolvedInput.dependencies
            }

            val filteredDeps = filteredDepsSeq.filter { it.hasSources }
                // Deduplicate by relativePrefix: same group+name → keep only one entry.
                // Prevents multiple Gradle variants of the same artifact from creating separate
                // manifest entries that would all point to the same CAS index, causing duplicate results.
                // Safe because version resolution is unique per project.
                .distinctBy { it.relativePrefix }
                .toList()

            if (filteredDeps.isEmpty() && matcher.dependencyFilter != null && !matcher.isJdkSelector) {
                throw IllegalArgumentException("Dependency filter '${matcher.dependencyFilter}' matched zero dependencies in scope with sources.")
            }

            val includeJdk = matcher.dependencyFilter == null || matcher.isJdkSelector
            val jdkEntry = if (includeJdk && filteredDeps.none { it.relativePrefix == GradleDependency.JDK_SOURCES_PREFIX }) {
                resolveJdkSourcesDependency(resolvedInput.jdkHome, forceDownload, fresh)
            } else {
                if (filteredDeps.any { it.relativePrefix == GradleDependency.JDK_SOURCES_PREFIX }) {
                    logger.warn("Skipping auto-included JDK sources because a dependency already uses the reserved path ${GradleDependency.JDK_SOURCES_PREFIX}.")
                }
                null
            }

            if (filteredDeps.isEmpty() && jdkEntry == null) {
                if (matcher.dependencyFilter != null) {
                    throw IllegalArgumentException("Dependency filter '${matcher.dependencyFilter}' matched zero dependencies in scope with sources.")
                } else {
                    // Return a stable sources root for empty scopes so callers can still inspect the manifest.
                    val emptyResult = storageService.createSessionView(emptyMap(), force = forceDownload)
                    cache[key] = CachedView(emptyResult, emptyMap())
                    return@withLock emptyResult
                }
            }

            val depToCasDir = filteredDeps.associateWith { dep ->
                val hash = storageService.calculateHash(dep)
                storageService.getCASDependencySourcesDir(hash)
            } + listOfNotNull(jdkEntry).toMap()

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

        val depToCasDirById = depToCasDir.entries.associate { it.key.id to it.value }

        // Process common siblings before platform artifacts. Sorting alone is insufficient because
        // unorderedParallelMap starts the whole list concurrently; a platform artifact can otherwise
        // observe the common sibling's unlocked, not-yet-started CAS directory as a failure.
        val (baseEntries, platformEntries) = depToCasDir.entries.partition { it.key.commonComponentId == null }

        suspend fun processEntries(entries: List<Map.Entry<GradleDependency, CASDependencySourcesDir>>) {
            entries.unorderedParallelMap(context = dispatcher) { (dep, casDir) ->
                val taskId = dep.id
                val subProgress = ProgressReporter { p, t, m ->
                    tracker.reportProgress(taskId, p, t, m)
                }

                val commonId = dep.commonComponentId
                val commonCasDir = commonId?.let { depToCasDirById[it] }

                try {
                    tracker.onStart(taskId)
                    with(subProgress) {
                        processCasDependency(dep, casDir, forceDownload, providerToIndex, commonCasDir)
                    }
                } finally {
                    tracker.onComplete(taskId, count = 1)
                }
            }
        }

        processEntries(baseEntries)
        processEntries(platformEntries)
    }

    context(progress: ProgressReporter)
    private suspend fun processCasDependency(
        dep: GradleDependency,
        casDir: CASDependencySourcesDir,
        forceDownload: Boolean,
        providerToIndex: SearchProvider?,
        commonCasDir: CASDependencySourcesDir? = null
    ) {
        logger.debug("Processing CAS dependency: ${dep.id}")
        if (dep.id.startsWith("JDK:")) {
            processJdkCasDependency(dep, casDir, forceDownload, providerToIndex)
            return
        }
        // 1. Ensure Base is ready (sources extracted & normalized)
        // Catch IllegalStateException from calculatePlatformSpecificSets (missing sourceSetsFile) so a
        // single broken common sibling doesn't fail the entire unorderedParallelMap batch. The dep
        // remains usable — it will fall back to normalized/ without a normalized-target/.
        val baseReady = try {
            ensureBaseReady(dep, casDir, forceDownload, commonCasDir)
            true
        } catch (e: MissingSourceSetsFileException) {
            logger.warn("Skipping normalized-target generation for ${dep.id}: ${e.message}. Dep will fall back to normalized/ in the session view.")
            casDir.baseCompletedMarker.exists()
        }

        // 2. Index if requested
        // Skip indexing entirely when base processing did not complete — the normalizedDir
        // may not exist, and writing an empty index marker would permanently poison the entry.
        if (!baseReady) return

        if (providerToIndex != null) {
            // Hold shared base lock during indexing to prevent a concurrent repair from deleting normalized/
            FileLockManager.withLock(casDir.baseLockFile, shared = true) {
                FileLockManager.withLock(casDir.indexLockFile(providerToIndex.name)) {
                    if (!forceDownload && casDir.baseCompletedMarker.exists() && casDir.index.resolve(providerToIndex.markerFileName).exists()) {
                        progress.report(1.0, 1.0, "Already indexed ${dep.id} with ${providerToIndex.name}")
                        return@withLock
                    }

                    // We need a tempDir for indexing (to move files atomically)
                    val tempDir = casDir.baseDir.resolveSibling("${casDir.hash}.index-${providerToIndex.name}.${Uuid.random()}.tmp")
                    tempDir.createDirectories()
                    try {
                        // Always index the full normalized directory to ensure identical indexes across all contexts
                        indexStep(dep, tempDir, casDir, casDir.normalizedDir, providerToIndex)
                    } finally {
                        if (tempDir.exists()) tempDir.deleteRecursivelyWithRetry()
                    }
                }
            }
        }
    }

    context(progress: ProgressReporter)
    private suspend fun processJdkCasDependency(
        dep: GradleDependency,
        casDir: CASDependencySourcesDir,
        forceDownload: Boolean,
        providerToIndex: SearchProvider?
    ) {
        if (!casDir.baseCompletedMarker.exists()) {
            logger.warn("Skipping JDK sources in session view: base processing is incomplete for ${casDir.hash}.")
            return
        }

        if (providerToIndex == null) return

        FileLockManager.withLock(casDir.baseLockFile, shared = true) {
            FileLockManager.withLock(casDir.indexLockFile(providerToIndex.name)) {
                if (!forceDownload && casDir.index.resolve(providerToIndex.markerFileName).exists()) {
                    progress.report(1.0, 1.0, "Already indexed JDK sources with ${providerToIndex.name}")
                    return@withLock
                }

                val tempDir = casDir.baseDir.resolveSibling("${casDir.hash}.index-${providerToIndex.name}.${Uuid.random()}.tmp")
                tempDir.createDirectories()
                try {
                    indexStep(dep, tempDir, casDir, casDir.sources, providerToIndex)
                } finally {
                    if (tempDir.exists()) tempDir.deleteRecursivelyWithRetry()
                }
            }
        }
    }

    private suspend fun generateNormalizedTarget(
        dep: GradleDependency,
        casDir: CASDependencySourcesDir,
        commonCasDir: CASDependencySourcesDir,
        progress: ProgressReporter
    ) {
        val processedWithCommon = casDir.processedWithCommonMarker
        if (processedWithCommon.exists()) return

        val sourceSets = detectSourceSets(dep, casDir.sources)
        val platformSpecificSets = calculatePlatformSpecificSets(dep, sourceSets, commonCasDir)
        if (platformSpecificSets.isNotEmpty()) {
            val targetAlreadyValid = casDir.normalizedTargetDir.exists() &&
                    try {
                        Files.list(casDir.normalizedTargetDir).use { it.findFirst().isPresent }
                    } catch (_: java.nio.file.NoSuchFileException) {
                        false
                    }
            if (!targetAlreadyValid) {
                progress.report(0.8, 1.0, "Generating normalized-target for ${dep.id}")
                val tempTargetDir = casDir.baseDir.resolveSibling("${casDir.hash}.lazy-target.${Uuid.random()}.tmp")
                try {
                    detectAndNormalize(
                        sourcesDir = casDir.sources,
                        outputDir = null,
                        sourceSets = sourceSets,
                        platformSpecificSets = platformSpecificSets,
                        targetOutputDir = tempTargetDir
                    )
                    FileUtils.atomicReplaceDirectory(tempTargetDir, casDir.normalizedTargetDir)
                } finally {
                    if (tempTargetDir.exists()) tempTargetDir.deleteRecursivelyWithRetry()
                }
            }
        }
        processedWithCommon.writeText(Clock.System.now().toString())
    }

    context(progress: ProgressReporter)
    private suspend fun ensureBaseReady(
        dep: GradleDependency,
        casDir: CASDependencySourcesDir,
        forceDownload: Boolean,
        commonCasDir: CASDependencySourcesDir?
    ) {
        // Double-checked locking pattern: release the OS file lock before any suspend-with-delay
        // calls (waitForBase polls with delay(500)), then re-acquire and re-check before acting.
        // This prevents blocking all other processes on casDir.baseLockFile for up to 300s.
        var needsLazyGeneration = false
        var needsFullProcessing = false
        var commonCasDirToWait: CASDependencySourcesDir? = null

        FileLockManager.withLock(casDir.baseLockFile) {
            val currentlyBroken = !casDir.baseCompletedMarker.exists() || !casDir.normalizedDir.exists()
            val processedWithCommon = casDir.baseDir.resolve(".processed-with-common")

            if (!forceDownload && !currentlyBroken) {
                if (commonCasDir != null && !processedWithCommon.exists()) {
                    // Need to wait for common sibling then do lazy generation — exit lock first.
                    needsLazyGeneration = true
                    commonCasDirToWait = commonCasDir
                }
                return@withLock
            }

            if (currentlyBroken) {
                // Invalidate search caches to release file handles before clearing.
                indexService.invalidateAllCaches(casDir.index)
                // Block-1 clearCasDir: releases Lucene file handles and evicts in-memory cache entries
                // so other processes holding shared locks can open the files cleanly after this exclusive
                // lock is released. Block-3's clearCasDir (inside the full-processing re-lock) is the
                // authoritative filesystem-prep that runs just before extraction; both calls are needed
                // because another process may have accessed the dirs between these two lock acquisitions.
                clearCasDir(casDir)
            }

            // Flag full processing needed; if a common sibling must be awaited, set
            // commonCasDirToWait so the wait happens outside this lock.
            needsFullProcessing = true
            if (commonCasDir != null) {
                commonCasDirToWait = commonCasDir
            }
        }

        // Wait for common sibling OUTSIDE the lock so we don't hold it across delay() calls.
        val toWait = commonCasDirToWait
        if (toWait != null) {
            val waitLabel = if (needsLazyGeneration) "lazy target generation" else "full processing"
            progress.report(0.0, 1.0, "Waiting for common sibling ${dep.commonComponentId} for $waitLabel")
            val completed = storageService.waitForBase(toWait)
            if (!completed) {
                val msg = "Common sibling ${dep.commonComponentId} failed to process."
                throw IllegalStateException(
                    if (needsLazyGeneration)
                        "$msg Cannot calculate platform-specific sources for ${dep.id}."
                    else
                        "$msg Failing dependent platform artifact ${dep.id}."
                )
            }
        }

        val commonCas = commonCasDirToWait
        if (needsLazyGeneration && commonCas != null) {
            FileLockManager.withLock(casDir.baseLockFile) {
                val isBroken = !casDir.baseCompletedMarker.exists() || !casDir.normalizedDir.exists()
                if (isBroken) {
                    needsFullProcessing = true
                    return@withLock
                }
                generateNormalizedTarget(dep, casDir, commonCas, progress)
            }
            if (!needsFullProcessing) return
        }

        if (!needsFullProcessing) return

        // Full processing path — re-acquire lock to do extraction/normalization.
        FileLockManager.withLock(casDir.baseLockFile) {
            // Re-check: another process may have completed while we were waiting.
            val currentlyBroken = !casDir.baseCompletedMarker.exists() || !casDir.normalizedDir.exists()
            // If another process completed full processing first the marker already exists — skip to avoid a second write.
            if (!forceDownload && !currentlyBroken) {
                if (commonCasDir != null && !casDir.processedWithCommonMarker.exists()) {
                    generateNormalizedTarget(dep, casDir, commonCasDir, progress)
                }
                return@withLock
            }

            val tempDir = casDir.baseDir.resolveSibling("${casDir.hash}.base.${Uuid.random()}.tmp")
            if (tempDir.exists()) tempDir.deleteRecursivelyWithRetry()
            tempDir.createDirectories()

            try {
                if (forceDownload || currentlyBroken) {
                    if (!forceDownload && casDir.baseDir.exists()) {
                        logger.warn("Found incomplete or empty CAS entry for ${dep.id} at ${casDir.baseDir}. Repairing.")
                    }
                    // clearCasDir deletes .processed-with-common, so lazy generation will re-run on next access.
                    clearCasDir(casDir)
                }
                casDir.baseDir.createDirectories()

                // Step A: Ensure sources exist (extract if needed)
                val sourcesInput = ensureSourcesStep(dep, casDir, tempDir, forceDownload)

                // Step B: Normalize sources
                val normalizeResult = normalizeStep(dep, sourcesInput, tempDir, commonCasDir)

                // Step D: Finalize (atomic moves and marker)
                finalizeStep(dep, casDir, tempDir, normalizeResult)

                if (commonCasDir != null) {
                    casDir.baseDir.resolve(".processed-with-common").writeText(Clock.System.now().toString())
                }

            } catch (e: Exception) {
                logger.error("Failed to process base CAS entry for ${dep.id}", e)
                throw e
            } finally {
                if (tempDir.exists()) tempDir.deleteRecursivelyWithRetry()
            }
        }
    }

    private fun clearCasDir(casDir: CASDependencySourcesDir) {
        casDir.sources.deleteRecursivelyWithRetry()
        casDir.normalizedDir.deleteRecursivelyWithRetry()
        casDir.normalizedTargetDir.deleteRecursivelyWithRetry()
        casDir.index.deleteRecursivelyWithRetry()
        casDir.sourceSetsFile.deleteIfExists()
        casDir.baseCompletedMarker.deleteIfExists()
        casDir.processedWithCommonMarker.deleteIfExists()

        // Clean up orphaned lazy-generation temp directories (siblings of baseDir)
        try {
            casDir.baseDir.parent.listDirectoryEntries("${casDir.hash}.lazy-target.*.tmp").forEach {
                it.deleteRecursivelyWithRetry()
            }
        } catch (e: Exception) {
            logger.warn("Failed to clean up orphaned lazy-target directories in ${casDir.baseDir.parent}: ${e.message}")
        }
    }

    context(progress: ProgressReporter)
    private suspend fun ensureSourcesStep(
        dep: GradleDependency,
        casDir: CASDependencySourcesDir,
        tempDir: Path,
        forceDownload: Boolean
    ): Path {
        return casDir.sources.takeIf { !forceDownload && it.exists() }
            ?: run {
                val tempSources = tempDir.resolve("sources")
                progress.report(0.1, 1.0, "Extracting sources for ${dep.id}")
                with(progress) {
                    storageService.extractSources(tempSources, dep)
                }
                tempSources
            }
    }

    private data class NormalizeResult(
        val normalizedDir: Path,
        val targetDir: Path,
        val sourceSetsFile: Path,
        val detectedSets: List<String>
    )

    context(progress: ProgressReporter)
    private fun normalizeStep(
        dep: GradleDependency,
        sourcesInput: Path,
        tempDir: Path,
        commonCasDir: CASDependencySourcesDir?
    ): NormalizeResult {
        progress.report(0.4, 1.0, "Normalizing sources for ${dep.id}")
        val tempNormalizedDir = tempDir.resolve("normalized")
        val tempTargetDir = tempDir.resolve("normalized-target")
        val sourceSets = detectSourceSets(dep, sourcesInput)
        val platformSpecificSets = try {
            calculatePlatformSpecificSets(dep, sourceSets, commonCasDir)
        } catch (e: MissingSourceSetsFileException) {
            logger.warn("Falling back to full sources for ${dep.id}: ${e.message}")
            emptySet()
        }

        detectAndNormalize(
            sourcesDir = sourcesInput,
            outputDir = tempNormalizedDir,
            sourceSets = sourceSets,
            platformSpecificSets = platformSpecificSets,
            targetOutputDir = tempTargetDir
        )

        val detectedSets = sourceSets.map { it.name }
        val tempSourceSetsFile = tempDir.resolve("source-sets.txt")
        Files.write(tempSourceSetsFile, detectedSets, Charsets.UTF_8)

        return NormalizeResult(tempNormalizedDir, tempTargetDir, tempSourceSetsFile, detectedSets)
    }

    context(progress: ProgressReporter)
    private suspend fun indexStep(
        dep: GradleDependency,
        tempDir: Path,
        casDir: CASDependencySourcesDir,
        tempNormalizedDir: Path,
        providerToIndex: SearchProvider
    ) {
        progress.report(0.5, 1.0, "Indexing ${dep.id}")
        with(progress) {
            indexFromNormalized(dep, tempDir, tempNormalizedDir, providerToIndex, sourceHash = casDir.hash)
        }

        val tempIndexBaseDir = tempDir.resolve("index")
        val tempProviderDir = tempIndexBaseDir.resolve(providerToIndex.name)
        if (tempProviderDir.exists()) {
            val targetProviderDir = casDir.index.resolve(providerToIndex.name)
            logger.info("Moving index for ${dep.id} from $tempProviderDir to $targetProviderDir")
            providerToIndex.invalidateCache(casDir.index.resolve(providerToIndex.name))

            // Ensure index parent exists before moves
            casDir.index.createDirectories()
            FileUtils.atomicReplaceDirectory(tempProviderDir, targetProviderDir)

            val markerFile = tempIndexBaseDir.resolve(providerToIndex.markerFileName)
            if (markerFile.exists()) {
                val targetMarker = casDir.index.resolve(providerToIndex.markerFileName)
                Files.move(markerFile, targetMarker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        } else {
            logger.warn("Index directory $tempProviderDir does not exist after indexing ${dep.id}")
        }
    }

    private suspend fun finalizeStep(
        dep: GradleDependency,
        casDir: CASDependencySourcesDir,
        tempDir: Path,
        normalizeResult: NormalizeResult
    ) {
        // Atomic moves — sources only if freshly extracted
        val tempSourcesDir = tempDir.resolve("sources")
        if (tempSourcesDir.exists()) {
            logger.info("Moving extracted sources from $tempSourcesDir to ${casDir.sources}")
            FileUtils.atomicMoveIfAbsent(tempSourcesDir, casDir.sources)
        }

        if (normalizeResult.normalizedDir.exists()) {
            logger.info("Moving normalized sources from ${normalizeResult.normalizedDir} to ${casDir.normalizedDir}")
            FileUtils.atomicMoveIfAbsent(normalizeResult.normalizedDir, casDir.normalizedDir)
        }

        if (normalizeResult.targetDir.exists()) {
            logger.info("Moving target-specific sources from ${normalizeResult.targetDir} to ${casDir.normalizedTargetDir}")
            FileUtils.atomicMoveIfAbsent(normalizeResult.targetDir, casDir.normalizedTargetDir)
        }

        if (normalizeResult.sourceSetsFile.exists()) {
            FileUtils.atomicMoveIfAbsent(normalizeResult.sourceSetsFile, casDir.sourceSetsFile)
        }

        try {
            casDir.baseCompletedMarker.createFile()
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            logger.info("Base completion marker already exists for ${dep.id}, another process might have finished it.")
        }
    }

    /**
     * Indexes all source files in [tempDir]/normalized/ into the Lucene index.
     * Paths in the index use the format `{group}/{name}/{packagePath}`.
     * Preserves the parallel channel-based indexing pattern for throughput.
     */
    context(progress: ProgressReporter)
    private suspend fun indexFromNormalized(
        dep: GradleDependency,
        tempDir: Path,
        indexSourceDir: Path,
        providerToIndex: SearchProvider,
        sourceHash: String? = null
    ) = coroutineScope {
        val depBase = requireNotNull(dep.relativePrefix) { "relativePrefix must not be null for ${dep.id}" }
        val channel = Channel<IndexEntry>(capacity = 20)

        val indexJob = launch(dispatcher) {
            try {
                val indexResult = indexService.indexFiles(tempDir, channel.consumeAsFlow(), providerToIndex)
                if (indexResult == null) {
                    logger.warn("Indexing yielded no results for ${dep.id} with provider ${providerToIndex.name}")
                }
            } finally {
                for (entry in channel) {
                    // drain on failure
                }
            }
        }

        try {
            if (indexSourceDir.exists()) {
                Files.walk(indexSourceDir).use { stream ->
                    for (file in stream) {
                        if (Files.isRegularFile(file)) {
                            val ext = file.fileName.toString().substringAfterLast('.', "")
                            if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                                val pathWithinNormalized = indexSourceDir.relativize(file).toString().replace('\\', '/')
                                val fullRelativePath = "$depBase/$pathWithinNormalized"
                                logger.debug("Indexing file: $fullRelativePath")
                                channel.send(IndexEntry(fullRelativePath, sourceHash = sourceHash) { file.readText() })
                            }
                        }
                    }
                }
            } else {
                logger.warn("indexSourceDir $indexSourceDir does not exist for indexing ${dep.id}")
            }
        } catch (e: Exception) {
            indexJob.cancel()
            throw e
        } finally {
            channel.close()
        }
        indexJob.join()
    }

    // ── Source-root normalization ─────────────────────────────────────────────

    /**
     * KMP source set directory names recognised at the root of an extracted sources jar.
     * Entries here receive a platform-specific suffix when merged into normalized/.
     */
    private val KMP_SOURCE_SET_NAMES = setOf(
        "commonMain", "jvmMain", "jsMain", "nativeMain", "androidMain",
        "iosMain", "mingwMain", "linuxMain", "macosMain",
        "wasmMain", "wasmJsMain", "wasmWasiMain"
    )

    /** Source sets whose files are written with their canonical name (no platform suffix). */
    private val PRIMARY_SOURCE_SET_NAMES = setOf("commonMain", "main")

    /**
     * Returns the short platform name used for renaming files in non-primary source sets,
     * or `null` if the source set is primary (files keep their canonical name).
     */
    private fun platformShortName(sourceSetName: String): String? = when (sourceSetName) {
        "commonMain", "main" -> null
        "jvmMain" -> "jvm"
        "jsMain" -> "js"
        "nativeMain" -> "native"
        "androidMain" -> "android"
        "iosMain" -> "ios"
        "mingwMain" -> "mingw"
        "linuxMain" -> "linux"
        "macosMain" -> "macos"
        "wasmMain" -> "wasm"
        "wasmJsMain" -> "wasmJs"
        "wasmWasiMain" -> "wasmWasi"
        else -> sourceSetName.removeSuffix("Main").lowercase()
    }

    /** A detected source set root ready for normalization. */
    private data class SourceSetInfo(
        /** Source set name (empty string = unnamed / direct root). */
        val name: String,
        /** Absolute path to the directory that directly contains package dirs or source files. */
        val contentRoot: Path,
        /** null = primary (no renaming); non-null = platform suffix to add. */
        val platformShortName: String?
    )

    /**
     * Detects the logical source sets inside [sourcesDir] and returns them in processing order
     * (primary sets first: commonMain → main → unnamed root → platform sets alphabetically).
     */
    private fun detectSourceSets(dep: GradleDependency, sourcesDir: Path): List<SourceSetInfo> {
        val allSets = mutableListOf<SourceSetInfo>()

        // 1. Check for standard Maven layout: src/main/kotlin or src/main/java
        val srcMainDir = sourcesDir.resolve("src").resolve("main")
        if (srcMainDir.exists() && Files.isDirectory(srcMainDir)) {
            val kotlinDir = srcMainDir.resolve("kotlin")
            val javaDir = srcMainDir.resolve("java")
            if (kotlinDir.exists()) allSets.add(SourceSetInfo("", kotlinDir, null))
            if (javaDir.exists()) allSets.add(SourceSetInfo("", javaDir, null))
            if (allSets.isNotEmpty()) return allSets
            return listOf(SourceSetInfo("", srcMainDir, null))
        }

        // 2. Check for alternative Maven-like layout: kotlin/ or java/ at root
        val kotlinRoot = sourcesDir.resolve("kotlin")
        val javaRoot = sourcesDir.resolve("java")
        if (kotlinRoot.exists()) allSets.add(SourceSetInfo("", kotlinRoot, null))
        if (javaRoot.exists()) allSets.add(SourceSetInfo("", javaRoot, null))
        if (allSets.isNotEmpty()) return allSets

        // 3. KMP layout: check root and src/ for known source-set dirs
        val candidates = mutableListOf<Path>()
        if (sourcesDir.exists()) {
            Files.list(sourcesDir).use { candidates.addAll(it.toList()) }
            val srcDir = sourcesDir.resolve("src")
            if (srcDir.exists() && Files.isDirectory(srcDir)) {
                Files.list(srcDir).use { candidates.addAll(it.toList()) }
            }
        }

        val kmpSets = mutableListOf<SourceSetInfo>()
        for (dir in candidates.filter { Files.isDirectory(it) }.distinctBy { it.fileName.toString() }) {
            val dirName = dir.fileName.toString()
            val isKmp = dirName.endsWith("Main", ignoreCase = true) || dirName in KMP_SOURCE_SET_NAMES
            if (!isKmp) continue

            val shortName = platformShortName(dirName)
            val kotlinDir = dir.resolve("kotlin")
            val javaDir = dir.resolve("java")

            when {
                kotlinDir.exists() && javaDir.exists() -> {
                    kmpSets.add(SourceSetInfo(dirName, kotlinDir, shortName))
                    kmpSets.add(SourceSetInfo(dirName, javaDir, shortName))
                }

                kotlinDir.exists() -> kmpSets.add(SourceSetInfo(dirName, kotlinDir, shortName))
                javaDir.exists() -> kmpSets.add(SourceSetInfo(dirName, javaDir, shortName))
                else -> kmpSets.add(SourceSetInfo(dirName, dir, shortName))
            }
        }

        if (kmpSets.isNotEmpty()) {
            return kmpSets.sortedWith(compareBy({ if (it.platformShortName == null) 0 else 1 }, { it.name }))
        }

        // 4. Fallback: Direct root
        return listOf(SourceSetInfo("", sourcesDir, null))
    }

    private fun calculatePlatformSpecificSets(dep: GradleDependency, sourceSets: List<SourceSetInfo>, commonCasDir: CASDependencySourcesDir?): Set<String> {
        if (commonCasDir == null) return emptySet()
        if (!commonCasDir.sourceSetsFile.exists()) {
            throw MissingSourceSetsFileException("Common sibling ${dep.commonComponentId} for ${dep.id} is missing its source-sets.txt file. Cannot calculate platform-specific source sets.")
        }

        val commonSets = Files.readAllLines(commonCasDir.sourceSetsFile, Charsets.UTF_8).toSet()
        val platformSets = sourceSets.map { it.name }.toSet()
        return platformSets - commonSets
    }

    /**
     * Detects source sets in [sourcesDir] and writes a flat merged structure to [outputDir].
     * If [platformSpecificSets] and [targetOutputDir] are provided, files belonging to those
     * source sets are also copied to the target directory for KMP target isolation.
     * Returns the list of detected source set names.
     */
    private fun detectAndNormalize(
        sourcesDir: Path,
        outputDir: Path?,
        sourceSets: List<SourceSetInfo>,
        platformSpecificSets: Set<String> = emptySet(),
        targetOutputDir: Path? = null
    ) {
        outputDir?.createDirectories()
        if (platformSpecificSets.isNotEmpty()) {
            targetOutputDir?.createDirectories()
        }

        logger.debug("Normalizing ${sourceSets.size} source set(s) from $sourcesDir: ${sourceSets.map { it.name.ifEmpty { "<root>" } }}")

        for (sourceSet in sourceSets) {
            if (!sourceSet.contentRoot.exists()) continue

            val isPlatformSpecific = sourceSet.name in platformSpecificSets

            Files.walk(sourceSet.contentRoot).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    val relPath = sourceSet.contentRoot.relativize(file).toString().replace('\\', '/')
                    val ext = relPath.substringAfterLast('.', "")
                    if (ext !in SearchProvider.SOURCE_EXTENSIONS) return@forEach

                    val fileContent = file.readBytes()
                    logger.debug("Normalizing file $relPath from source set ${sourceSet.name}")

                    // Write to main normalized/ directory
                    if (outputDir != null) {
                        val candidatePath = computeCandidatePath(outputDir, relPath, sourceSet.platformShortName)
                        writeFile(candidatePath, fileContent, relPath, sourceSet)
                    }

                    // Write to normalized-target/ if it's a platform-specific set
                    if (isPlatformSpecific && targetOutputDir != null && platformSpecificSets.isNotEmpty()) {
                        val targetCandidatePath = computeCandidatePath(targetOutputDir, relPath, sourceSet.platformShortName)
                        writeFile(targetCandidatePath, fileContent, relPath, sourceSet)
                    }
                }
            }
        }
    }

    private fun writeFile(candidatePath: Path, fileContent: ByteArray, relPath: String, sourceSet: SourceSetInfo) {
        if (!candidatePath.exists()) {
            candidatePath.createParentDirectories()
            candidatePath.writeBytes(fileContent)
        } else {
            val existingContent = candidatePath.readBytes()
            if (fileContent.contentEquals(existingContent)) {
                logger.trace("Skipping exact duplicate: $relPath (${sourceSet.name.ifEmpty { "<root>" }})")
            } else {
                val freePath = findFreePath(candidatePath)
                freePath.createParentDirectories()
                freePath.writeBytes(fileContent)
                logger.debug("Wrote conflicting file as ${freePath.fileName} (from ${sourceSet.name.ifEmpty { "<root>" }})")
            }
        }
    }

    private fun computeCandidatePath(outputDir: Path, relPath: String, platformShortName: String?): Path {
        if (platformShortName == null) {
            return outputDir.resolve(relPath)
        }
        val dir = relPath.substringBeforeLast('/', "")
        val fileName = relPath.substringAfterLast('/')
        val lastDot = fileName.lastIndexOf('.')
        val newFileName = if (lastDot >= 0) {
            "${fileName.substring(0, lastDot)}.$platformShortName${fileName.substring(lastDot)}"
        } else {
            "$fileName.$platformShortName"
        }
        return if (dir.isEmpty()) outputDir.resolve(newFileName) else outputDir.resolve("$dir/$newFileName")
    }

    /** Finds the first unused path by appending `(N)` before the extension. Extremely rare in practice. */
    private fun findFreePath(candidate: Path): Path {
        val dir = candidate.parent
        val fileName = candidate.fileName.toString()
        val lastDot = fileName.lastIndexOf('.')
        var n = 2
        while (true) {
            val newFileName = if (lastDot >= 0) {
                "${fileName.substring(0, lastDot)}($n)${fileName.substring(lastDot)}"
            } else {
                "$fileName($n)"
            }
            val freePath = dir.resolve(newFileName)
            if (!freePath.exists()) return freePath
            n++
        }
    }
}
