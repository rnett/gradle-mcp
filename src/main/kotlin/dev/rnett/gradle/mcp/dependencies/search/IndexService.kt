package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.SourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.nio.file.Path
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

                    val totalDocs = providers.sumOf { provider ->
                        includedIndices.keys.sumOf { provider.countDocuments(it.resolve(provider.name)) }
                    }
                    var completedDocs = 0

                    providers.forEach { provider ->
                        val providerTotalDocs = includedIndices.keys.sumOf { provider.countDocuments(it.resolve(provider.name)) }
                        val providerDir = dir.resolve(provider.name)
                        if (providerDir.exists()) {
                            providerDir.deleteRecursively()
                        }

                        providerDir.createDirectories()

                        val providerReporter = ProgressReporter { p, t, _ ->
                            val fraction = if (t != null && t > 0.0) p / t else p
                            val current = completedDocs + (fraction * providerTotalDocs).toInt()
                            mergeProgress.report(current.toDouble(), totalDocs.toDouble(), "Merging indices")
                        }

                        provider.mergeIndices(includedIndices.entries.associate { it.value.resolve(provider.name) to it.key }, providerDir, providerReporter)
                        completedDocs += providerTotalDocs
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
