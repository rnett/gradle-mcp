package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.dependencies.search.IndexService
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.gradle.dependencies.search.toSearchResults
import dev.rnett.gradle.mcp.tools.PaginationInput
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarInputStream
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class SourcesDir(val path: Path) {
    val sources = path.resolve("sources")
    val metadata = path.resolve("metadata")
    val index = metadata.resolve("index")
    val lastRefreshFile = metadata.resolve(".last_refresh")

    fun lastRefresh(): java.time.Instant? {
        if (lastRefreshFile.exists()) {
            return try {
                java.time.Instant.parse(lastRefreshFile.readText())
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}

interface SourcesService {

    /**
     * Download sources for all dependencies in the project.
     */
    suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific project.
     */
    suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific configuration.
     */
    suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific source set.
     */
    suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, index: Boolean = true, forceDownload: Boolean = false, fresh: Boolean = false): SourcesDir

    suspend fun search(sources: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS): SearchResponse<SearchResult>
}

class DefaultSourcesService(private val depService: GradleDependencyService, environment: GradleMcpEnvironment, private val indexService: IndexService) : SourcesService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultSourcesService::class.java)
    }

    val sourcesDir = environment.cacheDir.resolve("sources")
    val globalSourcesDir = environment.cacheDir.resolve("extracted-sources")
    val gradleSourcesDir = environment.cacheDir.resolve("gradle-sources")

    init {
        sourcesDir.createDirectories()
        globalSourcesDir.createDirectories()
    }

    private fun sourcesDirectory(projectRoot: GradleProjectRoot, path: String, kind: String): SourcesDir {
        return SourcesDir(sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + path.hashCode().toString() + kind.hashCode().toString()))
    }

    @OptIn(ExperimentalPathApi::class)
    private fun dependencyHash(deps: Sequence<GradleDependency>): String {
        return deps
            .filter { it.sourcesFile != null && it.group != null && it.version != null }
            .map { "${it.id}:${it.sourcesFile}" }
            .sorted()
            .joinToString("\n")
            .hashCode()
            .toString()
    }

    @OptIn(ExperimentalPathApi::class)
    private fun checkCached(dir: SourcesDir, currentHash: String, forceDownload: Boolean): Boolean {
        val hashFile = dir.path.resolve(".dependencies.hash")
        if (forceDownload) {
            dir.path.deleteRecursively()
            return false
        }
        return hashFile.exists() && hashFile.readText() == currentHash
    }

    @OptIn(ExperimentalPathApi::class)
    private fun saveCache(dir: SourcesDir, currentHash: String) {
        dir.path.createDirectories()
        dir.metadata.createDirectories()
        dir.path.resolve(".dependencies.hash").writeText(currentHash)
        dir.lastRefreshFile.writeText(java.time.Instant.now().toString())
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, "", "root")
        if (!fresh && !forceDownload && dir.sources.exists()) return dir

        val deps = depService.downloadAllSources(projectRoot)
        val allDeps = deps.projects.asSequence().flatMap { it.allDependencies() }
        val currentHash = dependencyHash(allDeps)

        if (checkCached(dir, currentHash, forceDownload)) return dir

        extractSources(allDeps, dir, index)
        saveCache(dir, currentHash)
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, projectPath, "project")
        if (!fresh && !forceDownload && dir.sources.exists()) return dir

        val deps = depService.downloadProjectSources(projectRoot, projectPath)
        val allDeps = deps.allDependencies()
        val currentHash = dependencyHash(allDeps)

        if (checkCached(dir, currentHash, forceDownload)) return dir

        extractSources(allDeps, dir, index)
        saveCache(dir, currentHash)
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, configurationPath, "configuration")
        if (!fresh && !forceDownload && dir.sources.exists()) return dir

        val deps = depService.downloadConfigurationSources(projectRoot, configurationPath)
        val allDeps = deps.allDependencies()
        val currentHash = dependencyHash(allDeps)

        if (checkCached(dir, currentHash, forceDownload)) return dir

        extractSources(allDeps, dir, index)
        saveCache(dir, currentHash)
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, sourceSetPath, "sourceSet")
        if (!fresh && !forceDownload && dir.sources.exists()) return dir

        val deps = depService.downloadSourceSetSources(projectRoot, sourceSetPath)
        val allDeps = deps.configurations.asSequence().flatMap { it.allDependencies() }
        val currentHash = dependencyHash(allDeps)

        if (checkCached(dir, currentHash, forceDownload)) return dir

        extractSources(allDeps, dir, index)
        saveCache(dir, currentHash)
        return dir
    }

    override suspend fun search(
        sources: SourcesDir,
        provider: SearchProvider,
        query: String,
        pagination: PaginationInput
    ): SearchResponse<SearchResult> {
        val response = indexService.search(sources, provider, query, pagination)
        val root = if (sources.path.startsWith(gradleSourcesDir)) {
            sources.sources
        } else {
            globalSourcesDir
        }
        return SearchResponse(
            results = response.results.toSearchResults(root),
            interpretedQuery = response.interpretedQuery,
            error = response.error
        )
    }

    @OptIn(ExperimentalPathApi::class)
    private suspend fun extractSources(deps: Sequence<GradleDependency>, target: SourcesDir, index: Boolean) {
        val validDeps = deps.filter { it.sourcesFile != null && it.group != null && it.version != null }.toSet()
        if (validDeps.isEmpty()) {
            LOGGER.info("No dependencies with sources to extract")
            return
        }
        LOGGER.info("Extracting sources for ${validDeps.size} dependencies")

        val targetSources = target.sources
        if (targetSources.exists()) {
            targetSources.deleteRecursively()
        }
        targetSources.createDirectories()

        val indices = validDeps.mapNotNull { dep ->
            val ext = dep.sourcesFile!!.extension
            val relativePath = Path(dep.group!!).resolve(dep.sourcesFile.nameWithoutExtension)
            val dir = globalSourcesDir.resolve(relativePath)
            val extractionMarker = dir.resolve(".extracted")

            if (!extractionMarker.exists()) {
                val stream = when (ext) {
                    "jar" -> JarInputStream(dep.sourcesFile.inputStream().buffered())
                    "zip" -> ZipInputStream(dep.sourcesFile.inputStream().buffered())
                    else -> {
                        LOGGER.warn("Sources artifact with unrecognized extension: {}", dep.sourcesFile)
                        return@mapNotNull null
                    }
                }
                dir.deleteRecursively()
                dir.createDirectories()
                try {
                    ArchiveExtractor.extractInto(dir, stream)
                    extractionMarker.createFile()
                } catch (e: Exception) {
                    LOGGER.error("Failed to extract sources for $dep", e)
                    dir.deleteRecursively()
                    return@mapNotNull null
                }
            }

            // Sync to target.sources
            val scopeLink = targetSources.resolve(relativePath)
            scopeLink.createParentDirectories()
            try {
                // Try to create a symbolic link (incremental sync)
                // Note: On Windows this might require developer mode or admin rights
                Files.createSymbolicLink(scopeLink, dir)
            } catch (e: Exception) {
                // Fallback to copy or just ignore (search will still work via globalSourcesDir)
                LOGGER.warn("Failed to create symbolic link for $dep in $targetSources. Falling back to simple directory creation.", e)
                scopeLink.createDirectories()
            }

            if (index) {
                indexService.index(dep, dir)?.let { relativePath to it }
            } else null
        }
        if (indices.isNotEmpty()) {
            indexService.mergeIndices(
                target,
                indices.toMap()
            )
        }
    }

}