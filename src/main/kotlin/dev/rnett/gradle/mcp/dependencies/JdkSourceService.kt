package dev.rnett.gradle.mcp.dependencies

import com.github.benmanes.caffeine.cache.Caffeine
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.markerFileName
import dev.rnett.gradle.mcp.utils.FileLockManager
import dev.rnett.gradle.mcp.utils.FileUtils
import dev.rnett.gradle.mcp.utils.FileUtils.deleteRecursivelyWithRetry
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Optional
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.measureTime

/**
 * Service for resolving, extracting, and indexing JDK source code from `src.zip`.
 * JDK sources are stored in the same CAS cache as dependency sources.
 */
interface JdkSourceService {
    /**
     * Resolves JDK sources for the given [jdkHome].
     *
     * Locates `src.zip`, computes its SHA-256 for content-addressed caching,
     * extracts it to the CAS cache, and optionally indexes it with [providerToIndex].
     *
     * @param jdkHome path to the JDK installation (e.g., `/usr/lib/jvm/java-21`)
     * @param forceDownload if true, bypasses memoized archive lookup/hash data and rebuilds the completed
     * CAS entry under the base lock, matching dependency-source force refresh semantics
     * @param fresh if true, refreshes dependent indexes without rebuilding the completed CAS entry
     * @param providerToIndex optional search provider to index for
     * @return the resolved CAS directory, or null if `src.zip` was not found
     */
    context(progress: ProgressReporter)
    suspend fun resolveSources(
        jdkHome: String,
        forceDownload: Boolean = false,
        fresh: Boolean = false,
        providerToIndex: SearchProvider? = null
    ): CASDependencySourcesDir?
}

/**
 * Resolves local JDK sources from `src.zip` into the dependency-source CAS.
 *
 * Only the active JDK layouts specified by OpenSpec are searched: `<javaHome>/lib/src.zip`
 * and `<javaHome>/src.zip`. When neither file is present, resolution returns `null`
 * so callers can expose ordinary dependency sources without inventing a JDK entry.
 * Completed CAS entries are keyed by the SHA-256 of the archive. `fresh` rebuilds
 * dependent indexes; `forceDownload` rebuilds the completed base entry under lock.
 */
@OptIn(ExperimentalPathApi::class)
class DefaultJdkSourceService(
    private val storageService: SourceStorageService,
    private val indexService: dev.rnett.gradle.mcp.dependencies.search.IndexService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : JdkSourceService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultJdkSourceService::class.java)
        private const val BUFFER_SIZE = 8192
    }

    private data class HashCacheKey(
        val path: Path,
        val size: Long,
        val modifiedMillis: Long
    )

    private val srcZipCache = Caffeine.newBuilder().maximumSize(100).build<Path, Optional<Path>>()
    private val hashCache = Caffeine.newBuilder().maximumSize(100).build<HashCacheKey, String>()

    context(progress: ProgressReporter)
    override suspend fun resolveSources(
        jdkHome: String,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider?
    ): CASDependencySourcesDir? = withContext(dispatcher) {
        val jdkHomePath = validateJdkHome(jdkHome) ?: return@withContext null
        if (fresh || forceDownload) {
            srcZipCache.invalidate(jdkHomePath)
        }

        val refresh = fresh || forceDownload
        val srcZip = locateSrcZip(jdkHomePath) ?: return@withContext null
        val sha256 = computeSha256(srcZip, bypassCache = refresh)
        val casDir = storageService.getCASDependencySourcesDir(sha256)

        val cached = FileLockManager.withLock(casDir.baseLockFile, shared = true) {
            if (isBaseReady(casDir)) {
                if (!forceDownload && (providerToIndex == null || (!fresh && casDir.index.resolve(providerToIndex.markerFileName).exists()))) {
                    casDir
                } else {
                    null
                }
            } else {
                null
            }
        }
        if (cached != null) return@withContext cached

        FileLockManager.withLock(casDir.baseLockFile) {
            if (forceDownload || !isBaseReady(casDir)) {
                repairBaseEntry(srcZip, casDir)
            }
        }

        if (providerToIndex != null) {
            ensureIndexed(casDir, providerToIndex, force = fresh || forceDownload)
        }

        casDir
    }

    private fun validateJdkHome(jdkHome: String): Path? {
        try {
            val path = Path.of(jdkHome)
            if (!path.isAbsolute) {
                LOGGER.warn("Skipping JDK sources because the detected JDK home is not an absolute path.")
                return null
            }
            val real = path.toRealPath()
            if (!real.isDirectory()) {
                LOGGER.warn("Skipping JDK sources because the detected JDK home is not a directory.")
                return null
            }
            return real
        } catch (e: Exception) {
            LOGGER.warn("Skipping JDK sources because the detected JDK home could not be validated.")
            LOGGER.debug("Detected JDK home validation failed.", e)
            return null
        }
    }

    private fun locateSrcZip(jdkHomePath: Path): Path? {
        val cached = srcZipCache.get(jdkHomePath) {
            Optional.ofNullable(locateSrcZipUncached(jdkHomePath))
        }
        return cached.orElse(null)
    }

    private fun locateSrcZipUncached(jdkHomePath: Path): Path? {
        val candidates = listOf(
            jdkHomePath.resolve("lib").resolve("src.zip"),
            jdkHomePath.resolve("src.zip")
        )

        for (candidate in candidates) {
            val srcZip = try {
                candidate.toRealPath()
            } catch (_: Exception) {
                null
            }
            if (srcZip != null && srcZip.isRegularFile() && srcZip.startsWith(jdkHomePath)) {
                return srcZip
            }
        }

        LOGGER.warn("JDK src.zip not found for detected JDK home; JDK sources will be skipped.")
        LOGGER.debug("Checked JDK source candidates: {}", candidates)
        return null
    }

    private fun computeSha256(file: Path, bypassCache: Boolean): String {
        val key = HashCacheKey(
            path = file,
            size = Files.size(file),
            modifiedMillis = file.getLastModifiedTime().toMillis()
        )
        if (bypassCache) {
            hashCache.invalidate(key)
            return computeSha256Uncached(file)
        }
        return hashCache.get(key) {
            computeSha256Uncached(file)
        }
    }

    private fun computeSha256Uncached(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isBaseReady(casDir: CASDependencySourcesDir): Boolean =
        casDir.baseCompletedMarker.exists() && casDir.sources.exists()

    context(progress: ProgressReporter)
    private suspend fun repairBaseEntry(srcZip: Path, casDir: CASDependencySourcesDir) {
        LOGGER.info("Preparing JDK sources CAS entry for hash ${casDir.hash}")
        if (casDir.baseDir.exists()) {
            indexService.invalidateAllCaches(casDir.index)
            if (casDir.sources.exists()) casDir.sources.deleteRecursivelyWithRetry()
            if (casDir.normalizedDir.exists()) casDir.normalizedDir.deleteRecursivelyWithRetry()
            if (casDir.normalizedTargetDir.exists()) casDir.normalizedTargetDir.deleteRecursivelyWithRetry()
            if (casDir.index.exists()) casDir.index.deleteRecursivelyWithRetry()
            casDir.baseCompletedMarker.deleteIfExists()
            casDir.processedWithCommonMarker.deleteIfExists()
        }

        val tempDir = casDir.baseDir.resolveSibling("${casDir.hash}.jdk-base.${java.util.UUID.randomUUID()}.tmp")
        try {
            val tempSources = tempDir.resolve("sources")
            tempSources.createDirectories()
            val extractionProgress = progress.withPhase("PROCESSING")
            extractionProgress.report(0.0, 1.0, "Extracting JDK sources")
            LOGGER.debug("Extracting JDK sources from {} into temporary CAS directory {}", srcZip, tempSources)
            with(extractionProgress) {
                ArchiveExtractor.extractInto(tempSources, srcZip, skipSingleFirstDir = false)
            }
            extractionProgress.report(1.0, 1.0, "Extracted JDK sources")

            casDir.baseDir.createDirectories()
            FileUtils.atomicMoveIfAbsent(tempSources, casDir.sources)
            createMarkerIfAbsent(casDir.baseCompletedMarker)
        } finally {
            if (tempDir.exists()) tempDir.deleteRecursivelyWithRetry()
        }
    }

    private fun createMarkerIfAbsent(marker: Path) {
        marker.parent?.createDirectories()
        try {
            Files.createFile(marker)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            // Another process published the same completed CAS entry first.
        }
    }

    context(progress: ProgressReporter)
    private suspend fun ensureIndexed(casDir: CASDependencySourcesDir, providerToIndex: SearchProvider, force: Boolean) {
        FileLockManager.withLock(casDir.baseLockFile, shared = true) {
            FileLockManager.withLock(casDir.indexLockFile(providerToIndex.name)) {
                if (!force && casDir.index.resolve(providerToIndex.markerFileName).exists()) {
                    return@withLock
                }
                indexInternal(casDir, providerToIndex)
            }
        }
    }

    context(progress: ProgressReporter)
    private suspend fun indexInternal(casDir: CASDependencySourcesDir, providerToIndex: SearchProvider) {
        LOGGER.info("Indexing JDK sources for hash ${casDir.hash}")
        val duration = measureTime {
            val tempDir = casDir.baseDir.resolveSibling("${casDir.hash}.jdk-index-${providerToIndex.name}.${java.util.UUID.randomUUID()}.tmp")
            try {
                indexPrefixedSources(tempDir, casDir.sources, providerToIndex, casDir.hash)
                val tempProviderDir = tempDir.resolve("index").resolve(providerToIndex.name)
                if (tempProviderDir.exists()) {
                    casDir.index.createDirectories()
                    providerToIndex.invalidateCache(casDir.index.resolve(providerToIndex.name))
                    FileUtils.atomicReplaceDirectory(tempProviderDir, casDir.index.resolve(providerToIndex.name))
                }
                val markerFile = tempDir.resolve("index").resolve(providerToIndex.markerFileName)
                if (markerFile.exists()) {
                    Files.move(
                        markerFile,
                        casDir.index.resolve(providerToIndex.markerFileName),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } finally {
                if (tempDir.exists()) tempDir.deleteRecursivelyWithRetry()
            }
        }
        LOGGER.info("Indexed JDK sources in $duration")
    }

    context(progress: ProgressReporter)
    private suspend fun indexPrefixedSources(
        indexBaseDir: Path,
        sourcesRoot: Path,
        providerToIndex: SearchProvider,
        sourceHash: String
    ) = coroutineScope {
        val channel = Channel<IndexEntry>(capacity = 20)
        val indexJob = launch(dispatcher) {
            try {
                indexService.indexFiles(indexBaseDir, channel.consumeAsFlow(), providerToIndex)
            } finally {
                for (entry in channel) {
                    // drain channel
                }
            }
        }

        try {
            Files.walk(sourcesRoot).use { stream ->
                for (file in stream) {
                    if (Files.isRegularFile(file)) {
                        val ext = file.fileName.toString().substringAfterLast('.', "")
                        if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                            val pathWithinJdk = sourcesRoot.relativize(file).toString().replace('\\', '/')
                            val relativePath = "${GradleDependency.JDK_SOURCES_PREFIX}/$pathWithinJdk"
                            channel.send(IndexEntry(relativePath, sourceHash = sourceHash) { file.readText() })
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
