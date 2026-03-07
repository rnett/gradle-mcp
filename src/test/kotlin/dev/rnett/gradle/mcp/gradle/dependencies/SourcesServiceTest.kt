package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.gradle.dependencies.search.IndexService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SourcesServiceTest {

    private lateinit var depService: GradleDependencyService
    private lateinit var indexService: IndexService
    private lateinit var environment: GradleMcpEnvironment
    private lateinit var sourcesService: DefaultSourcesService
    private lateinit var tempDir: Path

    @OptIn(ExperimentalPathApi::class)
    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("sources-service-test")
        environment = GradleMcpEnvironment(tempDir)
        depService = mockk()
        indexService = mockk()
        sourcesService = DefaultSourcesService(depService, environment, indexService)
    }

    @Test
    fun `downloadAllSources skips resolution when fresh is false and cache exists`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            // Setup existing cache
            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + "".hashCode().toString() + "root".hashCode().toString())
            val sourcesDir = dir.resolve("sources")
            sourcesDir.createDirectories()

            val result = sourcesService.downloadAllSources(projectRoot, fresh = false)

            assertNotNull(result)
            coVerify(exactly = 0) { depService.downloadAllSources(any()) }
        }
    }

    @Test
    fun `downloadAllSources performs resolution when fresh is true even if cache exists`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            // Setup existing cache
            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + "".hashCode().toString() + "root".hashCode().toString())
            dir.resolve("sources").createDirectories()

            coEvery { depService.downloadAllSources(any()) } returns GradleDependencyReport(emptyList())

            sourcesService.downloadAllSources(projectRoot, fresh = true)

            coVerify(exactly = 1) { depService.downloadAllSources(projectRoot) }
        }
    }

    @Test
    fun `downloadAllSources performs resolution when cache does not exist even if fresh is false`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            coEvery { depService.downloadAllSources(any()) } returns GradleDependencyReport(emptyList())

            sourcesService.downloadAllSources(projectRoot, fresh = false)

            coVerify(exactly = 1) { depService.downloadAllSources(projectRoot) }
        }
    }

    @Test
    fun `downloadAllSources writes last refresh timestamp`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            coEvery { depService.downloadAllSources(any()) } returns GradleDependencyReport(emptyList())

            val result = sourcesService.downloadAllSources(projectRoot, fresh = true)

            assertTrue(result.lastRefreshFile.exists())
            val timestamp = result.lastRefreshFile.readText()
            assertNotNull(java.time.Instant.parse(timestamp))
        }
    }

    @Test
    fun `downloadProjectSources respects fresh parameter`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project-p")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + ":app".hashCode().toString() + "project".hashCode().toString())
            dir.resolve("sources").createDirectories()

            sourcesService.downloadProjectSources(projectRoot, ":app", fresh = false)
            coVerify(exactly = 0) { depService.downloadProjectSources(any(), any()) }

            coEvery { depService.downloadProjectSources(any(), any()) } returns mockk(relaxed = true)
            sourcesService.downloadProjectSources(projectRoot, ":app", fresh = true)
            coVerify(exactly = 1) { depService.downloadProjectSources(projectRoot, ":app") }
        }
    }

    @Test
    fun `downloadConfigurationSources respects fresh parameter`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project-c")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + ":app:implementation".hashCode().toString() + "configuration".hashCode().toString())
            dir.resolve("sources").createDirectories()

            sourcesService.downloadConfigurationSources(projectRoot, ":app:implementation", fresh = false)
            coVerify(exactly = 0) { depService.downloadConfigurationSources(any(), any()) }

            coEvery { depService.downloadConfigurationSources(any(), any()) } returns mockk(relaxed = true)
            sourcesService.downloadConfigurationSources(projectRoot, ":app:implementation", fresh = true)
            coVerify(exactly = 1) { depService.downloadConfigurationSources(projectRoot, ":app:implementation") }
        }
    }

    @Test
    fun `downloadSourceSetSources respects fresh parameter`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project-s")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + ":app:main".hashCode().toString() + "sourceSet".hashCode().toString())
            dir.resolve("sources").createDirectories()

            sourcesService.downloadSourceSetSources(projectRoot, ":app:main", fresh = false)
            coVerify(exactly = 0) { depService.downloadSourceSetSources(any(), any()) }

            coEvery { depService.downloadSourceSetSources(any(), any()) } returns mockk(relaxed = true)
            sourcesService.downloadSourceSetSources(projectRoot, ":app:main", fresh = true)
            coVerify(exactly = 1) { depService.downloadSourceSetSources(projectRoot, ":app:main") }
        }
    }
}
