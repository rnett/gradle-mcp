package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesDir
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

@JvmInline
value class Index(val dir: Path)

interface IndexService {
    suspend fun index(dependency: GradleDependency, sourceDir: Path): Index?

    /**
     * [includedDeps] is map of relative path in merged to the index
     */
    suspend fun mergeIndices(sourcesDir: SourcesDir, includedDeps: Map<Path, Index>)

    suspend fun search(sourcesDir: SourcesDir, provider: SearchProvider, query: String): List<RelativeSearchResult>
}

class DefaultIndexService(
    val environment: GradleMcpEnvironment
) : IndexService {
    private val providers = listOf(SymbolSearch, FullTextSearch)
    private val indexDir = environment.cacheDir.resolve("source-indices")
    override suspend fun index(dependency: GradleDependency, sourceDir: Path): Index? {
        if (dependency.sourcesFile == null) return null
        if (dependency.group == null) return null

        val dir = indexDir.resolve(dependency.group).resolve(dependency.sourcesFile.nameWithoutExtension)

        providers.forEach { provider ->
            val providerDir = dir.resolve(provider.name)
            if (providerDir.exists()) {
                return@forEach
            }

            providerDir.createDirectories()
            provider.index(sourceDir, providerDir)
        }
        return Index(dir)
    }

    override suspend fun mergeIndices(
        sourcesDir: SourcesDir,
        includedDeps: Map<Path, Index>
    ) {
        val dir = sourcesDir.index
        val includedIndices = includedDeps.mapValues { it.value.dir }
        providers.forEach { provider ->
            val providerDir = dir.resolve(provider.name)
            if (providerDir.exists()) {
                return@forEach
            }

            providerDir.createDirectories()
            provider.mergeIndices(includedIndices.entries.associate { it.value.resolve(provider.name) to it.key }, providerDir)
        }
    }

    override suspend fun search(
        sourcesDir: SourcesDir,
        provider: SearchProvider,
        query: String
    ): List<RelativeSearchResult> {
        return provider.search(sourcesDir.index.resolve(provider.name), query)
    }
}