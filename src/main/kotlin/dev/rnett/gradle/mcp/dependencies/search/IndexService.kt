package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.SourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.unorderedParallelForEach
import dev.rnett.gradle.mcp.utils.unorderedParallelMap
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
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
    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    suspend fun index(dependency: GradleDependency, sourceDir: Path): Index? {
        val filesFlow = flow {
            sourceDir.walk().filter { it.isRegularFile() }.forEach { file ->
                val relativePath = file.relativeTo(sourceDir).toString().replace('\\', '/')
                emit(IndexEntry(relativePath, file.readText()))
            }
        }
        return indexFiles(dependency, filesFlow)
    }

    context(progress: ProgressReporter)
    suspend fun indexFiles(dependency: GradleDependency, fileFlow: Flow<IndexEntry>): Index?

    /**
     * [includedDeps] is map of relative path in merged to the index
     */
    context(progress: ProgressReporter)
    suspend fun mergeIndices(sourcesDir: SourcesDir, includedDeps: Map<Path, Index>)

    suspend fun search(sourcesDir: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS): SearchResponse<RelativeSearchResult>

    suspend fun listPackageContents(sourcesDir: SourcesDir, packageName: String): PackageContents?
}

@OptIn(ExperimentalAtomicApi::class)
class DefaultIndexService(
    val environment: GradleMcpEnvironment
) : IndexService {
    private val LOGGER = LoggerFactory.getLogger(DefaultIndexService::class.java)
    private val providers = listOf(DeclarationSearch, FullTextSearch, GlobSearch)
    private val combinedIndexVersion = providers.joinToString("-") { "${it.name}:${it.indexVersion}" }
    private val markerFileName = ".indexed-${combinedIndexVersion.hashCode()}"
    private val indexDir = environment.cacheDir.resolve("source-indices")

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun indexFiles(dependency: GradleDependency, fileFlow: Flow<IndexEntry>): Index? {
        if (dependency.group == null) return null

        val dirName = dependency.sourcesFile?.nameWithoutExtension ?: "direct-${dependency.hashCode()}"
        val dir = indexDir.resolve(dependency.group).resolve(dirName)
        val lockFile = dir.resolve(".lock")
        val markerFile = dir.resolve(markerFileName)

        val cached = FileLockManager.withLock(lockFile, shared = true) {
            if (markerFile.exists()) Index(dir) else null
        }
        if (cached != null) return cached

        return FileLockManager.withLock(lockFile, shared = false) {
            if (markerFile.exists()) return@withLock Index(dir)

            try {
                val indexers = providers.map { provider ->
                    val providerDir = dir.resolve(provider.name)
                    if (providerDir.exists()) providerDir.deleteRecursively()
                    providerDir.createDirectories()
                    provider to provider.newIndexer(providerDir)
                }

                var count = 0
                try {
                    fileFlow.collect { entry ->
                        count++
                        progress.report(count.toDouble(), null, "Indexing ${entry.relativePath}")
                        indexers.forEach { (_, indexer) ->
                            indexer.indexFile(entry.relativePath, entry.content)
                        }
                    }
                    indexers.forEach { (_, indexer) ->
                        indexer.finish()
                    }

                    val counts = indexers.associate { (provider, indexer) ->
                        provider.name to indexer.documentCount
                    }
                    val metadataFile = dir.resolve(".metadata.json")
                    val tempMetadata = dir.resolve(".metadata.json.tmp")
                    tempMetadata.writeText(Json.encodeToString(counts))
                    java.nio.file.Files.move(tempMetadata, metadataFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                } finally {
                    indexers.forEach { (_, indexer) ->
                        indexer.close()
                    }
                }

                markerFile.createParentDirectories()
                markerFile.createFile()
                Index(dir)
            } catch (e: Exception) {
                LOGGER.error("Failed to index dependency $dependency", e)
                dir.deleteRecursively()
                null
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun mergeIndices(
        sourcesDir: SourcesDir,
        includedDeps: Map<Path, Index>
    ) {
        val lockFile = sourcesDir.index.resolve(".lock")
        val currentHash = combinedIndexVersion + "\n" + includedDeps.hashCode().toString()

        val upToDate = FileLockManager.withLock(lockFile, shared = true) {
            val hashFile = sourcesDir.index.resolve(".merged.hash")
            hashFile.exists() && hashFile.readText() == currentHash
        }
        if (upToDate) return

        FileLockManager.withLock(lockFile, shared = false) {
            val duration = measureTime {
                val dir = sourcesDir.index
                val hashFile = dir.resolve(".merged.hash")

                if (hashFile.exists() && hashFile.readText() == currentHash) {
                    return@measureTime
                }

                val includedIndices = includedDeps.mapValues { it.value.dir }
                try {
                    val mergeProgress = progress.withPhase("MERGING")
                    val prepProgress = progress.withPhase("PREPARING")

                    val totalPreps = includedIndices.size.toDouble()
                    val currentPreps = AtomicInt(0)

                    val dependencyMetadata = includedIndices.values.unorderedParallelMap(context = Dispatchers.IO) { depDir ->
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
                    }.toMap()

                    val providerCounts = providers.associateWith { provider ->
                        includedIndices.values.sumOf { depDir ->
                            dependencyMetadata[depDir]?.get(provider.name) ?: provider.countDocuments(depDir.resolve(provider.name))
                        }
                    }

                    val totalDocs = providerCounts.values.sum().toDouble()
                    val currentDocs = AtomicInt(0)

                    providers.unorderedParallelForEach(context = Dispatchers.IO) { provider ->
                        val providerTotalDocs = providerCounts[provider] ?: 0
                        val providerDir = dir.resolve(provider.name)
                        if (providerDir.exists()) {
                            providerDir.deleteRecursively()
                        }

                        providerDir.createDirectories()

                        val lastProviderDocs = AtomicInt(0)
                        val providerReporter = ProgressReporter { p, t, _ ->
                            val fraction = if (t != null && t > 0.0) p / t else p
                            val currentP = (fraction * providerTotalDocs).toInt()
                            val delta = currentP - lastProviderDocs.exchange(currentP)
                            if (delta != 0) {
                                mergeProgress.report(currentDocs.addAndFetch(delta).toDouble(), totalDocs, "Merging ${provider.name}")
                            }
                        }

                        provider.mergeIndices(includedIndices.entries.associate { it.value.resolve(provider.name) to it.key }, providerDir, providerReporter)
                    }
                    hashFile.createParentDirectories()
                    hashFile.writeText(currentHash)
                } catch (e: Exception) {
                    LOGGER.error("Failed to merge indices for ${sourcesDir.storagePath}", e)
                    dir.deleteRecursively()
                    throw e
                }
            }
            LOGGER.info("Merging indices for ${sourcesDir.storagePath} took $duration (${includedDeps.size} dependencies)")
        }
    }

    override suspend fun search(
        sourcesDir: SourcesDir,
        provider: SearchProvider,
        query: String,
        pagination: PaginationInput
    ): SearchResponse<RelativeSearchResult> {
        val (response, duration) = measureTimedValue {
            val providerIndexDir = sourcesDir.index.resolve(provider.name)
            if (!sourcesDir.index.exists()) {
                throw IllegalStateException("Index not found in ${sourcesDir.index}. Did you enable indexing?")
            }
            if (!providerIndexDir.exists()) {
                throw IllegalStateException("Index for provider ${provider.name} not found in ${sourcesDir.index}")
            }
            provider.search(providerIndexDir, query, pagination)
        }
        val res = response
        LOGGER.info("Search using ${provider.name} for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) in ${sourcesDir.storagePath} took $duration (${res.results.size} results)")
        return res
    }

    override suspend fun listPackageContents(sourcesDir: SourcesDir, packageName: String): PackageContents? {
        val providerIndexDir = sourcesDir.index.resolve(DeclarationSearch.name)
        if (!providerIndexDir.exists()) return null
        return DeclarationSearch.listPackageContents(providerIndexDir, packageName)
    }
}
