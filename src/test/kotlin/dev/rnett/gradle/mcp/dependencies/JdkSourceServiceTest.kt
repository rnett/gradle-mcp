package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.markerFileName
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdkSourceServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private val mockStorageService: SourceStorageService = mockk(relaxed = true)
    private val mockIndexService: IndexService = mockk(relaxed = true)

    private fun createSrcZip(dir: Path, vararg files: Pair<String, String>): Path {
        val srcZip = dir.resolve("src.zip")
        srcZip.outputStream().use { fos ->
            ZipOutputStream(fos).use { zos ->
                for ((name, content) in files) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray())
                    zos.closeEntry()
                }
            }
        }
        return srcZip
    }

    private fun createService(cacheDir: Path): DefaultJdkSourceService {
        every { mockStorageService.getLockFile(any()) } answers {
            val storagePath = firstArg<Path>()
            storagePath.resolve("lock")
        }
        every { mockStorageService.getCASDependencySourcesDir(any()) } answers {
            val hash = firstArg<String>()
            CASDependencySourcesDir(hash, cacheDir.resolve("cas").resolve("v3").resolve(hash.take(2)).resolve(hash))
        }
        every { mockIndexService.invalidateAllCaches(any()) } returns Unit
        coEvery {
            with(any<ProgressReporter>()) { mockIndexService.indexFiles(any(), any(), any()) }
        } answers {
            val indexBaseDir = invocation.args[1] as Path
            val provider = invocation.args[3] as SearchProvider
            indexBaseDir.resolve("index").resolve(provider.name).createDirectories()
            indexBaseDir.resolve("index").resolve(provider.markerFileName).createParentDirectories().createFile()
            Index(indexBaseDir.resolve("index"))
        }
        return DefaultJdkSourceService(mockStorageService, mockIndexService)
    }

    @Test
    fun `resolveSources finds srcZip at standard path`() = runTest {
        val cacheDir = tempDir.resolve("cache").createDirectories()
        val jdkHome = tempDir.resolve("jdk21")
        val libDir = jdkHome.resolve("lib").createDirectories()
        createSrcZip(libDir, "java/lang/String.java" to "package java.lang; public class String {}")

        val service = createService(cacheDir)

        val result = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString())
        }
        assertNotNull(result, "resolveSources should find src.zip at standard path")
        assertTrue(result.sources.exists())
        assertTrue(result.baseCompletedMarker.exists())
        assertTrue(result.sources.resolve("java/lang/String.java").exists())
    }

    @Test
    fun `resolveSources finds srcZip at legacy path`() = runTest {
        val cacheDir = tempDir.resolve("cache").createDirectories()
        val jdkHome = tempDir.resolve("jdk8").createDirectories()
        createSrcZip(jdkHome, "java/lang/Object.java" to "package java.lang; public class Object {}")

        val service = createService(cacheDir)

        val result = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString())
        }
        assertNotNull(result, "resolveSources should find src.zip at legacy path")
        assertTrue(result.sources.exists())
        assertTrue(result.sources.resolve("java/lang/Object.java").exists())
    }

    @Test
    fun `resolveSources returns null when srcZip is not found`() = runTest {
        val cacheDir = tempDir.resolve("cache").createDirectories()
        val jdkHome = tempDir.resolve("empty-jdk").createDirectories()
        // No src.zip created

        val service = createService(cacheDir)

        val result = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString())
        }
        assertNull(result, "resolveSources should return null when src.zip is not found")
    }

    @Test
    fun `resolveSources uses stable SHA256-like hash as cache key`() = runTest {
        val cacheDir = tempDir.resolve("cache").createDirectories()
        val jdkHome = tempDir.resolve("jdk21")
        val libDir = jdkHome.resolve("lib").createDirectories()
        createSrcZip(libDir, "java/lang/String.java" to "package java.lang; public class String {}")

        val service = createService(cacheDir)

        val result = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString())
        }
        assertNotNull(result)
        assertEquals(64, result.hash.length)
        assertTrue(result.hash.all { it in '0'..'9' || it in 'a'..'f' })

        val result2 = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString())
        }
        assertNotNull(result2)
        assertEquals(result.hash, result2.hash)
    }

    @Test
    fun `resolveSources with forceDownload rebuilds completed CAS entry`() = runTest {
        val cacheDir = tempDir.resolve("cache").createDirectories()
        val jdkHome = tempDir.resolve("jdk21")
        val libDir = jdkHome.resolve("lib").createDirectories()
        createSrcZip(libDir, "java/lang/String.java" to "package java.lang; public class String {}")

        val service = createService(cacheDir)

        // First call
        val result1 = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString())
        }
        assertNotNull(result1)
        val readerVisibleFile = result1.sources.resolve("reader-visible.txt")
        readerVisibleFile.writeText("active-reader")

        // Match dependency-source forceDownload behavior: rebuild the completed CAS entry in place.
        val result2 = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString(), forceDownload = true)
        }
        assertNotNull(result2)
        assertEquals(result1.sources, result2.sources)
        assertTrue(result2.sources.exists())
        assertTrue(!readerVisibleFile.exists())
    }

    @Test
    fun `resolveSources with fresh reindexes`() = runTest {
        val cacheDir = tempDir.resolve("cache").createDirectories()
        val jdkHome = tempDir.resolve("jdk21")
        val libDir = jdkHome.resolve("lib").createDirectories()
        createSrcZip(libDir, "java/lang/String.java" to "package java.lang; public class String {}")

        val service = createService(cacheDir)

        // First call without indexing
        val result1 = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString())
        }
        assertNotNull(result1)

        // Fresh call with a provider should trigger indexing
        val mockProvider = mockk<SearchProvider>(relaxed = true)
        every { mockProvider.markerFileName } returns "test-marker"
        every { mockProvider.name } returns "test"
        every { mockProvider.indexVersion } returns 1

        val result2 = with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString(), fresh = true, providerToIndex = mockProvider)
        }
        assertNotNull(result2)
        assertTrue(result2.index.exists())
        assertTrue(result2.index.resolve(mockProvider.markerFileName).exists())
    }

    @Test
    fun `resolveSources handles concurrent access`() = runTest {
        val cacheDir = tempDir.resolve("cache").createDirectories()
        val jdkHome = tempDir.resolve("jdk21")
        val libDir = jdkHome.resolve("lib").createDirectories()
        createSrcZip(libDir, "java/lang/String.java" to "package java.lang; public class String {}")

        val service = createService(cacheDir)

        val results = coroutineScope {
            (1..5).map {
                async {
                    with(ProgressReporter.NONE) {
                        service.resolveSources(jdkHome.toString())
                    }
                }
            }.awaitAll()
        }

        // All results should be non-null and point to the same directory
        assertTrue(results.all { it != null }, "All concurrent calls should succeed")
        val baseDir = results.first()!!.baseDir
        assertTrue(results.all { it!!.baseDir == baseDir }, "All concurrent calls should return the same directory")
        val result = results.first()!!
        assertTrue(result.baseCompletedMarker.exists())
        assertTrue(result.sources.resolve("java/lang/String.java").exists())
    }

    @Test
    fun `resolveSources handles concurrent forceDownload`() = runTest {
        val cacheDir = tempDir.resolve("cache").createDirectories()
        val jdkHome = tempDir.resolve("jdk21")
        val libDir = jdkHome.resolve("lib").createDirectories()
        createSrcZip(libDir, "java/lang/String.java" to "package java.lang; public class String {}")

        val service = createService(cacheDir)

        // First, populate the cache
        with(ProgressReporter.NONE) {
            service.resolveSources(jdkHome.toString())
        }

        val results = coroutineScope {
            (1..3).map {
                async {
                    with(ProgressReporter.NONE) {
                        service.resolveSources(jdkHome.toString(), forceDownload = true)
                    }
                }
            }.awaitAll()
        }

        // All results should be non-null
        assertTrue(results.all { it != null }, "All concurrent force downloads should succeed")
        val baseDir = results.first()!!.baseDir
        assertTrue(results.all { it!!.baseDir == baseDir }, "All concurrent force downloads should return the same directory")
        val result = results.first()!!
        assertTrue(result.baseCompletedMarker.exists())
        assertTrue(result.sources.resolve("java/lang/String.java").exists())
    }
}
