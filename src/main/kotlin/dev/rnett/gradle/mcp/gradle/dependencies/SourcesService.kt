package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.dependencies.search.IndexService
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.gradle.dependencies.search.toSearchResults
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.jar.JarInputStream
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension

data class SourcesDir(val path: Path) {
    val sources = path.resolve("sources")
    val metadata = path.resolve("metadata")
    val index = metadata.resolve("index")
}

interface SourcesService {

    /**
     * Download sources for all dependencies in the project.
     */
    suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean = true, forceDownload: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific project.
     */
    suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, index: Boolean = true, forceDownload: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific configuration.
     */
    suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, index: Boolean = true, forceDownload: Boolean = false): SourcesDir

    /**
     * Download sources for all dependencies in a specific source set.
     */
    suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, index: Boolean = true, forceDownload: Boolean = false): SourcesDir

    suspend fun search(sources: SourcesDir, provider: SearchProvider, query: String): List<SearchResult>
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
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean, forceDownload: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, "", "root")
        val markerFile = dir.path.resolve(".completed")
        if (markerFile.exists() && !forceDownload) return dir
        if (forceDownload) dir.path.deleteRecursively()

        val deps = depService.downloadAllSources(projectRoot)
        extractSources(deps.projects.asSequence().flatMap { it.allDependencies() }, dir, index)
        dir.path.createDirectories()
        markerFile.createFile()
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, index: Boolean, forceDownload: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, projectPath, "project")
        val markerFile = dir.path.resolve(".completed")
        if (markerFile.exists() && !forceDownload) return dir
        if (forceDownload) dir.path.deleteRecursively()

        val deps = depService.downloadProjectSources(projectRoot, projectPath)
        extractSources(deps.allDependencies(), dir, index)
        dir.path.createDirectories()
        markerFile.createFile()
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, index: Boolean, forceDownload: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, configurationPath, "configuration")
        val markerFile = dir.path.resolve(".completed")
        if (markerFile.exists() && !forceDownload) return dir
        if (forceDownload) dir.path.deleteRecursively()

        val deps = depService.downloadConfigurationSources(projectRoot, configurationPath)
        extractSources(deps.allDependencies(), dir, index)
        dir.path.createDirectories()
        markerFile.createFile()
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, index: Boolean, forceDownload: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, sourceSetPath, "sourceSet")
        val markerFile = dir.path.resolve(".completed")
        if (markerFile.exists() && !forceDownload) return dir
        if (forceDownload) dir.path.deleteRecursively()

        val deps = depService.downloadSourceSetSources(projectRoot, sourceSetPath)
        extractSources(deps.configurations.asSequence().flatMap { it.allDependencies() }, dir, index)
        dir.path.createDirectories()
        markerFile.createFile()
        return dir
    }

    override suspend fun search(
        sources: SourcesDir,
        provider: SearchProvider,
        query: String
    ): List<SearchResult> {
        val results = indexService.search(sources, provider, query)
        val root = if (sources.path.startsWith(gradleSourcesDir)) {
            sources.sources
        } else {
            globalSourcesDir
        }
        return results.toSearchResults(root)
    }

    @OptIn(ExperimentalPathApi::class)
    private suspend fun extractSources(deps: Sequence<GradleDependency>, target: SourcesDir, index: Boolean) {
        val validDeps = deps.filter { it.sourcesFile != null && it.group != null && it.version != null }.toSet()
        if (validDeps.isEmpty()) {
            LOGGER.info("No dependencies with sources to extract")
            return
        }
        LOGGER.info("Extracting sources for ${validDeps.size} dependencies")

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