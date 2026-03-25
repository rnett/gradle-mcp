package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class SingleDependencySourcesTest {

    private lateinit var depService: GradleDependencyService
    private lateinit var indexService: SourceIndexService
    private lateinit var storageService: SourceStorageService
    private lateinit var sourcesService: DefaultSourcesService
    private lateinit var environment: GradleMcpEnvironment
    private lateinit var tempDir: Path
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("single-dep-test")
        environment = GradleMcpEnvironment(tempDir)
        depService = mockk()
        indexService = mockk()
        storageService = DefaultSourceStorageService(environment)
        sourcesService = DefaultSourcesService(depService, storageService, indexService, testDispatcher)

        coEvery { with(any<ProgressReporter>()) { indexService.indexDependency(any(), any(), any(), any()) } } returns dev.rnett.gradle.mcp.dependencies.search.Index(kotlin.io.path.Path("dummy"))
        coEvery { with(any<ProgressReporter>()) { indexService.ensureMergeUpToDate(any(), any(), any()) } } returns true
        coEvery { with(any<ProgressReporter>()) { indexService.mergeIndices(any(), any(), any(), any()) } } returns Unit
    }

    @AfterEach
    fun cleanup() {
        var retries = 5
        while (retries > 0) {
            try {
                tempDir.toFile().deleteRecursively()
                if (!tempDir.toFile().exists()) return
            } catch (e: Exception) {
                retries--
                Thread.sleep(200)
            }
        }
    }

    private fun createZip(name: String): Path {
        val path = tempDir.resolve(name)
        java.util.zip.ZipOutputStream(path.toFile().outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("foo.txt"))
            it.write("bar".toByteArray())
            it.closeEntry()
        }
        return path
    }

    @Test
    fun `resolveAndProcessAllSources with exact filter matches single dependency`() = runTest(testDispatcher) {
        val zip = createZip("test.zip")
        val dep = GradleDependency(
            id = "test:test:1.0",
            group = "test",
            name = "test",
            version = "1.0",
            sourcesFile = zip
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

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report

        val resultDir = with(ProgressReporter.NONE) {
            sourcesService.resolveAndProcessAllSources(mockk(relaxed = true), dependency = "test:test", index = true, providerToIndex = FullTextSearch)
        }

        assertNotNull(resultDir)
        // For single dependency, resultDir.sources is the extraction directory itself
        assertTrue(resultDir.sources.toString().replace('\\', '/').contains("test/test"))
    }

    @Test
    fun `resolveAndProcessAllSources with group filter matches multiple dependencies`() = runTest(testDispatcher) {
        val zip1 = createZip("test1.zip")
        val zip2 = createZip("test2.zip")
        val dep1 = GradleDependency(
            id = "test:test1:1.0",
            group = "test",
            name = "test1",
            version = "1.0",
            sourcesFile = zip1
        )
        val dep2 = GradleDependency(
            id = "test:test2:1.0",
            group = "test",
            name = "test2",
            version = "1.0",
            sourcesFile = zip2
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
                            dependencies = listOf(dep1, dep2)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report

        val resultDir = with(ProgressReporter.NONE) {
            sourcesService.resolveAndProcessAllSources(mockk(relaxed = true), dependency = "test", index = true, providerToIndex = FullTextSearch)
        }

        assertNotNull(resultDir)
        // Should return project-level merged directory, not single-dep directory
        // The merged directory uses hashes, so we don't check for "root" string anymore.
        assertTrue(resultDir.sources.resolve("test/test1").toFile().exists() || resultDir.sources.resolve("test/test1").isSymbolicLink())
        assertTrue(resultDir.sources.resolve("test/test2").toFile().exists() || resultDir.sources.resolve("test/test2").isSymbolicLink())
    }

    @Test
    fun `resolveAndProcessAllSources without filter matches all dependencies`() = runTest(testDispatcher) {
        val zip = createZip("test.zip")
        val dep = GradleDependency(
            id = "test:test:1.0",
            group = "test",
            name = "test",
            version = "1.0",
            sourcesFile = zip
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

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report

        val resultDir = with(ProgressReporter.NONE) {
            sourcesService.resolveAndProcessAllSources(mockk(relaxed = true), dependency = null, index = true, providerToIndex = FullTextSearch)
        }

        assertNotNull(resultDir)
        // The merged directory uses hashes, so we don't check for "root" string anymore.
        assertTrue(resultDir.sources.exists() || resultDir.sources.isSymbolicLink())
    }

    @Test
    fun `resolveAndProcessAllSources with non-existent filter throws exception`() = runTest(testDispatcher) {
        val report = GradleDependencyReport(emptyList())
        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            with(ProgressReporter.NONE) {
                sourcesService.resolveAndProcessAllSources(mockk(relaxed = true), dependency = "not:existent", index = false)
            }
        }
    }

    @Test
    fun `resolveAndProcessAllSources with filter matching only deps without sources throws exception`() = runTest(testDispatcher) {
        val dep = GradleDependency(
            id = "test:no-sources:1.0",
            group = "test",
            name = "no-sources",
            version = "1.0",
            sourcesFile = null
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

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report

        val exception = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            with(ProgressReporter.NONE) {
                sourcesService.resolveAndProcessAllSources(mockk(relaxed = true), dependency = "test:no-sources:1.0", index = false)
            }
        }
        assertTrue(exception.message!!.contains("does not have sources available"))
    }
}
