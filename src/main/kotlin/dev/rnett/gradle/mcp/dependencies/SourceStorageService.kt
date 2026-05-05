@file:OptIn(ExperimentalUuidApi::class)
package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.ManifestDependency
import dev.rnett.gradle.mcp.dependencies.model.ProjectManifest
import dev.rnett.gradle.mcp.dependencies.model.SessionViewSourcesDir
import dev.rnett.gradle.mcp.hash
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Manages the physical layout and file system operations for dependency sources.
 * Responsible for extracting source archives, managing cache directories, maintaining metadata,
 * and performing filesystem-level locking to ensure safe concurrent access.
 */
interface SourceStorageService {

    /**
     * Gets the lock file for a specific storage path.
     */
    fun getLockFile(storagePath: Path): Path

    /**
     * Resolves the [CASDependencySourcesDir] for a specific dependency's content hash.
     */
    fun getCASDependencySourcesDir(hash: String): CASDependencySourcesDir

    /**
     * Calculates a unique stable hash for a specific dependency's sources.
     */
    fun calculateHash(dep: GradleDependency): String

    /**
     * Calculates a unique stable hash for a map of dependencies to their CAS directories.
     */
    fun calculateViewHash(deps: Map<GradleDependency, CASDependencySourcesDir>): String

    /**
     * Creates an ephemeral session view for the given dependencies.
     * Returns a [SessionViewSourcesDir] containing junctions to CAS entries.
     * If [force] is true, the view is recreated even if it already exists.
     */
    suspend fun createSessionView(deps: Map<GradleDependency, CASDependencySourcesDir>, force: Boolean = false): SessionViewSourcesDir
    /**
     * Extracts a dependency's source archive into the specified directory.
     * Supports a callback for each file extracted to enable parallel indexing.
     */
    context(progress: ProgressReporter)
    suspend fun extractSources(
        dir: Path,
        dep: GradleDependency,
        onFileExtracted: (suspend (String, ByteArray) -> Unit)? = null
    )

    /**
     * Normalizes a relative file path within a dependency by prepending its relative prefix.
     */
    fun normalizeRelativePath(prefix: Path, path: Path): String

    /**
     * Normalizes a relative file path string within a dependency by prepending its relative prefix.
     */
    fun normalizeRelativePath(prefix: Path, path: String): String

    /**
     * Waits for base sources (extraction/normalization) to be completed by another process.
     * Uses a shared lock to detect when the exclusive extraction lock is released.
     * Returns true if successfully completed, false if the lock was released but the marker is missing (failure).
     */
    suspend fun waitForBase(casDir: CASDependencySourcesDir, timeout: Duration = 300.seconds): Boolean

    /**
     * Prunes session views older than the specified [maxAge].
     */
    suspend fun pruneSessionViews(maxAge: Duration = 24.seconds * 3600)
}

@OptIn(ExperimentalPathApi::class)
class DefaultSourceStorageService(private val environment: GradleMcpEnvironment) : SourceStorageService {
    private val logger = LoggerFactory.getLogger(DefaultSourceStorageService::class.java)

    private val casDir = environment.cacheDir.resolve("cas").resolve(CAS_VERSION)
    private val sessionViewsDir = environment.cacheDir.resolve("views")

    companion object {
        private const val CAS_VERSION = "v3"
    }

    init {
        casDir.createDirectories()
        sessionViewsDir.createDirectories()
        cleanupOldCasVersions()
    }

    private fun cleanupOldCasVersions() {
        val currentVersion = CAS_VERSION.removePrefix("v").toIntOrNull() ?: return
        val casBase = environment.cacheDir.resolve("cas")
        if (!casBase.exists()) return
        try {
            casBase.listDirectoryEntries("v*")
                .forEach { dir ->
                    val dirVersion = dir.fileName.toString().removePrefix("v").toIntOrNull() ?: return@forEach
                    if (dirVersion < currentVersion) {
                        logger.info("Cleaning up old CAS version directory: ${dir.fileName}")
                        dir.deleteRecursively()
                    }
                }
        } catch (e: Exception) {
            logger.warn("Failed to clean up old CAS version directories", e)
        }
    }

    override fun normalizeRelativePath(prefix: Path, path: Path): String {
        return normalizeRelativePath(prefix, path.toString())
    }

    override fun normalizeRelativePath(prefix: Path, path: String): String {
        return prefix.resolve(path).toString().replace('\\', '/')
    }

    override fun getCASDependencySourcesDir(hash: String): CASDependencySourcesDir {
        return CASDependencySourcesDir(hash, casDir.resolve(hash.take(2)).resolve(hash))
    }

    override fun calculateHash(dep: GradleDependency): String {
        val sourcesFile = requireNotNull(dep.sourcesFile)
        return try {
            if (sourcesFile.exists()) {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                sourcesFile.toFile().inputStream().use { fis ->
                    java.security.DigestInputStream(fis, md).use { dis ->
                        val buffer = ByteArray(8192)
                        while (dis.read(buffer) != -1) {
                            // read to update digest
                        }
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }.take(32)
            } else {
                "${dep.id}:${sourcesFile.fileName}".toByteArray().hash()
            }
        } catch (e: Exception) {
            "${dep.id}:${sourcesFile.fileName}".toByteArray().hash()
        }
    }

    override fun getLockFile(storagePath: Path): Path {
        return environment.projectLockFile(storagePath)
    }

    override fun calculateViewHash(deps: Map<GradleDependency, CASDependencySourcesDir>): String {
        val input = deps.entries
            .map { (dep, casDir) -> "${dep.id}:${casDir.hash}" }
            .sorted()
            .joinToString("\n")
        return input.toByteArray().hash()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Computes whether [casDir]'s `normalizedTargetDir` exists and contains at least one file.
     * Guards against a TOCTOU race with lazy generation via a shared advisory lock. The fast path
     * (skipping the lock) is only safe if lazy generation is definitely complete (processedWithCommonMarker
     * exists). Otherwise, we must hold the shared lock to ensure we don't read while
     * normalizedTargetDir is being atomically replaced.
     */
    private suspend fun computeTargetExistsAndNotEmpty(casDir: CASDependencySourcesDir, hasCommonSibling: Boolean): Boolean {
        if (!hasCommonSibling) return false
        fun compute(): Boolean = casDir.normalizedTargetDir.exists() && try {
            Files.list(casDir.normalizedTargetDir).use { it.findFirst().isPresent }
        } catch (_: java.nio.file.NoSuchFileException) {
            false
        }

        // The baseCompletedMarker only ensures sources/ and normalized/ are stable.
        // normalizedTargetDir can be mutated by lazy generation AFTER baseCompletedMarker exists.
        // We can only skip the lock if lazy generation is definitely complete.
        return if (casDir.processedWithCommonMarker.exists()) {
            compute()
        } else {
            FileLockManager.withLock(casDir.baseLockFile, shared = true) { compute() }
        }
    }

    override suspend fun createSessionView(deps: Map<GradleDependency, CASDependencySourcesDir>, force: Boolean): SessionViewSourcesDir = withContext(Dispatchers.IO) {
        val sessionId = Uuid.random().toString()
        val timestamp = Clock.System.now().toString().replace(Regex("[^a-zA-Z0-9]"), "_")
        val viewBaseDir = sessionViewsDir.resolve("${timestamp}_$sessionId")
        val viewSourcesDir = viewBaseDir.resolve("sources")
        viewSourcesDir.createDirectories()

        val depIdsInScope = deps.keys.map { it.id }.toSet()

        val failedDependencies = deps.filter { (_, casDir) ->
            !casDir.baseCompletedMarker.exists()
        }.keys.map { it.id }.sorted()

        val readyDeps: Map<GradleDependency, CASDependencySourcesDir> = deps.filter { (dep, casDir) ->
            val ready = casDir.baseCompletedMarker.exists()
            if (!ready) {
                logger.warn("Skipping dep ${dep.id} in session view: baseCompletedMarker absent (base processing incomplete or failed).")
            }
            ready
        }
        val validatedPrefixes = readyDeps.keys.associateWith { validateSessionViewPrefix(it) }

        // Pre-compute once per dep to avoid TOCTOU: if clearCasDir ran between the symlink loop and
        // the manifest loop the two calls would return different values, producing a junction pointing
        // to normalizedTargetDir while the manifest says isDiffOnly=false (or vice versa).
        val targetExistsSnapshot: Map<GradleDependency, Boolean> = readyDeps.entries
            .associate { (dep, casDir) ->
                val hasCommonSibling = dep.commonComponentId != null && dep.commonComponentId in depIdsInScope
                dep to computeTargetExistsAndNotEmpty(casDir, hasCommonSibling)
            }

        for ((dep, casDir) in readyDeps) {
            val relativePrefix = validatedPrefixes.getValue(dep)
            val linkPath = viewSourcesDir.resolve(relativePrefix).normalize()
            linkPath.createParentDirectories()

            val commonId = dep.commonComponentId
            val hasCommonSibling = commonId != null && commonId in depIdsInScope

            val targetExistsAndNotEmpty = targetExistsSnapshot.getValue(dep)

            if (hasCommonSibling && !targetExistsAndNotEmpty) {
                // Skip creating link, it has no platform-specific sources and its common sibling provides them
                continue
            }

            // Prefer normalized-target/ if it's a platform artifact with its common sibling present.
            // Otherwise prefer pre-normalized normalized/ dir; fall back to raw sources/ for legacy CAS entries
            val linkTarget = if (hasCommonSibling && targetExistsAndNotEmpty) {
                casDir.normalizedTargetDir
            } else if (casDir.normalizedDir.exists()) {
                casDir.normalizedDir
            } else {
                casDir.sources
            }

            if (!FileUtils.createSymbolicLink(linkPath, linkTarget)) {
                logger.warn("Failed to create symlink/junction for ${dep.id} in session view. Falling back to copy.")
                linkTarget.copyToRecursively(linkPath, followLinks = false, overwrite = true)
            }
        }

        val manifestDeps = mutableListOf<ManifestDependency>()
        for ((dep, casDir) in readyDeps) {
            val commonId = dep.commonComponentId
            val hasCommonSibling = commonId != null && commonId in depIdsInScope

            val targetExistsAndNotEmpty = targetExistsSnapshot.getValue(dep)

            if (!(hasCommonSibling && !targetExistsAndNotEmpty)) {
                val isDiffOnly = hasCommonSibling && targetExistsAndNotEmpty
                manifestDeps.add(
                    ManifestDependency(
                        id = dep.id,
                        hash = casDir.hash,
                        relativePath = validatedPrefixes.getValue(dep).replace('\\', '/'),
                        isDiffOnly = isDiffOnly
                    )
                )
            }
        }

        val manifest = ProjectManifest(
            sessionId = sessionId,
            timestamp = Clock.System.now().toString(),
            dependencies = manifestDeps,
            failedDependencies = failedDependencies
        )

        val manifestFile = viewBaseDir.resolve("manifest.json")
        manifestFile.writeText(json.encodeToString(manifest))

        SessionViewSourcesDir(sessionId, viewBaseDir, viewSourcesDir, manifest, casDir)
    }

    private fun validateSessionViewPrefix(dep: GradleDependency): String {
        val rawPrefix = requireNotNull(dep.relativePrefix) { "Dependency ${dep.id} has no session-view prefix." }
        val normalizedSeparators = rawPrefix.replace('\\', '/')
        val isSyntheticJdk = dep.id.startsWith("JDK:") &&
                dep.group == GradleDependency.JDK_SOURCES_GROUP &&
                dep.name == GradleDependency.JDK_SOURCES_NAME

        require(rawPrefix == normalizedSeparators) {
            "Dependency ${dep.id} has invalid session-view prefix '$rawPrefix': backslashes are not allowed."
        }

        val segments = normalizedSeparators.split('/')
        require(segments.isNotEmpty() && segments.all { it.isNotBlank() }) {
            "Dependency ${dep.id} has invalid session-view prefix '$rawPrefix': empty path segments are not allowed."
        }
        require(segments.none { it == "." || it == ".." || ':' in it }) {
            "Dependency ${dep.id} has invalid session-view prefix '$rawPrefix': unsafe path segments are not allowed."
        }

        val path = Path.of(normalizedSeparators)
        require(!path.isAbsolute) {
            "Dependency ${dep.id} has invalid session-view prefix '$rawPrefix': absolute paths are not allowed."
        }

        val normalized = path.normalize().toString().replace('\\', '/')
        require(normalized == normalizedSeparators) {
            "Dependency ${dep.id} has invalid session-view prefix '$rawPrefix': normalized path escapes its declared prefix."
        }

        val reservedPrefix = GradleDependency.JDK_SOURCES_PREFIX
        val usesReservedJdkPath = normalized == reservedPrefix || normalized.startsWith("$reservedPrefix/")
        if (isSyntheticJdk) {
            require(normalized == reservedPrefix) {
                "Synthetic JDK dependency must use reserved session-view prefix '$reservedPrefix'."
            }
        } else {
            require(!usesReservedJdkPath) {
                "Dependency ${dep.id} uses reserved session-view prefix '$normalized'."
            }
        }

        return normalized
    }

    context(progress: ProgressReporter)
    override suspend fun extractSources(
        dir: Path,
        dep: GradleDependency,
        onFileExtracted: (suspend (String, ByteArray) -> Unit)?
    ) {
        try {
            ArchiveExtractor.extractInto(dir, requireNotNull(dep.sourcesFile), skipSingleFirstDir = false, writeFiles = true) { path, contentBytes ->
                onFileExtracted?.invoke(path, contentBytes)
            }
        } catch (e: Exception) {
            logger.error("Failed to extract sources for $dep", e)
            throw e
        }
    }

    override suspend fun waitForBase(casDir: CASDependencySourcesDir, timeout: Duration): Boolean {
        val start = Clock.System.now()
        while (true) {
            if (casDir.baseCompletedMarker.exists()) return true

            // Try to acquire shared lock to see if the exclusive lock is released
            val lock = FileLockManager.tryLockAdvisory(casDir.baseLockFile, shared = true)
            if (lock != null) {
                lock.close() // Release immediately
                // If we got the shared lock, it means NO ONE has the exclusive lock.
                // Re-check marker one last time.
                return casDir.baseCompletedMarker.exists()
            }

            if (Clock.System.now() - start > timeout) {
                throw java.io.IOException("Timed out waiting for base CAS sources for ${casDir.hash} after ${timeout}")
            }
            kotlinx.coroutines.delay(500)
        }
    }


    override suspend fun pruneSessionViews(maxAge: Duration) = withContext(Dispatchers.IO) {
        if (!sessionViewsDir.exists()) return@withContext
        val now = Clock.System.now()
        sessionViewsDir.listDirectoryEntries().forEach { entry ->
            try {
                val lastModified = entry.getLastModifiedTime().toInstant()
                val lastModifiedKotlin = kotlin.time.Instant.fromEpochMilliseconds(lastModified.toEpochMilli())
                if (now - lastModifiedKotlin > maxAge) {
                    logger.info("Pruning old session view: ${entry.fileName}")
                    entry.deleteRecursively()
                }
            } catch (e: Exception) {
                logger.warn("Failed to prune session view entry: $entry", e)
            }
        }
    }
}
