package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.markerFileName
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.time.measureTime

interface GradleSourceService {
    context(progress: ProgressReporter)
    suspend fun getGradleSources(projectRoot: GradleProjectRoot, forceDownload: Boolean = false, providerToIndex: SearchProvider? = null): SourcesDir
}

@OptIn(ExperimentalPathApi::class)
class DefaultGradleSourceService(
    private val environment: GradleMcpEnvironment,
    private val storageService: SourceStorageService,
    private val indexService: dev.rnett.gradle.mcp.dependencies.search.IndexService,
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
    override suspend fun getGradleSources(projectRoot: GradleProjectRoot, forceDownload: Boolean, providerToIndex: SearchProvider?): SourcesDir = withContext(Dispatchers.IO) {
        val rootPath = GradlePathUtils.getRootProjectPath(projectRoot)
        val inputVersion = GradlePathUtils.getGradleVersion(rootPath)
        val version = versionService.resolveVersion(inputVersion)

        val storagePath = gradleSourcesDir.resolve(version)
        val lockFile = storageService.getLockFile(storagePath)
        val targetDir = MergedSourcesDir(storagePath, storagePath.resolve("sources"), storagePath.resolve("metadata"))
        val markerFile = storagePath.resolve(".completed")

        if (!forceDownload) {
            val cached = FileLockManager.withLock(lockFile, shared = true) {
                if (markerFile.exists()) {
                    if (providerToIndex == null) {
                        targetDir
                    } else {
                        if (targetDir.index.resolve(providerToIndex.markerFileName).exists()) {
                            targetDir
                        } else null
                    }
                } else null
            }
            if (cached != null) return@withContext cached
        }

        return@withContext FileLockManager.withLock(lockFile, shared = false) {
            if (markerFile.exists() && !forceDownload) {
                if (providerToIndex != null && !targetDir.index.resolve(providerToIndex.markerFileName).exists()) {
                    indexInternal(version, null, targetDir, providerToIndex)
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
    private suspend fun indexInternal(version: String, sourceZip: Path?, targetDir: MergedSourcesDir, providerToIndex: SearchProvider? = null) {
        if (providerToIndex == null) return

        LOGGER.info("Indexing Gradle sources at ${targetDir.sources} for version $version")
        val duration = measureTime {
            coroutineScope {
                val channel = Channel<IndexEntry>(capacity = 20)

                val indexJob = launch(Dispatchers.IO) {
                    try {
                        indexService.indexFiles(targetDir.metadataPath, channel.consumeAsFlow(), providerToIndex)
                    } finally {
                        for (entry in channel) {
                        }
                    }
                }

                try {
                    val rootDir = targetDir.sources
                    Files.walk(rootDir).use { stream ->
                        for (file in stream) {
                            if (Files.isRegularFile(file)) {
                                val ext = file.fileName.toString().substringAfterLast('.', "")
                                if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                                    val relativePath = rootDir.relativize(file).toString().replace('\\', '/')
                                    channel.send(IndexEntry(relativePath) { file.readText() })
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    indexJob.cancel()
                    throw e
                } finally {
                    channel.close()
                }

                indexJob.join()
            }
        }
        LOGGER.info("Indexed Gradle sources in $duration for version $version")
    }

    context(progress: ProgressReporter)
    private suspend fun extractAndIndex(version: String, sourceZip: Path, targetDir: MergedSourcesDir, providerToIndex: SearchProvider? = null) {
        targetDir.sources.createDirectories()

        val extractionProgress = progress.withPhase("PROCESSING")
        extractionProgress.report(0.0, 1.0, "Extracting Gradle $version sources")

        LOGGER.info("Extracting Gradle sources from $sourceZip to ${targetDir.sources}")
        with(extractionProgress) {
            ArchiveExtractor.extractInto(targetDir.sources, sourceZip, skipSingleFirstDir = true)
        }

        withContext(Dispatchers.IO) {
            cleanupGradleSources(targetDir.sources)
        }

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
                    if (buildLogicMatcher.matches(kotlin.io.path.Path(fileName)) || fileName in excludedRootDirs) {
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
