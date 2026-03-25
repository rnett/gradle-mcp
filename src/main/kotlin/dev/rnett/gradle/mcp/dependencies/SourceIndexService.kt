package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.dependencies.search.toSearchResults
import dev.rnett.gradle.mcp.tools.PaginationInput

/**
 * High-level service for executing searches across resolved dependency views.
 */
interface SourceIndexService {
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

class DefaultSourceIndexService(
    private val indexService: IndexService
) : SourceIndexService {

    override suspend fun search(
        sources: SourcesDir,
        provider: SearchProvider,
        query: String,
        pagination: PaginationInput
    ): SearchResponse<SearchResult> {
        val response = indexService.search(sources, provider, query, pagination)
        return SearchResponse(
            results = response.results.toSearchResults(sources.rootForSearch),
            interpretedQuery = response.interpretedQuery,
            error = response.error,
            hasMore = response.hasMore
        )
    }

    override suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents? {
        return indexService.listPackageContents(sources, packageName)
    }
}
