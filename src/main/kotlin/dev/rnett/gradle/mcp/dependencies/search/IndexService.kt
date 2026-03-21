package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.unorderedParallelMap
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@JvmInline
value class Index(val dir: Path)

interface IndexService {
    /**
     * Indexes a directory of source files for a specific dependency using the given provider.
     * This is a convenience method that creates a [Flow] of [IndexEntry] from the directory.
     *
     * @param dependency The dependency being indexed.
     * @param sourceDir The local directory containing extracted source files.
     * @param provider The search provider to use.
     * @return The resulting [Index] or null if indexing failed.
     */
    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    suspend fun index(dependency: GradleDependency, sourceDir: Path, provider: SearchProvider): Index? {
        val filesFlow: Flow<IndexEntry> = flow {
            sourceDir.walk().filter { it.isRegularFile() }.forEach { file ->
                val relativePath = file.relativeTo(sourceDir).toString().replace('\\', '/')
                emit(IndexEntry(relativePath, file.readText()))
            }
        }
        return indexFiles(dependency, filesFlow, provider)
    }

    /**
     * Indexes a stream of files for a specific dependency using the given provider.
     *
     * This method handles per-dependency locking and cache invalidation. It acquires an exclusive
     * lock on the provider-specific lock file. If [forceIndex] is true or the marker file is missing,
     * it immediately deletes any existing marker file to prevent cache corruption before starting
     * the indexing process.
     *
     * @param dependency The dependency being indexed.
     * @param fileFlow A stream of files to index. MUST be fully consumed even on cache hits.
     * @param provider The search provider to use.
     * @param forceIndex If true, triggers a re-index even if a valid cache exists.
     * @return The resulting [Index] or null if indexing failed.
     */
    context(progress: ProgressReporter)
    suspend fun indexFiles(dependency: GradleDependency, fileFlow: Flow<IndexEntry>, provider: SearchProvider, forceIndex: Boolean = false): Index?

    /**
     * Checks if a specific dependency is already indexed for the given provider.
     * This can be used to avoid creating expensive file flows on cache hits.
     *
     * @param dependency The dependency to check.
     * @param provider The search provider to check.
     * @return True if a valid index exists for this dependency and provider.
     */
    suspend fun isIndexed(dependency: GradleDependency, provider: SearchProvider): Boolean

    /**
     * Retrieves the existing index for a dependency and provider, if it exists.
     *
     * @param dependency The dependency to retrieve.
     * @param provider The search provider to retrieve.
     * @return The [Index] or null if not found.
     */
    suspend fun getIndex(dependency: GradleDependency, provider: SearchProvider): Index?

    /**
     * Merges multiple individual dependency indices into a project-level merged index.
     *
     * This operation is performed under an exclusive lock on the project's merged index directory.
     * It uses a [withLock] callback to acquire shared locks on individual dependency indices while
     * they are being read, ensuring they aren't deleted or modified mid-merge.
     *
     * @param sourcesDir The project-specific sources directory where the merged index will be stored.
     * @param includedDeps Map of relative paths in the merged project to their source indices.
     * @param provider The search provider whose indices are being merged.
     * @param expectedDepsHash A hash representing the exact set of dependencies included in this merge.
     */
    context(progress: ProgressReporter)
    suspend fun mergeIndices(sourcesDir: SourcesDir, includedDeps: Map<Path, Index>, provider: SearchProvider, expectedDepsHash: String)

    /**
     * Performs a search against the project's merged index.
     *
     * Acquires a shared lock on the project's index directory to ensure no merge is in progress.
     *
     * @param sourcesDir The sources directory containing the merged index.
     * @param provider The search provider to use.
     * @param query The search query string.
     * @param pagination Optional pagination parameters.
     * @return A [SearchResponse] containing the search results and optional interpreted query/error.
     */
    suspend fun search(sourcesDir: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS): SearchResponse<RelativeSearchResult>

    /**
     * Checks if a merged index is up-to-date for the given provider and dependency set.
     *
     * Performed in two stages:
     * 1. Fast shared-lock check of the `.merged.hash` file.
     * 2. Brief exclusive-lock check to ensure no atomic move is currently replacing the directory.
     *
     * @param indexDir The directory containing the merged indices.
     * @param provider The search provider to check.
     * @param expectedDepsHash The expected dependency hash for this index.
     * @return True if the index is present and up-to-date.
     */
    suspend fun isMergeUpToDate(indexDir: Path, provider: SearchProvider, expectedDepsHash: String): Boolean

    suspend fun listPackageContents(sourcesDir: SourcesDir, packageName: String): PackageContents?
}

@OptIn(ExperimentalAtomicApi::class)
class DefaultIndexService(
    val environment: GradleMcpEnvironment
) : IndexService {
    private val logger = LoggerFactory.getLogger(DefaultIndexService::class.java)
    private val indexDir = environment.cacheDir.resolve("source-indices")

    private data class ResolvedIndex(val dir: Path, val lockFile: Path, val dirName: String)

    private fun resolveDependencyIndex(dependency: GradleDependency, provider: SearchProvider): ResolvedIndex? {
        if (dependency.group == null) return null
        val dirName = dependency.sourcesFile?.nameWithoutExtension ?: "direct-${dependency.hashCode()}"
        val dir = indexDir.resolve(dependency.group).resolve(dirName)
        val lockFile = environment.cacheDir.resolve(".locks/source-indices").resolve(dependency.group).resolve("$dirName-${provider.name}.lock")
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
        var consumed = false
        try {
            val resolved = resolveDependencyIndex(dependency, provider) ?: return null
            val dir = resolved.dir
            val lockFile = resolved.lockFile
            val dirName = resolved.dirName

            if (!forceIndex) {
                val cached = FileLockManager.withLock(lockFile, shared = true) {
                    if (dir.resolve(provider.markerFileName).exists()) Index(dir) else null
                }
                if (cached != null) {
                    fileFlow.collect { } // Must consume the flow
                    consumed = true
                    return cached
                }
            }

            return FileLockManager.withLock(lockFile, shared = false) {
                val markerFile = dir.resolve(provider.markerFileName)
                if (!forceIndex && markerFile.exists()) {
                    fileFlow.collect { } // Must consume the flow
                    consumed = true
                    return@withLock Index(dir)
                }

                // If we get here, we are re-indexing. Ensure the marker is gone so a failure
                // doesn't leave a corrupted cache state that looks valid to subsequent runs.
                if (markerFile.exists()) {
                    markerFile.toFile().delete()
                }

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
                            progress.report(count.toDouble(), null, "Indexing ${entry.relativePath}")
                            indexer.indexFile(entry.relativePath, entry.content)
                        }
                        consumed = true
                        indexer.finish()

                        // Update metadata
                        val metadataFile = dir.resolve(".metadata.json")
                        val metadataLockFile = environment.cacheDir.resolve(".locks/source-indices").resolve(dependency.group).resolve("$dirName-metadata.lock")
                        FileLockManager.withLock(metadataLockFile) {
                            val existingMetadata: Map<String, Int> = if (metadataFile.exists()) {
                                try {
                                    Json.decodeFromString(metadataFile.readText())
                                } catch (e: Exception) {
                                    emptyMap()
                                }
                            } else emptyMap()

                            val counts = existingMetadata.toMutableMap()
                            counts[provider.name] = indexer.documentCount

                            val tmpFile = metadataFile.resolveSibling(".metadata.json.tmp")
                            tmpFile.writeText(Json.encodeToString(counts))
                            Files.move(tmpFile, metadataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        }
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
        } finally {
            if (!consumed) {
                fileFlow.collect { } // Must consume the flow
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun mergeIndices(
        sourcesDir: SourcesDir,
        includedDeps: Map<Path, Index>,
        provider: SearchProvider,
        expectedDepsHash: String
    ) {
        val lockFile = sourcesDir.index.resolve(".lock")

        FileLockManager.withLock(lockFile, shared = false) {
            val duration = measureTime {
                val dir = sourcesDir.index
                val includedIndices = includedDeps.mapValues { it.value.dir }
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

                    val dependencyMetadata = loadDependencyMetadata(includedIndices)

                    val totalDocs = includedIndices.values.sumOf {
                        dependencyMetadata[it]?.get(provider.name) ?: provider.countDocuments(it.resolve(provider.name))
                    }.toDouble()

                    // Use a temporary directory for atomic merge
                    val tmpMergeDir = dir.resolve("${provider.name}.tmp")
                    if (tmpMergeDir.exists()) tmpMergeDir.deleteRecursively()
                    tmpMergeDir.createDirectories()

                    val currentDocs = AtomicInt(0)
                    val providerReporter = ProgressReporter { p, t, _ ->
                        val fraction = if (t != null && t > 0.0) p / t else p
                        val currentP = (fraction * totalDocs).toInt()
                        val delta = currentP - currentDocs.exchange(currentP)
                        if (delta != 0) {
                            mergeProgress.report(currentDocs.load().toDouble(), totalDocs, "Merging indices")
                        }
                    }

                    val lockFunction: suspend (Path, suspend () -> Unit) -> Unit = { idxDir, action ->
                        val group = idxDir.parent.parent.fileName.toString()
                        val dirName = idxDir.parent.fileName.toString()
                        val dependencyLockFile = environment.cacheDir.resolve(".locks/source-indices").resolve(group).resolve("$dirName-${provider.name}.lock")
                        FileLockManager.withLock(dependencyLockFile, shared = true) {
                            action()
                        }
                    }

                    provider.mergeIndices(includedIndices.entries.associate { it.value.resolve(provider.name) to it.key }, tmpMergeDir, providerReporter, lockFunction)

                    // Write hash into the tmp directory before moving
                    tmpMergeDir.resolve(".merged.hash").writeText(currentHash)

                    if (providerDir.exists()) {
                        providerDir.deleteRecursively()
                    }

                    Files.move(tmpMergeDir, providerDir, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

                } catch (e: Exception) {
                    logger.error("Failed to merge index for provider ${provider.name} in ${sourcesDir.sources}", e)
                    throw e
                }
            }
            logger.info("Merging index for ${provider.name} in ${sourcesDir.sources} took $duration (${includedDeps.size} dependencies)")
        }
    }

    override suspend fun isMergeUpToDate(indexDir: Path, provider: SearchProvider, expectedDepsHash: String): Boolean {
        val lockFile = indexDir.resolve(".lock")
        val isUpToDate = FileLockManager.withLock(lockFile, shared = true) {
            checkMergeState(indexDir, provider, expectedDepsHash)
        }

        if (!isUpToDate) return false

        // If it looks up to date under a shared lock, acquire an exclusive lock briefly
        // to ensure no other process is currently replacing the directory (atomic move).
        // This is necessary because ATOMIC_MOVE might be in progress even if the hash file
        // appears valid, and we want to ensure we're seeing a stable, fully-moved directory.
        return FileLockManager.withLock(lockFile, shared = false) {
            checkMergeState(indexDir, provider, expectedDepsHash)
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

    context(progress: ProgressReporter)
    private suspend fun loadDependencyMetadata(includedIndices: Map<Path, Path>): Map<Path, Map<String, Int>?> {
        val prepProgress = progress.withPhase("PREPARING")
        val totalPreps = includedIndices.size.toDouble()
        val currentPreps = AtomicInt(0)

        return includedIndices.values.unorderedParallelMap(context = Dispatchers.IO) { depDir ->
            val group = depDir.parent.fileName.toString()
            val dirName = depDir.fileName.toString()
            val lockFile = environment.cacheDir.resolve(".locks/source-indices").resolve(group).resolve("$dirName-metadata.lock")
            FileLockManager.withLock(lockFile, shared = true) {
                val metadataFile = depDir.resolve(".metadata.json")
                val metadata: Map<String, Int>? = if (metadataFile.exists()) {
                    try {
                        Json.decodeFromString<Map<String, Int>>(metadataFile.readText())
                    } catch (e: Exception) {
                        null
                    }
                } else null
                prepProgress.report(currentPreps.addAndFetch(1).toDouble(), totalPreps, "Reading metadata")
                depDir to metadata
            }
        }.toMap()
    }

    override suspend fun search(
        sourcesDir: SourcesDir,
        provider: SearchProvider,
        query: String,
        pagination: PaginationInput
    ): SearchResponse<RelativeSearchResult> {
        val lockFile = sourcesDir.index.resolve(".lock")
        return FileLockManager.withLock(lockFile, shared = true) {
            val providerDir = sourcesDir.index.resolve(provider.name)
            if (!sourcesDir.index.exists()) return@withLock SearchResponse(emptyList(), error = "Index not found in ${sourcesDir.index}. Did you enable indexing?")
            if (!providerDir.exists()) return@withLock SearchResponse(emptyList(), error = "Index for provider ${provider.name} not found in ${sourcesDir.index}")

            val (response, duration) = measureTimedValue {
                provider.search(providerDir, query, pagination)
            }
            logger.info("Search using ${provider.name} for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) in ${sourcesDir.sources} took $duration (${response.results.size} results)")
            response
        }
    }

    override suspend fun listPackageContents(sourcesDir: SourcesDir, packageName: String): PackageContents? {
        val lockFile = sourcesDir.index.resolve(".lock")
        return FileLockManager.withLock(lockFile, shared = true) {
            val providerIndexDir = sourcesDir.index.resolve(DeclarationSearch.name)
            if (!providerIndexDir.exists()) return@withLock null
            DeclarationSearch.listPackageContents(providerIndexDir, packageName)
        }
    }
}
