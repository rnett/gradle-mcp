package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.hash
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.FileUtils
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.time.measureTime

/**
 * Manages the underlying search indices (Lucene/etc) for both individual dependencies
 * and project-level merged views. Handles indexing, searching, and metadata persistence.
 */
interface IndexService {
    /**
     * Checks if a dependency is already indexed for the given provider.
     */
    suspend fun isIndexed(dependency: GradleDependency, provider: SearchProvider): Boolean

    /**
     * Retrieves the index for a single dependency if it exists.
     */
    suspend fun getIndex(dependency: GradleDependency, provider: SearchProvider): Index?

    /**
     * Indexes a stream of files for a specific dependency and search provider.
     *
     * This operation is performed under an exclusive lock on the dependency's index directory.
     * If the index already exists and [forceIndex] is false, the operation returns immediately.
     */
    context(progress: ProgressReporter)
    suspend fun indexFiles(dependency: GradleDependency, fileFlow: Flow<IndexEntry>, provider: SearchProvider, forceIndex: Boolean = false): Index?

    /**
     * Merges multiple individual dependency indices into a project-level merged index.
     *
     * This operation is performed under an exclusive lock on the project's merged index directory.
     * It uses a [withLock] callback to acquire shared locks on individual dependency indices while
     * they are being read, ensuring they aren't deleted or modified mid-merge.
     *
     * @param sourcesDir The project-specific sources directory where the merged index will be stored.
     * @param includedDeps List of relative paths in the merged project to their source indices. 
     *                     Uses a list to handle duplicate dependencies across different configurations.
     * @param provider The search provider whose indices are being merged.
     * @param expectedDepsHash A hash representing the exact set of dependencies included in this merge.
     */
    context(progress: ProgressReporter)
    suspend fun mergeIndices(sourcesDir: SourcesDir, includedDeps: List<Pair<Path, Index>>, provider: SearchProvider, expectedDepsHash: String)

    /**
     * Performs a search against the project's merged index.
     *
     * Acquisition of a shared lock on the sources directory is the responsibility of the caller or delegated to this service.
     */
    suspend fun search(sourcesDir: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult>

    /**
     * Checks if a merged index is present and up-to-date for the given provider and dependency hash.
     *
     * @param sourcesDir The sources directory containing the merged indices.
     * @param provider The search provider to check.
     * @param expectedDepsHash The expected dependency hash for this index.
     * @return True if the index is present and up-to-date.
     */
    suspend fun isMergeUpToDate(sourcesDir: SourcesDir, provider: SearchProvider, expectedDepsHash: String): Boolean

    suspend fun listPackageContents(sourcesDir: SourcesDir, packageName: String): PackageContents?
}

@OptIn(ExperimentalAtomicApi::class)
class DefaultIndexService(
    val environment: GradleMcpEnvironment
) : IndexService {
    private val logger = LoggerFactory.getLogger(DefaultIndexService::class.java)
    private val indexDir = environment.cacheDir.resolve("source-indices")

    private data class ResolvedIndex(val dir: Path, val lockFile: Path, val dirName: String)

    private fun getLockFile(dir: Path): Path {
        val relative = try {
            dir.relativeTo(indexDir).toString().replace('\\', '/')
        } catch (e: Exception) {
            null
        }
        if (relative != null) {
            return environment.dependencyLockFile(relative)
        }

        val lockName = dir.toAbsolutePath().toString().toByteArray().hash().take(12)
        return environment.cacheDir.resolve(".locks/source-indices").resolve("ext-$lockName.lock")
    }

    private fun resolveDependencyIndex(dependency: GradleDependency, provider: SearchProvider): ResolvedIndex? {
        if (dependency.group == null) return null
        val dirName = dependency.sourcesFile?.nameWithoutExtension ?: "direct-${dependency.hashCode()}"
        val dir = indexDir.resolve(dependency.group).resolve(dirName)
        val lockFile = getLockFile(dir)
        return ResolvedIndex(dir, lockFile, dirName)
    }

    override suspend fun isIndexed(dependency: GradleDependency, provider: SearchProvider): Boolean {
        val resolved = resolveDependencyIndex(dependency, provider) ?: return false
        return FileLockManager.withLock(resolved.lockFile, shared = true) {
            resolved.dir.resolve(provider.markerFileName).exists()
        }
    }

    override suspend fun getIndex(dependency: GradleDependency, provider: SearchProvider): Index? {
        val resolved = resolveDependencyIndex(dependency, provider) ?: return null
        return FileLockManager.withLock(resolved.lockFile, shared = true) {
            if (resolved.dir.resolve(provider.markerFileName).exists()) Index(resolved.dir) else null
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun indexFiles(dependency: GradleDependency, fileFlow: Flow<IndexEntry>, provider: SearchProvider, forceIndex: Boolean): Index? {
        val resolved = resolveDependencyIndex(dependency, provider) ?: return null
        val dir = resolved.dir
        val lockFile = resolved.lockFile

        if (!forceIndex) {
            val cached = FileLockManager.withLock(lockFile, shared = true) {
                if (dir.resolve(provider.markerFileName).exists()) Index(dir) else null
            }
            if (cached != null) {
                fileFlow.collect { } // Consume the flow to avoid resource leaks
                return cached
            }
        }

        return FileLockManager.withLock(lockFile, shared = false) {
            val markerFile = dir.resolve(provider.markerFileName)
            if (!forceIndex && markerFile.exists()) {
                fileFlow.collect { } // Consume the flow
                return@withLock Index(dir)
            }

            // If we get here, we are re-indexing or index is missing/stale.
            cleanupOldIndices(dir, provider)

            val providerDir = dir.resolve(provider.name)
            if (providerDir.exists()) providerDir.deleteRecursively()
            providerDir.createDirectories()

            val indexer = provider.newIndexer(providerDir)
            try {
                dir.createDirectories()

                var count = 0
                try {
                    fileFlow.collect { entry ->
                        count++
                        // Intentional per-file reporting; throttling is handled by the tool context or ParallelProgressTracker
                        progress.report(count.toDouble(), null, "Indexing ${entry.relativePath}")
                        indexer.indexFile(entry.relativePath, entry.content)
                    }
                    indexer.finish()

                    // Update metadata
                    val metadataFile = dir.resolve(".metadata.json")

                    val existingMetadata: Map<String, Int> = if (metadataFile.exists()) {
                        try {
                            Json.decodeFromString(metadataFile.readText())
                        } catch (e: Exception) {
                            emptyMap()
                        }
                    } else emptyMap()

                    val mutableCounts = existingMetadata.toMutableMap()
                    mutableCounts[provider.name] = indexer.documentCount

                    val tmpFile = metadataFile.resolveSibling(".metadata.json.tmp")
                    tmpFile.writeText(Json.encodeToString(mutableCounts))
                    Files.move(tmpFile, metadataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    indexer.close()
                }

                markerFile.createParentDirectories()
                markerFile.createFile()
                Index(dir)
            } catch (e: Exception) {
                logger.error("Failed to index dependency $dependency for provider ${provider.name}", e)
                providerDir.deleteRecursively()
                null
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun mergeIndices(
        sourcesDir: SourcesDir,
        includedDeps: List<Pair<Path, Index>>,
        provider: SearchProvider,
        expectedDepsHash: String
    ) {
        val duration = measureTime {
            val dir = sourcesDir.index
            try {
                val mergeProgress = progress.withPhase("MERGING")

                val providerDir = dir.resolve(provider.name)
                val hashFile = providerDir.resolve(".merged.hash")
                val currentHash = "${provider.indexVersion}\n$expectedDepsHash"

                if (hashFile.exists()) {
                    try {
                        val onDisk = hashFile.readText()
                        if (onDisk == currentHash) {
                            return@measureTime
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to read hash file at $hashFile, re-merging", e)
                    }
                }

                // Use a temporary directory for atomic merge
                val tmpMergeDir = dir.resolve("${provider.name}.tmp")
                if (tmpMergeDir.exists()) tmpMergeDir.deleteRecursively()
                tmpMergeDir.createDirectories()

                val indexDirs = includedDeps.associate { it.second.dir.resolve(provider.name) to it.first }
                val totalDeps = indexDirs.size.toDouble()
                val currentDeps = AtomicInt(0)
                val providerReporter = ProgressReporter { _, _, _ ->
                    val completed = currentDeps.addAndFetch(1)
                    mergeProgress.report(completed.toDouble(), totalDeps, "Merging indices")
                }

                val uniqueDepIndexDirs = indexDirs.keys.map { it.parent }.distinct()
                val providerDirsToLockFiles = uniqueDepIndexDirs.associate {
                    it.resolve(provider.name) to getLockFile(it)
                }

                val lockFunction: suspend (Path, suspend () -> Unit) -> Unit = { idxDir, action ->
                    val dependencyLockFile = providerDirsToLockFiles[idxDir] ?: error("Unknown lock file for $idxDir")
                    FileLockManager.withLock(dependencyLockFile, shared = true) {
                        action()
                    }
                }

                with(providerReporter) {
                    provider.mergeIndices(indexDirs, tmpMergeDir, lockFunction)
                }

                // Write hash into the tmp directory before moving
                tmpMergeDir.resolve(".merged.hash").writeText(currentHash)

                FileUtils.atomicReplaceDirectory(tmpMergeDir, providerDir)

            } catch (e: Exception) {
                logger.error("Failed to merge index for provider ${provider.name} in ${sourcesDir.sources}", e)
                throw e
            }
        }
        logger.info("Merging index for ${provider.name} in ${sourcesDir.sources} took $duration (${includedDeps.size} dependencies)")
    }

    @OptIn(ExperimentalPathApi::class)
    private fun cleanupOldIndices(dir: Path, provider: SearchProvider) {
        val currentVersion = provider.indexVersion
        val prefix = ".indexed-${provider.name}-"

        if (!dir.exists()) return

        dir.listDirectoryEntries().forEach { entry ->
            val name = entry.fileName.toString()
            if (name.startsWith(prefix)) {
                val versionStr = name.removePrefix(prefix)
                val version = versionStr.toIntOrNull()
                if (version != null) {
                    logger.info("Cleaning up index marker version $version for ${provider.name} in $dir")
                    entry.deleteRecursively()
                }
            }
        }

        // Also cleanup old index directories
        val indexDirPrefix = "${provider.name}-index-v"
        dir.listDirectoryEntries().forEach { entry ->
            val name = entry.fileName.toString()
            if (name.startsWith(indexDirPrefix)) {
                val versionStr = name.removePrefix(indexDirPrefix)
                val version = versionStr.toIntOrNull()
                if (version != null && version != currentVersion) {
                    logger.info("Cleaning up old index directory version $version for ${provider.name} in $dir")
                    entry.deleteRecursively()
                }
            }
        }
    }

    override suspend fun isMergeUpToDate(sourcesDir: SourcesDir, provider: SearchProvider, expectedDepsHash: String): Boolean {
        val lockFile = sourcesDir.lockFile
        return FileLockManager.withLock(lockFile, shared = true) {
            checkMergeState(sourcesDir.index, provider, expectedDepsHash)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun checkMergeState(indexDir: Path, provider: SearchProvider, expectedDepsHash: String): Boolean {
        val providerDir = indexDir.resolve(provider.name)
        val hashFile = providerDir.resolve(".merged.hash")

        if (!providerDir.exists() || !hashFile.exists()) return false

        // Verify the directory contains actual index files, not just the hash
        val hasIndexFiles = try {
            providerDir.listDirectoryEntries().any { it.isRegularFile() && it.fileName.toString() != ".merged.hash" }
        } catch (e: Exception) {
            false
        }

        if (!hasIndexFiles) return false

        return try {
            val content = hashFile.readText()
            content == "${provider.indexVersion}\n$expectedDepsHash"
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun search(sourcesDir: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> {
        return provider.search(sourcesDir.index.resolve(provider.name), query, pagination)
    }

    override suspend fun listPackageContents(sourcesDir: SourcesDir, packageName: String): PackageContents? {
        val providerIndexDir = sourcesDir.index.resolve(DeclarationSearch.name)
        if (!providerIndexDir.exists()) return null
        return DeclarationSearch.listPackageContents(providerIndexDir, packageName)
    }
}
