package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.dependencies.search.IndexService
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchResult
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.jar.JarInputStream
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
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

    init {
        sourcesDir.createDirectories()
    }

    private fun sourcesDirectory(projectRoot: GradleProjectRoot, path: String, kind: String): SourcesDir {
        return SourcesDir(sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + path.hashCode().toString() + kind.hashCode().toString()))
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean, forceDownload: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, "", "root")
        if (dir.sources.exists() && !forceDownload) return dir
        if (forceDownload) dir.path.deleteRecursively()

        val deps = depService.downloadAllSources(projectRoot)
        extractSources(deps.projects.asSequence().flatMap { it.allDependencies() }, dir, index)
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, index: Boolean, forceDownload: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, projectPath, "project")
        if (dir.sources.exists() && !forceDownload) return dir
        if (forceDownload) dir.path.deleteRecursively()

        val deps = depService.downloadProjectSources(projectRoot, projectPath)
        extractSources(deps.allDependencies(), dir, index)
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, index: Boolean, forceDownload: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, configurationPath, "configuration")
        if (dir.sources.exists() && !forceDownload) return dir
        if (forceDownload) dir.path.deleteRecursively()

        val deps = depService.downloadConfigurationSources(projectRoot, configurationPath)
        extractSources(deps.allDependencies(), dir, index)
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, index: Boolean, forceDownload: Boolean): SourcesDir {
        val dir = sourcesDirectory(projectRoot, sourceSetPath, "sourceSet")
        if (dir.sources.exists() && !forceDownload) return dir
        if (forceDownload) dir.path.deleteRecursively()

        val deps = depService.downloadSourceSetSources(projectRoot, sourceSetPath)
        extractSources(deps.configurations.asSequence().flatMap { it.allDependencies() }, dir, index)
        return dir
    }

    override suspend fun search(
        sources: SourcesDir,
        provider: SearchProvider,
        query: String
    ): List<SearchResult> {
        return indexService.search(sources, provider, query)
            .map {
                it.toSearchResult(sources.sources)
            }
    }

    private suspend fun extractSources(deps: Sequence<GradleDependency>, target: SourcesDir, index: Boolean) {
        val deps = deps.filter { it.sourcesFile != null && it.group != null && it.version != null }.toSet()
        val indices = deps.mapNotNull {
            val ext = it.sourcesFile!!.extension
            val stream = when (ext) {
                "jar" -> JarInputStream(it.sourcesFile.inputStream().buffered())
                "zip" -> ZipInputStream(it.sourcesFile.inputStream().buffered())
                else -> {
                    LOGGER.warn("Sources artifact with unrecognized extension: {}", it.sourcesFile)
                    return@mapNotNull null
                }
            }
            val relativePath = Path(it.group!!).resolve(it.sourcesFile.nameWithoutExtension)
            val dir = target.sources.resolve(relativePath)
            dir.createDirectories()
            ArchiveExtractor.extractInto(dir, stream)
            if (index) {
                indexService.index(it, dir)?.let { relativePath to it }
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