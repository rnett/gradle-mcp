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
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes

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
        // Fast path: already complete (checks .completed-v1; old .completed entries fall through)
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
            // Double-check after acquiring lock in case we raced
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
                    casDir.normalizedDir.deleteRecursively()
                    casDir.index.deleteRecursively()
                    casDir.completionMarker.deleteIfExists()
                }

                // Step A: Ensure sources exist (extract if needed), then normalize to v1/
                val sourcesInput = casDir.sources.takeIf { !forceDownload && it.exists() }
                    ?: run {
                        val tempSources = tempDir.resolve("sources")
                        progress.report(0.1, 1.0, "Extracting sources for ${dep.id}")
                        storageService.extractSources(tempSources, dep)
                        tempSources
                    }

                progress.report(0.4, 1.0, "Normalizing sources for ${dep.id}")
                detectAndNormalize(sourcesInput, tempDir.resolve("v1"))

                // Step B: Index from the normalized v1/ tree (only when a provider is requested)
                if (providerToIndex != null) {
                    logger.info("Indexing ${dep.id} with provider ${providerToIndex.name}")
                    progress.report(0.5, 1.0, "Indexing ${dep.id}")
                    indexFromNormalized(dep, tempDir, providerToIndex)

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
                }

                // Atomic moves — sources only if freshly extracted, v1/ always from temp
                val tempSourcesDir = tempDir.resolve("sources")
                if (tempSourcesDir.exists()) {
                    logger.info("Moving extracted sources from $tempSourcesDir to ${casDir.sources}")
                    FileUtils.atomicMoveIfAbsent(tempSourcesDir, casDir.sources)
                }
                val tempNormalizedDir = tempDir.resolve("v1")
                if (tempNormalizedDir.exists()) {
                    logger.info("Moving normalized sources from $tempNormalizedDir to ${casDir.normalizedDir}")
                    FileUtils.atomicMoveIfAbsent(tempNormalizedDir, casDir.normalizedDir)
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

    /**
     * Indexes all source files in [tempDir]/v1/ into the Lucene index.
     * Paths in the index use the format `deps/{name}/{version}/{packagePath}`.
     * Preserves the parallel channel-based indexing pattern for throughput.
     */
    context(progress: ProgressReporter)
    private suspend fun indexFromNormalized(
        dep: GradleDependency,
        tempDir: Path,
        providerToIndex: SearchProvider
    ) = coroutineScope {
        val normalizedDir = tempDir.resolve("v1")
        val depBase = requireNotNull(dep.relativePrefix) { "relativePrefix must not be null for ${dep.id}" }
        val channel = Channel<IndexEntry>(capacity = 20)

        val indexJob = launch(dispatcher) {
            try {
                val indexResult = indexService.indexFiles(tempDir, channel.consumeAsFlow(), providerToIndex)
                if (indexResult == null) {
                    throw IllegalStateException("Indexing failed for ${dep.id} with provider ${providerToIndex.name}")
                }
            } finally {
                for (entry in channel) {
                    // drain on failure
                }
            }
        }

        try {
            Files.walk(normalizedDir).use { stream ->
                for (file in stream) {
                    if (Files.isRegularFile(file)) {
                        val ext = file.fileName.toString().substringAfterLast('.', "")
                        if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                            val pathWithinNormalized = normalizedDir.relativize(file).toString().replace('\\', '/')
                            val fullRelativePath = "$depBase/$pathWithinNormalized"
                            channel.send(IndexEntry(fullRelativePath) { file.readText() })
                        }
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

    // ── Source-root normalization ─────────────────────────────────────────────

    /**
     * KMP source set directory names recognised at the root of an extracted sources jar.
     * Entries here receive a platform-specific suffix when merged into v1/.
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
    private fun detectSourceSets(sourcesDir: Path): List<SourceSetInfo> {
        // Maven layout: src/main/kotlin and/or src/main/java
        val srcMainDir = sourcesDir.resolve("src").resolve("main")
        if (Files.isDirectory(srcMainDir)) {
            val kotlinDir = srcMainDir.resolve("kotlin")
            val javaDir = srcMainDir.resolve("java")
            if (Files.isDirectory(kotlinDir) || Files.isDirectory(javaDir)) {
                return buildList {
                    if (Files.isDirectory(kotlinDir)) add(SourceSetInfo("", kotlinDir, null))
                    if (Files.isDirectory(javaDir)) add(SourceSetInfo("", javaDir, null))
                }
            }
        }

        // KMP layout: known source-set dirs at the root
        val topLevel = Files.list(sourcesDir).use { it.toList() }.filter { Files.isDirectory(it) }
        val kmpSets = mutableListOf<SourceSetInfo>()
        var hasKmp = false

        for (dir in topLevel) {
            val dirName = dir.fileName.toString()
            val isKmp = dirName in KMP_SOURCE_SET_NAMES
            val isMaybe = dirName == "main" // "main" alone is also acceptable
            if (!isKmp && !isMaybe) continue

            hasKmp = true
            val shortName = platformShortName(dirName)
            val kotlinDir = dir.resolve("kotlin")
            val javaDir = dir.resolve("java")

            when {
                Files.isDirectory(kotlinDir) && Files.isDirectory(javaDir) -> {
                    kmpSets.add(SourceSetInfo(dirName, kotlinDir, shortName))
                    kmpSets.add(SourceSetInfo(dirName, javaDir, shortName))
                }

                Files.isDirectory(kotlinDir) -> kmpSets.add(SourceSetInfo(dirName, kotlinDir, shortName))
                Files.isDirectory(javaDir) -> kmpSets.add(SourceSetInfo(dirName, javaDir, shortName))
                else -> kmpSets.add(SourceSetInfo(dirName, dir, shortName))
            }
        }

        if (hasKmp) {
            // Sort: primary sets (shortName == null) before platform sets, then alphabetically by name
            return kmpSets.sortedWith(compareBy({ if (it.platformShortName == null) 0 else 1 }, { it.name }))
        }

        // Direct root: jar directly exposes package dirs or source files
        return listOf(SourceSetInfo("", sourcesDir, null))
    }

    /**
     * Detects source sets in [sourcesDir] and writes a flat merged structure to [outputDir]:
     * - Primary set files land at `{outputDir}/{packagePath}` with their canonical name.
     * - Platform set files land at `{outputDir}/{dir}/{baseName}.{platform}.{ext}`.
     * - Files with identical byte content are silently de-duplicated.
     * - Files with the same candidate path but different content get a `(N)` disambiguation suffix.
     */
    private fun detectAndNormalize(sourcesDir: Path, outputDir: Path) {
        outputDir.createDirectories()

        val sourceSets = detectSourceSets(sourcesDir)
        logger.debug("Detected ${sourceSets.size} source set(s) in $sourcesDir: ${sourceSets.map { it.name.ifEmpty { "<root>" } }}")

        for (sourceSet in sourceSets) {
            if (!sourceSet.contentRoot.exists()) continue

            Files.walk(sourceSet.contentRoot).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    val relPath = sourceSet.contentRoot.relativize(file).toString().replace('\\', '/')
                    val ext = relPath.substringAfterLast('.', "")
                    if (ext !in SearchProvider.SOURCE_EXTENSIONS) return@forEach

                    val candidatePath = computeCandidatePath(outputDir, relPath, sourceSet.platformShortName)
                    val fileContent = file.readBytes()

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

    // ── Public API delegates ──────────────────────────────────────────────────

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
