package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.markerFileName
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleSourceServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `resolved Gradle sources index on demand once under concurrent callers`() = runTest {
        val environment = GradleMcpEnvironment(tempDir.resolve("working"))
        val storageService = DefaultSourceStorageService(environment)
        val indexService = mockk<IndexService>(relaxed = true)
        val provider = mockk<SearchProvider>(relaxed = true)
        every { provider.name } returns "declarations"
        every { provider.indexVersion } returns 1
        coEvery {
            with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), provider) }
        } answers {
            val indexBaseDir = invocation.args[1] as Path
            indexBaseDir.resolve("index").resolve(provider.name).createDirectories()
            indexBaseDir.resolve("index").resolve(provider.markerFileName).createParentDirectories().createFile()
            Index(indexBaseDir.resolve("index"))
        }
        val service = DefaultGradleSourceService(
            environment = environment,
            storageService = storageService,
            indexService = indexService,
            httpClient = mockk<HttpClient>(relaxed = true),
            versionService = mockk<GradleVersionService>(relaxed = true)
        )
        val storagePath = environment.cacheDir.resolve("gradle-sources/9.0").createDirectories()
        val sources = MergedSourcesDir(
            storagePath = storagePath,
            sourcesPath = storagePath.resolve("sources").createDirectories(),
            metadataPath = storagePath.resolve("metadata").createDirectories()
        )
        sources.sources.resolve("Project.java").createFile()
        storagePath.resolve(".completed").createFile()

        assertFalse(sources.index.resolve(provider.markerFileName).exists(), "Plain resolved Gradle sources must start without an index marker")

        (1..3).map {
            async { with(ProgressReporter.NONE) { service.ensureIndexed(sources, provider) } }
        }.awaitAll()

        assertTrue(sources.index.resolve(provider.markerFileName).exists(), "On-demand indexing must publish the provider marker")
        assertTrue(sources.index.resolve(provider.name).exists(), "On-demand indexing must publish the provider directory")
        coVerify(exactly = 1) {
            with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), provider) }
        }
    }
}
