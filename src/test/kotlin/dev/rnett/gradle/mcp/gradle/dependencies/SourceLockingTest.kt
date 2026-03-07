package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.gradle.dependencies.search.IndexService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalPathApi::class)
class SourceLockingTest {

    private lateinit var depService: GradleDependencyService
    private lateinit var indexService: IndexService
    private lateinit var environment: GradleMcpEnvironment
    private lateinit var sourcesService: DefaultSourcesService
    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("source-locking-test")
        environment = GradleMcpEnvironment(tempDir)
        depService = mockk()
        indexService = mockk()
        sourcesService = DefaultSourcesService(depService, environment, indexService)
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `downloadAllSources prevents concurrent extraction`() = kotlinx.coroutines.runBlocking {
        val projectRootPath = tempDir.resolve("project")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourceFile = tempDir.resolve("test-sources.jar")
        sourceFile.createFile()

        val dep = GradleDependency(
            id = "test:test:1.0",
            group = "test",
            name = "test",
            version = "1.0",
            sourcesFile = sourceFile
        )

        coEvery { depService.downloadAllSources(any()) } coAnswers {
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
            with(any<dev.rnett.gradle.mcp.ProgressReporter>()) {
                indexService.index(any(), any())
            }
        } returns null
        coEvery {
            with(any<dev.rnett.gradle.mcp.ProgressReporter>()) {
                indexService.mergeIndices(any(), any())
            }
        } returns Unit

        // Start multiple concurrent requests
        val jobs = List(3) {
            async(Dispatchers.IO) {
                with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
                    sourcesService.downloadAllSources(projectRoot, fresh = false)
                }
            }
        }

        jobs.awaitAll()

        coVerify(exactly = 1) { depService.downloadAllSources(projectRoot) }
    }

    @Test
    fun `downloadAllSources allows parallel readers when cached`() = kotlinx.coroutines.runBlocking {
        val projectRootPath = tempDir.resolve("project-parallel")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourceFile = tempDir.resolve("test-sources-parallel.jar")
        sourceFile.createFile()

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

        coEvery { depService.downloadAllSources(any()) } returns report
        coEvery {
            with(any<dev.rnett.gradle.mcp.ProgressReporter>()) {
                indexService.index(any(), any())
            }
        } returns null
        coEvery {
            with(any<dev.rnett.gradle.mcp.ProgressReporter>()) {
                indexService.mergeIndices(any(), any())
            }
        } returns Unit

        // 1. Initial download to populate cache
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            sourcesService.downloadAllSources(projectRoot, fresh = true)
        }
        coVerify(exactly = 1) { depService.downloadAllSources(projectRoot) }

        // 2. Start multiple parallel readers (fresh = false)
        // They should all acquire shared lock and return immediately without blocking each other
        val jobs = List(5) {
            async(Dispatchers.IO) {
                with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
                    sourcesService.downloadAllSources(projectRoot, fresh = false)
                }
            }
        }

        jobs.awaitAll()

        // No additional calls to depService
        coVerify(exactly = 1) { depService.downloadAllSources(projectRoot) }
    }
}
