package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesDir
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.time.measureTimedValue

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
    private val LOGGER = LoggerFactory.getLogger(DefaultIndexService::class.java)
    private val providers = listOf(SymbolSearch, FullTextSearch)
    private val indexDir = environment.cacheDir.resolve("source-indices")
    override suspend fun index(dependency: GradleDependency, sourceDir: Path): Index? {
        if (dependency.group == null) return null

        val dir = indexDir.resolve(dependency.group).resolve(dependency.sourcesFile?.nameWithoutExtension ?: "direct-${sourceDir.hashCode()}")

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
        val (unit, duration) = measureTimedValue {
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
        LOGGER.info("Merging indices for ${sourcesDir.path} took $duration (${includedDeps.size} dependencies)")
    }

    override suspend fun search(
        sourcesDir: SourcesDir,
        provider: SearchProvider,
        query: String
    ): List<RelativeSearchResult> {
        val (results, duration) = measureTimedValue {
            provider.search(sourcesDir.index.resolve(provider.name), query)
        }
        LOGGER.info("Search using ${provider.name} for \"$query\" in ${sourcesDir.path} took $duration (${results.size} results)")
        return results
    }
}