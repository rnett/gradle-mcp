package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesDir
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
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
    private val providers = listOf(SymbolSearch, FullTextSearch, GlobSearch)
    private val combinedIndexVersion = providers.joinToString("-") { "${it.name}:${it.indexVersion}" }
    private val markerFileName = ".indexed-${combinedIndexVersion.hashCode()}"
    private val indexDir = environment.cacheDir.resolve("source-indices")

    @OptIn(ExperimentalPathApi::class)
    override suspend fun index(dependency: GradleDependency, sourceDir: Path): Index? {
        if (dependency.group == null) return null

        val dirName = dependency.sourcesFile?.nameWithoutExtension ?: "direct-${sourceDir.hashCode()}"
        val dir = indexDir.resolve(dependency.group).resolve(dirName)
        val markerFile = dir.resolve(markerFileName)

        if (markerFile.exists()) {
            return Index(dir)
        }

        try {
            providers.forEach { provider ->
                val providerDir = dir.resolve(provider.name)
                if (providerDir.exists()) {
                    providerDir.deleteRecursively()
                }

                providerDir.createDirectories()
                provider.index(sourceDir, providerDir)
            }
            markerFile.createFile()
        } catch (e: Exception) {
            LOGGER.error("Failed to index dependency $dependency", e)
            dir.deleteRecursively()
            return null
        }
        return Index(dir)
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun mergeIndices(
        sourcesDir: SourcesDir,
        includedDeps: Map<Path, Index>
    ) {
        val (unit, duration) = measureTimedValue {
            val dir = sourcesDir.index
            val hashFile = dir.resolve(".merged.hash")
            val currentHash = combinedIndexVersion + "\n" + includedDeps.hashCode().toString()

            if (hashFile.exists() && hashFile.readText() == currentHash) {
                return@measureTimedValue
            }

            val includedIndices = includedDeps.mapValues { it.value.dir }
            try {
                providers.forEach { provider ->
                    val providerDir = dir.resolve(provider.name)
                    if (providerDir.exists()) {
                        providerDir.deleteRecursively()
                    }

                    providerDir.createDirectories()
                    provider.mergeIndices(includedIndices.entries.associate { it.value.resolve(provider.name) to it.key }, providerDir)
                }
                hashFile.createParentDirectories()
                hashFile.writeText(currentHash)
            } catch (e: Exception) {
                LOGGER.error("Failed to merge indices for ${sourcesDir.path}", e)
                dir.deleteRecursively()
                throw e
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
            val providerIndexDir = sourcesDir.index.resolve(provider.name)
            if (!sourcesDir.index.exists()) {
                return@measureTimedValue emptyList()
            }
            if (!providerIndexDir.exists()) {
                throw IllegalStateException("Index for provider ${provider.name} not found in ${sourcesDir.index}")
            }
            provider.search(providerIndexDir, query)
        }
        LOGGER.info("Search using ${provider.name} for \"$query\" in ${sourcesDir.path} took $duration (${results.size} results)")
        return results
    }
}