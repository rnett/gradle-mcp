package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.dependencies.search.toSearchResults
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.withMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Manages the coordination between source files and the underlying search index (Lucene/etc).
 * Responsible for directing indexing operations during source extraction, merging dependency-specific
 * indices into project-level indices, and delegating search queries.
 */
interface SourceIndexService {
    /**
     * Indexes a single dependency's sources from a provided flow of [IndexEntry].
     */
    context(progress: ProgressReporter)
    suspend fun indexDependency(
        dep: GradleDependency,
        entries: Flow<IndexEntry>,
        providerToIndex: SearchProvider,
        forceIndex: Boolean = false
    ): Index?

    /**
     * Ensures that a dependency is indexed, triggering a full extraction-directory walk if necessary.
     */
    context(progress: ProgressReporter)
    suspend fun ensureIndexed(
        dep: GradleDependency,
        dir: Path,
        forceDownload: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): Index?

    /**
     * Merges multiple dependency-level indices into a project-level merged index.
     */
    context(progress: ProgressReporter)
    suspend fun mergeIndices(
        target: MergedSourcesDir,
        indices: List<Pair<Path, Index>>,
        providerToIndex: SearchProvider,
        currentHash: String
    )

    /**
     * Checks if a merged index is up-to-date for the given provider and dependency hash.
     */
    suspend fun isMergeUpToDate(sourcesDir: SourcesDir, providerToIndex: SearchProvider, currentHash: String): Boolean

    /**
     * Ensures a merged index is present and up-to-date, performing a merge if necessary.
     */
    context(progress: ProgressReporter)
    suspend fun ensureMergeUpToDate(
        target: MergedSourcesDir,
        providerToIndex: SearchProvider,
        currentHash: String
    ): Boolean

    /**
     * Performs a search across the specified sources using the provided query and pagination.
     */
    suspend fun search(
        sources: SourcesDir,
        provider: SearchProvider,
        query: String,
        pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS
    ): SearchResponse<SearchResult>

    /**
     * Lists the contents of a package within the specified sources.
     */
    suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents?
}

@OptIn(ExperimentalPathApi::class)
class DefaultSourceIndexService(
    private val indexService: IndexService,
    private val storageService: SourceStorageService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SourceIndexService {
    private val logger = LoggerFactory.getLogger(DefaultSourceIndexService::class.java)

    private fun mapFileToIndexEntry(dir: Path, file: Path, relativePrefix: Path): IndexEntry? {
        val relativePath = file.relativeTo(dir)
        val fullRelativePath = storageService.normalizeRelativePath(relativePrefix, relativePath)
        val ext = fullRelativePath.substringAfterLast('.', "")
        return if (ext in SearchProvider.SOURCE_EXTENSIONS) {
            IndexEntry(fullRelativePath, file.readText())
        } else null
    }

    context(progress: ProgressReporter)
    override suspend fun indexDependency(
        dep: GradleDependency,
        entries: Flow<IndexEntry>,
        providerToIndex: SearchProvider,
        forceIndex: Boolean
    ): Index? {
        val currentIndexingProgress = progress.withMessage { "Indexing sources for ${dep.id}" }
        with(currentIndexingProgress) {
            indexService.indexFiles(dep, entries, providerToIndex, forceIndex = forceIndex)
        }
        return indexService.getIndex(dep, providerToIndex)
    }

    context(progress: ProgressReporter)
    override suspend fun ensureIndexed(
        dep: GradleDependency,
        dir: Path,
        forceDownload: Boolean,
        providerToIndex: SearchProvider?
    ): Index? {
        if (providerToIndex == null) return null

        if (!forceDownload && indexService.isIndexed(dep, providerToIndex)) {
            return indexService.getIndex(dep, providerToIndex)
        }

        val currentIndexingProgress = progress.withMessage { "Indexing sources for ${dep.id}" }

        return with(currentIndexingProgress) {
            val relativePrefix = dep.relativePrefix?.let { kotlin.io.path.Path(it) } ?: return@with null

            indexService.indexFiles(dep, flow {
                dir.walk().filter { it.isRegularFile() && it.fileName.toString() != ".extracted" }.forEach { file ->
                    mapFileToIndexEntry(dir, file, relativePrefix)?.let { emit(it) }
                }
            }, providerToIndex, forceIndex = forceDownload)
        }
    }

    context(progress: ProgressReporter)
    override suspend fun mergeIndices(
        target: MergedSourcesDir,
        indices: List<Pair<Path, Index>>,
        providerToIndex: SearchProvider,
        currentHash: String
    ) {
        indexService.mergeIndices(target, indices, providerToIndex, currentHash)
    }

    override suspend fun isMergeUpToDate(sourcesDir: SourcesDir, providerToIndex: SearchProvider, currentHash: String): Boolean {
        return indexService.isMergeUpToDate(sourcesDir, providerToIndex, currentHash)
    }

    context(progress: ProgressReporter)
    override suspend fun ensureMergeUpToDate(
        target: MergedSourcesDir,
        providerToIndex: SearchProvider,
        currentHash: String
    ): Boolean {
        // Fast path: check if up-to-date under shared lock
        val isUpToDate = isMergeUpToDate(target, providerToIndex, currentHash)
        if (isUpToDate) return true

        // Slow path: update under exclusive lock
        return FileLockManager.withLock(target.lockFile, shared = false) {
            // Re-check under exclusive lock
            if (!isMergeUpToDate(target, providerToIndex, currentHash)) {
                val cachedIndices = storageService.loadCachedIndices(target) ?: return@withLock false
                mergeIndices(target, cachedIndices.toList().map { it.first to Index(it.second) }, providerToIndex, currentHash)
            }
            true
        }
    }

    override suspend fun search(
        sources: SourcesDir,
        provider: SearchProvider,
        query: String,
        pagination: PaginationInput
    ): SearchResponse<SearchResult> {
        val lockFile = sources.lockFile
        return FileLockManager.withLock(lockFile, shared = true) {
            val response = indexService.search(sources, provider, query, pagination)
            SearchResponse(
                results = response.results.toSearchResults(sources.rootForSearch),
                interpretedQuery = response.interpretedQuery,
                error = response.error
            )
        }
    }

    override suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents? {
        val lockFile = sources.lockFile
        return FileLockManager.withLock(lockFile, shared = true) {
            indexService.listPackageContents(sources, packageName)
        }
    }
}
