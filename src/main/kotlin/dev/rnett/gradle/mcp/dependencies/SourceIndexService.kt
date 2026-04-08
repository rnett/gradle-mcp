package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.NestedPackageContents
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.dependencies.search.SubPackageContents
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

    /**
     * Lists a 2-level nested view of a package for richer navigation output.
     * Expands each direct sub-package one additional level. Capped at 30 sub-packages.
     */
    suspend fun listNestedPackageContents(sources: SourcesDir, packageName: String): NestedPackageContents?
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

    override suspend fun listNestedPackageContents(sources: SourcesDir, packageName: String): NestedPackageContents? {
        val root = indexService.listPackageContents(sources, packageName) ?: return null
        if (root.subPackages.size > 30) {
            return NestedPackageContents(
                symbols = root.symbols,
                subPackages = root.subPackages.map { SubPackageContents(name = it, symbols = emptyList(), subPackages = emptyList()) },
                tooManySubPackages = true
            )
        }
        val expandedSubs = root.subPackages.map { subName ->
            val fullSubPackage = if (packageName.isEmpty()) subName else "$packageName.$subName"
            val subContents = indexService.listPackageContents(sources, fullSubPackage)
            SubPackageContents(
                name = subName,
                symbols = subContents?.symbols ?: emptyList(),
                subPackages = subContents?.subPackages ?: emptyList()
            )
        }
        return NestedPackageContents(symbols = root.symbols, subPackages = expandedSubs)
    }
}
