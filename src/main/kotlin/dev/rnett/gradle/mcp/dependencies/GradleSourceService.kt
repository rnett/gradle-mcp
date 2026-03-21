package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.tools.GradlePathUtils
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.withPhase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.time.measureTime

interface GradleSourceService {
    context(progress: ProgressReporter)
    suspend fun getGradleSources(projectRoot: GradleProjectRoot, forceDownload: Boolean = false, providerToIndex: SearchProvider? = null): SourcesDir
}

@OptIn(ExperimentalPathApi::class)
class DefaultGradleSourceService(
    private val environment: GradleMcpEnvironment,
    private val indexService: IndexService,
    private val httpClient: HttpClient,
    private val versionService: GradleVersionService
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
    context(progress: ProgressReporter)
    override suspend fun getGradleSources(projectRoot: GradleProjectRoot, forceDownload: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        val rootPath = GradlePathUtils.getRootProjectPath(projectRoot)
        val inputVersion = GradlePathUtils.getGradleVersion(rootPath)
        val version = versionService.resolveVersion(inputVersion)

        val storagePath = gradleSourcesDir.resolve(version)
        val lockFile = lockFile(projectRoot, version, "gradle")
        val targetDir = MergedSourcesDir(storagePath, lockFile, storagePath.resolve("sources"))
        val markerFile = storagePath.resolve(".completed")

        if (!forceDownload) {
            val cached = FileLockManager.withLock(lockFile, shared = true) {
                if (markerFile.exists()) {
                    if (providerToIndex == null) {
                        targetDir
                    } else {
                        val upToDate = indexService.isMergeUpToDate(targetDir.index, providerToIndex, version)
                        if (upToDate) {
                            targetDir
                        } else {
                            null // Need to index this provider
                        }
                    }
                } else null
            }
            if (cached != null) return cached
        }

        return FileLockManager.withLock(lockFile, shared = false) {
            if (markerFile.exists() && !forceDownload) {
                if (providerToIndex != null) {
                    val upToDate = indexService.isMergeUpToDate(targetDir.index, providerToIndex, version)
                    if (!upToDate) {
                        indexInternal(version, null, targetDir, providerToIndex)
                    }
                }
                return@withLock targetDir
            }

            if (forceDownload) {
                LOGGER.info("Force downloading Gradle sources for version $version")
                storagePath.deleteRecursively()
            }

            storagePath.createDirectories()
            val sourceZip = try {
                downloadGradleSources(version)
            } catch (e: Exception) {
                LOGGER.error("Failed to download Gradle sources for version $version", e)
                throw e
            }

            try {
                extractAndIndex(version, sourceZip, targetDir, providerToIndex)
                markerFile.createFile()
                targetDir
            } catch (e: Exception) {
                LOGGER.error("Failed to extract and index Gradle sources for version $version", e)
                storagePath.deleteRecursively()
                throw e
            } finally {
                sourceZip.deleteIfExists()
            }
        }
    }

    private fun lockFile(projectRoot: GradleProjectRoot, path: String, kind: String): Path {
        return environment.lockFile(projectRoot.projectRoot, path, kind, "sources")
    }

    context(progress: ProgressReporter)
    private suspend fun downloadGradleSources(version: String): Path {
        val url = GRADLE_DIST_URL_TEMPLATE.format(version)
        val tempZip = withContext(Dispatchers.IO) {
            Files.createTempFile("gradle-$version-src", ".zip")
        }

        val downloadProgress = progress.withPhase("DOWNLOADING")

        val duration = measureTime {
            LOGGER.info("Downloading Gradle sources from $url to $tempZip")
            val response = httpClient.get(url) {
                onDownload { bytesSentTotal, contentLength ->
                    downloadProgress.report(bytesSentTotal.toDouble(), contentLength?.toDouble(), "Downloading Gradle $version source distribution")
                }
            }
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

    context(progress: ProgressReporter)
    private suspend fun indexInternal(version: String, sourceZip: Path?, targetDir: SourcesDir, providerToIndex: SearchProvider? = null) {
        if (providerToIndex == null) return

        val dummyDependency = GradleDependency(
            id = "org.gradle:gradle:$version",
            group = "org.gradle",
            name = "gradle",
            version = version,
            sourcesFile = sourceZip
        )

        LOGGER.info("Indexing Gradle sources at ${targetDir.sources} for version $version")
        val duration = measureTime {
            val index = with(progress) {
                indexService.index(dummyDependency, targetDir.sources, providerToIndex)
            } ?: throw IllegalStateException("Failed to index Gradle sources at ${targetDir.sources} for provider ${providerToIndex.name}")

            val providerIndexDir = targetDir.index.resolve(providerToIndex.name)
            if (index.dir.resolve(providerToIndex.name) != providerIndexDir) {
                providerIndexDir.createParentDirectories()
                index.dir.resolve(providerToIndex.name).copyToRecursively(providerIndexDir, followLinks = false, overwrite = true)
            }
        }
        LOGGER.info("Indexed Gradle sources in $duration for version $version")
    }

    context(progress: ProgressReporter)
    private suspend fun extractAndIndex(version: String, sourceZip: Path, targetDir: SourcesDir, providerToIndex: SearchProvider? = null) {
        targetDir.sources.createDirectories()

        val extractionProgress = progress.withPhase("PROCESSING")
        extractionProgress.report(0.0, 1.0, "Extracting Gradle $version sources")

        LOGGER.info("Extracting Gradle sources from $sourceZip to ${targetDir.sources}")
        with(extractionProgress) {
            ArchiveExtractor.extractInto(targetDir.sources, sourceZip, skipSingleFirstDir = true)
        }

        cleanupGradleSources(targetDir.sources)

        extractionProgress.report(1.0, 1.0, "Extracted Gradle $version sources")

        indexInternal(version, sourceZip, targetDir, providerToIndex)
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
