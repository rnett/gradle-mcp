package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.dependencies.search.IndexService
import dev.rnett.gradle.mcp.tools.GradlePathUtils
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

interface GradleSourceService {
    suspend fun getGradleSources(projectRoot: GradleProjectRoot, forceDownload: Boolean = false): SourcesDir
}

class DefaultGradleSourceService(
    private val environment: GradleMcpEnvironment,
    private val indexService: IndexService,
    private val httpClient: HttpClient
) : GradleSourceService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultGradleSourceService::class.java)
        private const val GRADLE_DIST_URL_TEMPLATE = "https://services.gradle.org/distributions/gradle-%s-src.zip"
    }

    private val gradleSourcesDir = environment.cacheDir.resolve("gradle-sources")

    init {
        gradleSourcesDir.createDirectories()
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun getGradleSources(projectRoot: GradleProjectRoot, forceDownload: Boolean): SourcesDir = withContext(Dispatchers.IO) {
        val rootPath = GradlePathUtils.getRootProjectPath(projectRoot)
        val version = GradlePathUtils.getGradleVersion(rootPath)
            ?: throw IllegalStateException("Could not determine Gradle version for $rootPath")

        val targetDir = SourcesDir(gradleSourcesDir.resolve(version))
        val markerFile = targetDir.path.resolve(".completed")

        if (markerFile.exists() && !forceDownload) {
            return@withContext targetDir
        }

        if (forceDownload) {
            targetDir.path.deleteRecursively()
        }

        targetDir.path.createDirectories()
        val sourceZip = downloadGradleSources(version)
        try {
            extractAndIndex(version, sourceZip, targetDir)
            markerFile.createFile()
            return@withContext targetDir
        } catch (e: Exception) {
            LOGGER.error("Failed to extract and index Gradle sources for version $version", e)
            targetDir.path.deleteRecursively()
            throw e
        } finally {
            sourceZip.deleteIfExists()
        }
    }

    private suspend fun downloadGradleSources(version: String): Path {
        val url = GRADLE_DIST_URL_TEMPLATE.format(version)
        val tempZip = Files.createTempFile("gradle-$version-src", ".zip")

        val (unit, duration) = measureTimedValue {
            LOGGER.info("Downloading Gradle sources from $url to $tempZip")
            val response = httpClient.get(url)
            if (!response.status.isSuccess()) {
                tempZip.deleteIfExists()
                throw IllegalStateException("Failed to download Gradle sources from $url: ${response.status}")
            }

            response.bodyAsChannel().toInputStream().use { input ->
                tempZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        LOGGER.info("Downloaded Gradle sources in $duration")
        return tempZip
    }

    private suspend fun indexInternal(version: String, sourceZip: Path?, targetDir: SourcesDir) {
        val dummyDependency = GradleDependency(
            id = "org.gradle:gradle:$version",
            group = "org.gradle",
            name = "gradle",
            version = version,
            sourcesFile = sourceZip
        )

        LOGGER.info("Indexing Gradle sources at ${targetDir.sources}")
        val duration = measureTime {
            val index = indexService.index(dummyDependency, targetDir.sources)
                ?: throw IllegalStateException("Failed to index Gradle sources at ${targetDir.sources}")

            if (index.dir != targetDir.index) {
                index.dir.toFile().copyRecursively(targetDir.index.toFile(), overwrite = true)
            }
        }
        LOGGER.info("Indexed Gradle sources in $duration")
    }

    private suspend fun extractAndIndex(version: String, sourceZip: Path, targetDir: SourcesDir) {
        targetDir.sources.createDirectories()

        LOGGER.info("Extracting Gradle sources from $sourceZip to ${targetDir.sources}")
        sourceZip.inputStream().buffered().use {
            ArchiveExtractor.extractInto(targetDir.sources, ZipInputStream(it), skipSingleFirstDir = true)
        }

        cleanupGradleSources(targetDir.sources)

        indexInternal(version, sourceZip, targetDir)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun cleanupGradleSources(root: Path) {
        val fs = FileSystems.getDefault()
        val buildLogicMatcher = fs.getPathMatcher("glob:build-logic*")
        val excludedRootDirs = listOf("gradle", "testing")

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relative = root.relativize(dir)
                val fileName = dir.name

                // Root-level exclusions
                if (relative.parent == null) {
                    if (buildLogicMatcher.matches(Path.of(fileName)) || fileName in excludedRootDirs) {
                        LOGGER.trace("Deleting excluded root directory: $relative")
                        dir.deleteRecursively()
                        return FileVisitResult.SKIP_SUBTREE
                    }
                }

                // Any src/subfolder that is NOT main
                if (dir.parent?.name == "src" && fileName != "main") {
                    LOGGER.trace("Deleting non-main source directory: $relative")
                    dir.deleteRecursively()
                    return FileVisitResult.SKIP_SUBTREE
                }

                return FileVisitResult.CONTINUE
            }
        })
    }
}
