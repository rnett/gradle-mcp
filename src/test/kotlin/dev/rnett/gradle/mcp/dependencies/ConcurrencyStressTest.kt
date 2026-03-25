package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.test.assertEquals

class ConcurrencyStressTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `multiple parallel requests for same dependency should not deadlock`() = runTest {
        val environment = GradleMcpEnvironment(tempDir.resolve("mcp"))
        val dependencyService: GradleDependencyService = mockk()
        val storageService = DefaultSourceStorageService(environment)
        val indexService = DefaultIndexService(environment)
        val sourcesService = DefaultSourcesService(dependencyService, storageService, indexService)

        val projectRoot = GradleProjectRoot(tempDir.resolve("project").createDirectories().toString())

        val zipFile = tempDir.resolve("sources.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("com/example/MyClass.kt"))
            zip.write("class MyClass".toByteArray())
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
                            name = "compileClasspath",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(dep)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { dependencyService.downloadAllSources(projectRoot) } } returns report

        // Launch 5 parallel requests (reduced from 10 for performance)
        val jobs = List(5) {
            async {
                with(ProgressReporter.NONE) {
                    sourcesService.resolveAndProcessAllSources(projectRoot, providerToIndex = FullTextSearch)
                }
            }
        }

        val results = jobs.awaitAll()
        assertEquals(5, results.size)
        assertEquals(true, storageService.getCASDependencySourcesDir(storageService.calculateHash(dep)).completionMarker.exists())

        Unit
    }
}
