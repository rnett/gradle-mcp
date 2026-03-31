package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.tools.PaginationInput
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

/**
 * Manages the underlying search indices (Lucene/etc) for both individual dependencies
 * and project-level merged views. Handles indexing, searching, and metadata persistence.
 */
interface IndexService {
    /**
     * Indexes a stream of files for a specific CAS directory and search provider.
     * The index is created in the provided [casBaseDir]'s index subdirectory.
     */
    context(progress: ProgressReporter)
    suspend fun indexFiles(casBaseDir: Path, fileFlow: Flow<IndexEntry>, provider: SearchProvider): Index?

    /**
     * Performs a search against multiple indices using MultiReader.
     */
    suspend fun search(view: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult>

    /**
     * Lists package contents using a MultiReader over all indices in the session.
     */
    suspend fun listPackageContents(view: SourcesDir, packageName: String): PackageContents?
}

@OptIn(ExperimentalAtomicApi::class)
class DefaultIndexService(
    val environment: GradleMcpEnvironment
) : IndexService {
    private val logger = LoggerFactory.getLogger(DefaultIndexService::class.java)

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    override suspend fun indexFiles(casBaseDir: Path, fileFlow: Flow<IndexEntry>, provider: SearchProvider): Index? {
        val dir = casBaseDir.resolve("index")
        val markerFile = dir.resolve(provider.markerFileName)

        val providerDir = dir.resolve(provider.name)
        if (markerFile.exists() && providerDir.exists()) {
            fileFlow.collect { } // Consume the flow to avoid resource leaks
            return Index(dir)
        }

        cleanupOldIndices(dir, provider, markerFile)

        if (providerDir.exists()) providerDir.deleteRecursively()
        providerDir.createDirectories()

        val indexer = provider.newIndexer(providerDir)
        try {
            var count = 0
            try {
                fileFlow.collect { entry ->
                    count++
                    progress.report(count.toDouble(), null, "Indexing ${entry.relativePath}")
                    indexer.indexFile(entry.relativePath, entry.content)
                }
                indexer.finish()

                // Update metadata
                val metadataFile = dir.resolve(".metadata-${provider.name}.json")

                val mutableCounts = mapOf(provider.name to indexer.documentCount)

                val tmpFile = metadataFile.resolveSibling(".metadata-${provider.name}.json.tmp")
                tmpFile.writeText(Json.encodeToString(mutableCounts))
                Files.move(tmpFile, metadataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                indexer.close()
            }

            markerFile.createParentDirectories()
            markerFile.createFile()
            return Index(dir)
        } catch (e: Exception) {
            logger.error("Failed to index CAS directory $casBaseDir for provider ${provider.name}", e)
            providerDir.deleteRecursively()
            return null
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun cleanupOldIndices(dir: Path, provider: SearchProvider, markerFile: Path) {
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

        // Also cleanup old index directories (for Lucene providers)
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
            } else if (name == provider.name && !markerFile.exists()) {
                // If directory exists but marker doesn't, it's a partial/old index of current version
                logger.info("Cleaning up partial index for ${provider.name} in $dir")
                entry.deleteRecursively()
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(view: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> {
        val indexDirs = getProviderIndexDirs(view, provider)
        if (indexDirs.isEmpty()) return SearchResponse(emptyList(), error = "No dependencies found with indices.")
        return provider.search(indexDirs, query, pagination)
    }

    override suspend fun listPackageContents(view: SourcesDir, packageName: String): PackageContents? {
        val indexDirs = getProviderIndexDirs(view, DeclarationSearch)
        if (indexDirs.isEmpty()) return null
        return DeclarationSearch.listPackageContents(indexDirs, packageName)
    }

    private fun getProviderIndexDirs(view: SourcesDir, provider: SearchProvider): List<Path> {
        return view.resolveIndexDirs(provider.name)
    }
}
