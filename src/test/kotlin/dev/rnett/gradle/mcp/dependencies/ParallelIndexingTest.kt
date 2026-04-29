package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.markerFileName
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelIndexingTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test concurrent indexing of different providers`() = runTest {
        val env = GradleMcpEnvironment(tempDir.resolve("mcp"))
        val depService = mockk<GradleDependencyService>()
        val storageService = DefaultSourceStorageService(env)
        val indexService = mockk<IndexService>()
        io.mockk.coEvery { indexService.invalidateAllCaches(any()) } just io.mockk.Runs
        val sourcesService = DefaultSourcesService(depService, storageService, indexService)

        val projectRoot = GradleProjectRoot(tempDir.resolve("project").createDirectories().toString())

        val zipFile = tempDir.resolve("sources.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Test.kt"))
            zip.write("class Test".toByteArray())
            zip.closeEntry()
        }

        val dep = GradleDependency(
            id = "com.example:test:1.0",
            group = "com.example",
            name = "test",
            version = "1.0",
            sourcesFile = zipFile
        )

        val report = GradleDependencyReport(
            listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "compile",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(dep)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any(), any(), any(), any()) } } returns report.projects.first()

        val provider1 = mockk<SearchProvider>(relaxed = true)
        every { provider1.name } returns "provider1"
        every { provider1.markerFileName } returns ".p1"
        every { provider1.indexVersion } returns 1
        every { provider1.resolveIndexDir(any()) } returns tempDir.resolve("idx1")
        every { provider1.invalidateCache(any()) } just Runs

        val provider2 = mockk<SearchProvider>(relaxed = true)
        every { provider2.name } returns "provider2"
        every { provider2.markerFileName } returns ".p2"
        every { provider2.indexVersion } returns 1
        every { provider2.resolveIndexDir(any()) } returns tempDir.resolve("idx2")
        every { provider2.invalidateCache(any()) } just Runs

        // Mock indexing to take some time and write the marker and directory
        coEvery { with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), eq(provider1)) } } coAnswers {
            println("MOCK: Indexing p1")
            val dir = it.invocation.args[1] as Path
            val indexDir = dir.resolve("index")
            val providerDir = indexDir.resolve("provider1")
            providerDir.createDirectories()
            val marker = indexDir.resolve(provider1.markerFileName)
            marker.createFile()
            delay(1000)
            Index(indexDir)
        }
        coEvery { with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), eq(provider2)) } } coAnswers {
            println("MOCK: Indexing p2")
            val dir = it.invocation.args[1] as Path
            val indexDir = dir.resolve("index")
            val providerDir = indexDir.resolve("provider2")
            providerDir.createDirectories()
            val marker = indexDir.resolve(provider2.markerFileName)
            marker.createFile()
            delay(1000)
            Index(indexDir)
        }

        // 1. First call to ensure base is ready (or just run them in parallel)
        val job1 = async {
            with(ProgressReporter.NONE) {
                sourcesService.resolveAndProcessProjectSources(projectRoot, ":", providerToIndex = provider1)
            }
        }

        delay(200) // Ensure job1 starts and gets the base lock

        val job2 = async {
            with(ProgressReporter.NONE) {
                sourcesService.resolveAndProcessProjectSources(projectRoot, ":", providerToIndex = provider2)
            }
        }

        val results = listOf(job1.await(), job2.await())
        assertEquals(2, results.size)

        val hash = storageService.calculateHash(dep)
        val casDir = storageService.getCASDependencySourcesDir(hash)
        assertTrue(casDir.baseCompletedMarker.exists())
        assertTrue(casDir.index.resolve(provider1.markerFileName).exists())
        assertTrue(casDir.index.resolve(provider2.markerFileName).exists())
    }
}
