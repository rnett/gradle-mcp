package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SingleDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.hash
import dev.rnett.gradle.mcp.utils.FileUtils
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.time.Clock

/**
 * Manages the physical layout and file system operations for dependency sources.
 * Responsible for extracting source archives, managing cache directories, maintaining metadata,
 * and performing filesystem-level locking to ensure safe concurrent access.
 */
interface SourceStorageService {
    /**
     * The root directory where extracted sources are stored globally.
     */
    val globalSourcesDir: Path

    /**
     * Resolves the [MergedSourcesDir] for a set of dependencies based on their project-level path and kind.
     */
    fun getMergedSourcesDir(projectRoot: GradleProjectRoot, path: String, kind: String): MergedSourcesDir

    /**
     * Resolves the [SingleDependencySourcesDir] for a specific dependency.
     */
    fun getSingleDependencySourcesDir(dep: GradleDependency): SingleDependencySourcesDir

    /**
     * Gets the global lock file for a specific extraction directory.
     */
    fun getGlobalLockFile(dir: Path): Path

    /**
     * Gets the lock file for a project-level merged sources directory.
     */
    fun getLockFile(storagePath: Path): Path

    /**
     * Calculates a unique stable hash for a sequence of dependencies.
     */
    fun calculateDependencyHash(deps: Sequence<GradleDependency>): String

    /**
     * Retrieves the stored dependency hash for a merged sources directory.
     */
    fun getDependencyHash(dir: MergedSourcesDir): String?

    /**
     * Checks if a merged sources directory is already cached and matches the current hash.
     */
    fun isMergedSourcesCached(dir: MergedSourcesDir, currentHash: String, forceDownload: Boolean): Boolean

    /**
     * Persists metadata and hash for a merged sources directory.
     */
    fun saveMergedSourcesCache(dir: MergedSourcesDir, currentHash: String, indices: Map<Path, Path>)

    /**
     * Loads the map of dependency-level index directories from a merged cache.
     */
    fun loadCachedIndices(dir: MergedSourcesDir): Map<Path, Path>?

    /**
     * Attempts to return a valid [MergedSourcesDir] from the cache if it's not stale.
     */
    fun tryGetCachedMergedSources(dir: MergedSourcesDir, fresh: Boolean, forceDownload: Boolean): MergedSourcesDir?

    /**
     * Extracts a dependency's source archive into the specified directory.
     * Supports a callback for each file extracted to enable parallel indexing.
     */
    context(progress: ProgressReporter)
    suspend fun extractSources(
        dir: Path,
        dep: GradleDependency,
        forceDownload: Boolean = false,
        onFileExtracted: (suspend (String, ByteArray) -> Unit)? = null
    )

    /**
     * Synchronizes (via symlink/junction or copy) an extraction directory into a project-level sources scope.
     */
    fun syncTo(scopeLink: Path, dir: Path, dep: GradleDependency, targetSources: Path)

    /**
     * Normalizes a relative file path within a dependency by prepending its relative prefix.
     */
    fun normalizeRelativePath(prefix: Path, path: Path): String

    /**
     * Normalizes a relative file path string within a dependency by prepending its relative prefix.
     */
    fun normalizeRelativePath(prefix: Path, path: String): String

    /**
     * Prepares the target sources directory by deleting existing content and creating a fresh one.
     */
    fun prepareTargetSources(target: SourcesDir)

    /**
     * Checks if a directory is marked as successfully extracted.
     */
    fun isExtracted(dir: Path): Boolean

    /**
     * Marks a directory as successfully extracted.
     */
    fun markExtracted(dir: Path)

    /**
     * Deletes the contents of a merged sources directory.
     */
    fun deleteMergedSources(dir: MergedSourcesDir)

    /**
     * Deletes the contents of a single dependency's extraction directory.
     */
    fun deleteSingleDependencySources(dir: Path)
}

@OptIn(ExperimentalPathApi::class)
class DefaultSourceStorageService(private val environment: GradleMcpEnvironment) : SourceStorageService {
    private val logger = LoggerFactory.getLogger(DefaultSourceStorageService::class.java)

    private val sourcesDir = environment.cacheDir.resolve("sources")
    override val globalSourcesDir = environment.cacheDir.resolve("extracted-sources")

    init {
        sourcesDir.createDirectories()
        globalSourcesDir.createDirectories()
    }

    override fun normalizeRelativePath(prefix: Path, path: Path): String {
        return normalizeRelativePath(prefix, path.toString())
    }

    override fun normalizeRelativePath(prefix: Path, path: String): String {
        return prefix.resolve(path).toString().replace('\\', '/')
    }

    override fun getMergedSourcesDir(projectRoot: GradleProjectRoot, path: String, kind: String): MergedSourcesDir {
        val storagePath = sourcesDir.resolve("${projectRoot.projectRoot.hashCode()}-${path.hashCode()}-${kind.hashCode()}")
        return MergedSourcesDir(storagePath, getLockFile(storagePath), globalSourcesDir)
    }

    override fun getSingleDependencySourcesDir(dep: GradleDependency): SingleDependencySourcesDir {
        val group = requireNotNull(dep.group)
        val sourcesFile = requireNotNull(dep.sourcesFile)
        val dir = globalSourcesDir.resolve(kotlin.io.path.Path(group.replace('.', '/')).resolve(sourcesFile.nameWithoutExtension))
        val lockFile = environment.dependencyLockFile(requireNotNull(dep.relativePrefix))
        return SingleDependencySourcesDir(dep.id, dir, kotlin.io.path.Path(""), lockFile)
    }

    override fun getLockFile(storagePath: Path): Path {
        return environment.projectLockFile(storagePath)
    }

    override fun getGlobalLockFile(dir: Path): Path {
        val relative = try {
            dir.relativeTo(globalSourcesDir).toString().replace('\\', '/')
        } catch (e: Exception) {
            null
        }
        if (relative != null) {
            return environment.dependencyLockFile(relative)
        }

        val lockName = dir.toAbsolutePath().toString().toByteArray().hash().take(12)
        val lockFile = environment.cacheDir.resolve(".locks").resolve("extracted-sources").resolve("ext-$lockName.lock")
        lockFile.createParentDirectories()
        return lockFile
    }

    override fun calculateDependencyHash(deps: Sequence<GradleDependency>): String {
        val input = deps
            .filter { it.hasSources }
            .map { "${it.id}:${it.sourcesFile?.fileName?.toString()}" }
            .sorted()
            .joinToString("\n")
        return input.toByteArray().hash()
    }

    override fun getDependencyHash(dir: MergedSourcesDir): String? {
        val hashFile = dir.storagePath.resolve(".dependencies.hash")
        return if (hashFile.exists()) hashFile.readText() else null
    }

    override fun isMergedSourcesCached(dir: MergedSourcesDir, currentHash: String, forceDownload: Boolean): Boolean {
        val hashFile = dir.storagePath.resolve(".dependencies.hash")
        if (forceDownload) {
            dir.storagePath.deleteRecursively()
            return false
        }
        return hashFile.exists() && hashFile.readText() == currentHash
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun saveMergedSourcesCache(dir: MergedSourcesDir, currentHash: String, indices: Map<Path, Path>) {
        dir.storagePath.createDirectories()
        dir.metadata.createDirectories()
        dir.lastRefreshFile.writeText(Clock.System.now().toString())

        val metadataFile = dir.storagePath.resolve(".dependencies.json")
        val metadata = indices.mapKeys { it.key.toString() }.mapValues { it.value.toString() }
        metadataFile.writeText(json.encodeToString(metadata))

        // Write hash LAST to ensure metadata is also written
        dir.storagePath.resolve(".dependencies.hash").writeText(currentHash)
    }

    override fun loadCachedIndices(dir: MergedSourcesDir): Map<Path, Path>? {
        val metadataFile = dir.storagePath.resolve(".dependencies.json")
        if (!metadataFile.exists()) return null
        return try {
            val metadata = json.decodeFromString<Map<String, String>>(metadataFile.readText())
            val indices = metadata.map { kotlin.io.path.Path(it.key) to kotlin.io.path.Path(it.value) }.toMap()
            if (indices.values.all { it.exists() }) indices else null
        } catch (e: Exception) {
            null
        }
    }

    override fun tryGetCachedMergedSources(
        dir: MergedSourcesDir,
        fresh: Boolean,
        forceDownload: Boolean
    ): MergedSourcesDir? {
        if (fresh || forceDownload) return null

        if (dir.storagePath.resolve(".dependencies.hash").exists() && dir.sources.exists()) {
            return dir
        }
        return null
    }

    context(progress: ProgressReporter)
    override suspend fun extractSources(
        dir: Path,
        dep: GradleDependency,
        forceDownload: Boolean,
        onFileExtracted: (suspend (String, ByteArray) -> Unit)?
    ) {
        val extractionMarker = dir.resolve(".extracted")
        if (!forceDownload && extractionMarker.exists()) {
            progress.report(1.0, 1.0, "Processing sources for ${dep.id}")
            return
        }

        try {
            ArchiveExtractor.extractInto(dir, requireNotNull(dep.sourcesFile), skipSingleFirstDir = true, writeFiles = true) { path, contentBytes ->
                onFileExtracted?.invoke(path, contentBytes)
            }
            extractionMarker.createFile()
        } catch (e: Exception) {
            logger.error("Failed to extract sources for $dep", e)
            throw e
        }
    }

    override fun syncTo(scopeLink: Path, dir: Path, dep: GradleDependency, targetSources: Path) {
        scopeLink.createParentDirectories()
        if (!FileUtils.createSymbolicLink(scopeLink, dir)) {
            logger.warn("Failed to create symbolic link or junction for {} to {}. Falling back to recursive copy. This will consume more disk space and time.", dep.id, targetSources)
            try {
                dir.copyToRecursively(scopeLink, followLinks = false, overwrite = true)
            } catch (e2: Exception) {
                logger.error("Failed to sync sources for $dep to $targetSources.", e2)
            }
        }
    }

    override fun prepareTargetSources(target: SourcesDir) {
        val targetSources = target.sources
        if (targetSources.exists()) {
            targetSources.deleteRecursively()
        }
        targetSources.createDirectories()
    }

    override fun isExtracted(dir: Path): Boolean {
        return dir.resolve(".extracted").exists()
    }

    override fun markExtracted(dir: Path) {
        dir.resolve(".extracted").createFile()
    }

    override fun deleteMergedSources(dir: MergedSourcesDir) {
        dir.storagePath.deleteRecursively()
    }

    override fun deleteSingleDependencySources(dir: Path) {
        dir.deleteRecursively()
    }
}
