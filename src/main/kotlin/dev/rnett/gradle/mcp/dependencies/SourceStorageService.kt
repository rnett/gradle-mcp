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
        private const val CAS_VERSION = "v2"
    }

    init {
        casDir.createDirectories()
        sessionViewsDir.createDirectories()
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

    override suspend fun createSessionView(deps: Map<GradleDependency, CASDependencySourcesDir>, force: Boolean): SessionViewSourcesDir = withContext(Dispatchers.IO) {
        val sessionId = Uuid.random().toString()
        val timestamp = Clock.System.now().toString().replace(Regex("[^a-zA-Z0-9]"), "_")
        val viewBaseDir = sessionViewsDir.resolve("${timestamp}_$sessionId")
        val viewSourcesDir = viewBaseDir.resolve("sources")
        viewSourcesDir.createDirectories()

        val depIdsInScope = deps.keys.map { it.id }.toSet()

        deps.forEach { (dep, casDir) ->
            val linkPath = viewSourcesDir.resolve(requireNotNull(dep.relativePrefix))
            linkPath.createParentDirectories()

            val commonId = dep.commonComponentId
            val hasCommonSibling = commonId != null && commonId in depIdsInScope

            if (hasCommonSibling && !casDir.normalizedTargetDir.exists()) {
                // Skip creating link, it has no platform-specific sources and its common sibling provides them
                return@forEach
            }

            // Prefer normalized-target/ if it's a platform artifact with its common sibling present.
            // Otherwise prefer pre-normalized normalized/ dir; fall back to raw sources/ for legacy CAS entries
            val linkTarget = if (hasCommonSibling) {
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

        val manifest = ProjectManifest(
            sessionId = sessionId,
            timestamp = Clock.System.now().toString(),
            dependencies = deps.mapNotNull { (dep, casDir) ->
                val commonId = dep.commonComponentId
                val hasCommonSibling = commonId != null && commonId in depIdsInScope
                if (hasCommonSibling && !casDir.normalizedTargetDir.exists()) {
                    null
                } else {
                    val isDiffOnly = hasCommonSibling && casDir.normalizedTargetDir.exists()
                    ManifestDependency(
                        id = dep.id,
                        hash = casDir.hash,
                        relativePath = requireNotNull(dep.relativePrefix).replace('\\', '/'),
                        isDiffOnly = isDiffOnly
                    )
                }
            }
        )

        val manifestFile = viewBaseDir.resolve("manifest.json")
        manifestFile.writeText(json.encodeToString(manifest))

        SessionViewSourcesDir(sessionId, viewBaseDir, viewSourcesDir, manifest, casDir)
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
