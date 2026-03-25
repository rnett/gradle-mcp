package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.ManifestDependency
import dev.rnett.gradle.mcp.dependencies.model.ProjectManifest
import dev.rnett.gradle.mcp.dependencies.model.SessionViewSourcesDir
import dev.rnett.gradle.mcp.hash
import dev.rnett.gradle.mcp.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
     * Calculates a unique stable hash for a sequence of dependencies.
     */
    fun calculateDependencyHash(deps: Sequence<GradleDependency>): String

    /**
     * Creates an ephemeral session view for the given dependencies.
     * Returns a [SessionViewSourcesDir] containing junctions to CAS entries.
     */
    suspend fun createSessionView(deps: Map<GradleDependency, CASDependencySourcesDir>): SessionViewSourcesDir

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
     * Polls for a CAS directory to be completed by another process.
     */
    suspend fun waitForCAS(casDir: CASDependencySourcesDir, timeout: Duration = 120.seconds)

    /**
     * Prunes session views older than the specified [maxAge].
     */
    suspend fun pruneSessionViews(maxAge: Duration = 24.seconds * 3600)
}

@OptIn(ExperimentalPathApi::class)
class DefaultSourceStorageService(private val environment: GradleMcpEnvironment) : SourceStorageService {
    private val logger = LoggerFactory.getLogger(DefaultSourceStorageService::class.java)

    private val casDir = environment.cacheDir.resolve("cas")
    private val sessionViewsDir = environment.cacheDir.resolve("views")

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
                md.digest().fold("") { str, it -> str + "%02x".format(it) }.take(8)
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

    override fun calculateDependencyHash(deps: Sequence<GradleDependency>): String {
        val input = deps
            .filter { it.hasSources }
            .map { "${it.id}:${it.sourcesFile?.fileName?.toString()}" }
            .sorted()
            .joinToString("\n")
        return input.toByteArray().hash()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun createSessionView(deps: Map<GradleDependency, CASDependencySourcesDir>): SessionViewSourcesDir = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        val timestamp = Clock.System.now().toString().replace(Regex("[^a-zA-Z0-9]"), "_")
        val viewBaseDir = sessionViewsDir.resolve("${timestamp}_$sessionId")
        val viewSourcesDir = viewBaseDir.resolve("sources")
        viewSourcesDir.createDirectories()

        deps.forEach { (dep, casDir) ->
            val linkPath = viewSourcesDir.resolve(requireNotNull(dep.relativePrefix))
            linkPath.createParentDirectories()
            if (!FileUtils.createSymbolicLink(linkPath, casDir.sources)) {
                logger.warn("Failed to create symlink/junction for ${dep.id} in session view. Falling back to copy.")
                casDir.sources.copyToRecursively(linkPath, followLinks = false, overwrite = true)
            }
        }

        val manifest = ProjectManifest(
            sessionId = sessionId,
            timestamp = Clock.System.now().toString(),
            dependencies = deps.map { (dep, casDir) ->
                ManifestDependency(
                    id = dep.id,
                    hash = casDir.hash,
                    relativePath = requireNotNull(dep.relativePrefix).replace('\\', '/')
                )
            }
        )

        SessionViewSourcesDir(sessionId, viewBaseDir, viewSourcesDir, manifest, casDir)
    }

    context(progress: ProgressReporter)
    override suspend fun extractSources(
        dir: Path,
        dep: GradleDependency,
        onFileExtracted: (suspend (String, ByteArray) -> Unit)?
    ) {
        try {
            ArchiveExtractor.extractInto(dir, requireNotNull(dep.sourcesFile), skipSingleFirstDir = true, writeFiles = true) { path, contentBytes ->
                onFileExtracted?.invoke(path, contentBytes)
            }
        } catch (e: Exception) {
            logger.error("Failed to extract sources for $dep", e)
            throw e
        }
    }

    override suspend fun waitForCAS(casDir: CASDependencySourcesDir, timeout: Duration) {
        val start = Clock.System.now()
        while (!casDir.completionMarker.exists()) {
            if (Clock.System.now() - start > timeout) {
                throw java.io.IOException("Timed out waiting for CAS directory ${casDir.hash} to be completed by another process after ${timeout}")
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

