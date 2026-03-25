package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalPathApi::class)
class SourceLockingTest {

    private lateinit var depService: GradleDependencyService
    private lateinit var indexService: IndexService
    private lateinit var storageService: SourceStorageService
    private lateinit var sourceIndexService: SourceIndexService
    private lateinit var environment: GradleMcpEnvironment
    private lateinit var sourcesService: DefaultSourcesService
    private lateinit var tempDir: Path
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("source-locking-test")
        environment = GradleMcpEnvironment(tempDir)
        depService = mockk()
        indexService = mockk()
        storageService = DefaultSourceStorageService(environment)
        sourceIndexService = DefaultSourceIndexService(indexService, storageService, testDispatcher)
        sourcesService = DefaultSourcesService(depService, storageService, sourceIndexService, testDispatcher)
    }

    @AfterEach
    fun cleanup() {
        var retries = 5
        while (retries > 0) {
            try {
                // Use a custom walk to handle links correctly during deletion if needed, 
                // though deleteRecursively should handle it. The main issue is often file locks.
                tempDir.toFile().deleteRecursively()
                if (!tempDir.toFile().exists()) return
            } catch (e: Exception) {
                retries--
                Thread.sleep(200)
            }
        }
    }

    @Test
    fun `resolveAndProcessAllSources prevents concurrent extraction`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("project")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourceFile = tempDir.resolve("test-sources.jar")
        java.util.zip.ZipOutputStream(sourceFile.toFile().outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("foo.txt"))
            it.write("bar".toByteArray())
            it.closeEntry()
        }

        val dep = GradleDependency(
            id = "test:test:1.0",
            group = "test",
            name = "test",
            version = "1.0",
            sourcesFile = sourceFile
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } coAnswers {
            delay(500)
            GradleDependencyReport(
                listOf(
                    GradleProjectDependencies(
                        path = ":",
                        sourceSets = emptyList(),
                        repositories = emptyList(),
                        configurations = listOf(
                            GradleConfigurationDependencies(
                                name = "implementation",
                                description = null,
                                isResolvable = true,
                                extendsFrom = emptyList(),
                                dependencies = listOf(dep)
                            )
                        )
                    )
                )
            )
        }

        coEvery {
            with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), any(), any()) }
        } returns null
        coEvery { indexService.isMergeUpToDate(any(), any(), any()) } returns true
        coEvery {
            with(any<ProgressReporter>()) { indexService.mergeIndices(any(), any(), any(), any()) }
        } returns Unit

        // Start multiple concurrent requests
        val jobs = List(3) {
            async {
                with(ProgressReporter.PRINTLN) {
                    sourcesService.resolveAndProcessAllSources(projectRoot, fresh = false)
                }
            }
        }

        jobs.awaitAll()

        coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadAllSources(projectRoot) } }
    }

    @Test
    fun `resolveAndProcessAllSources allows parallel readers when cached`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("project-parallel")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourceFile = tempDir.resolve("test-sources-parallel.jar")
        java.util.zip.ZipOutputStream(sourceFile.toFile().outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("foo.txt"))
            it.write("bar".toByteArray())
            it.closeEntry()
        }

        val dep = GradleDependency(
            id = "test:test:1.0",
            group = "test",
            name = "test",
            version = "1.0",
            sourcesFile = sourceFile
        )

        val report = GradleDependencyReport(
            listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "implementation",
                            description = null,
                            isResolvable = true,
                            extendsFrom = emptyList(),
                            dependencies = listOf(dep)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns report
        coEvery {
            with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), any(), any()) }
        } returns null
        coEvery { indexService.isMergeUpToDate(any(), any(), any()) } returns true
        coEvery {
            with(any<ProgressReporter>()) { indexService.mergeIndices(any(), any(), any(), any()) }
        } returns Unit

        // 1. Initial download to populate cache
        with(ProgressReporter.PRINTLN) {
            sourcesService.resolveAndProcessAllSources(projectRoot, fresh = true)
        }
        coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadAllSources(projectRoot) } }

        // 2. Start multiple parallel readers (fresh = false)
        // They should all acquire shared lock and return immediately without blocking each other
        val jobs = List(5) {
            async {
                with(ProgressReporter.PRINTLN) {
                    sourcesService.resolveAndProcessAllSources(projectRoot, fresh = false)
                }
            }
        }

        jobs.awaitAll()

        // No additional calls to depService
        coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadAllSources(projectRoot) } }
    }
}
