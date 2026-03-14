package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.search.IndexEntry
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText

class SourcesServiceTest {

    private lateinit var depService: GradleDependencyService
    private lateinit var indexService: IndexService
    private lateinit var environment: GradleMcpEnvironment
    private lateinit var sourcesService: DefaultSourcesService
    private lateinit var tempDir: Path

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("sources-service-test")
        environment = GradleMcpEnvironment(tempDir)
        depService = mockk(relaxed = true)
        indexService = mockk()
        coEvery { with(any<ProgressReporter>()) { indexService.indexFiles(any(), any()) } } returns null
        coEvery { with(any<ProgressReporter>()) { indexService.index(any(), any()) } } returns null
        coEvery { indexService.search(any(), any(), any(), any()) } returns dev.rnett.gradle.mcp.dependencies.search.SearchResponse(emptyList())
        coEvery { indexService.listPackageContents(any(), any()) } returns null
        coEvery { with(any<ProgressReporter>()) { indexService.mergeIndices(any(), any()) } } returns Unit

        sourcesService = DefaultSourcesService(depService, environment, indexService)
    }

    @Test
    fun `downloadAllSources reports granular progress during processing`() = runTest {
        val projectRootPath = tempDir.resolve("project-granular")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourcesFile = tempDir.resolve("lib-sources.jar")
        java.util.zip.ZipOutputStream(sourcesFile.toFile().outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("foo.txt"))
            it.write("bar".toByteArray())
            it.closeEntry()
        }

        val mockDep = dev.rnett.gradle.mcp.dependencies.model.GradleDependency(
            id = "com.example:lib:1.0.0",
            group = "com.example",
            name = "lib",
            version = "1.0.0",
            sourcesFile = sourcesFile
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns GradleDependencyReport(
            listOf(
                dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies(
                            name = "main",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(mockDep)
                        )
                    )
                )
            )
        )

        val progressMessages = java.util.concurrent.ConcurrentLinkedQueue<String?>()
        val reporter = ProgressReporter { _, _, msg -> progressMessages.add(msg) }

        with(reporter) {
            sourcesService.downloadAllSources(projectRoot, fresh = true)
        }

        assertTrue(progressMessages.any { it?.contains("Extracting sources for com.example:lib:1.0.0") == true }, "Should report extraction")
        assertTrue(progressMessages.any { it?.contains("Indexing sources for com.example:lib:1.0.0") == true }, "Should report indexing")
    }

    @Test
    fun `downloadAllSources skips resolution when fresh is false and cache exists`() = runTest {
        with(ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            // Setup existing cache
            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + "".hashCode().toString() + "root".hashCode().toString())
            val sourcesDir = dir.resolve("sources")
            sourcesDir.createDirectories()

            val result = sourcesService.downloadAllSources(projectRoot, fresh = false)

            assertNotNull(result)
            coVerify(exactly = 0) { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } }
        }
    }

    @Test
    fun `downloadAllSources performs resolution when fresh is true even if cache exists`() = runTest {
        with(ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            // Setup existing cache
            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + "".hashCode().toString() + "root".hashCode().toString())
            dir.resolve("sources").createDirectories()

            coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns GradleDependencyReport(emptyList())

            sourcesService.downloadAllSources(projectRoot, fresh = true)

            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadAllSources(projectRoot) } }
        }
    }

    @Test
    fun `downloadAllSources performs resolution when cache does not exist even if fresh is false`() = runTest {
        with(ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns GradleDependencyReport(emptyList())

            sourcesService.downloadAllSources(projectRoot, fresh = false)

            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadAllSources(projectRoot) } }
        }
    }

    @Test
    fun `downloadAllSources writes last refresh timestamp`() = runTest {
        with(ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns GradleDependencyReport(emptyList())

            val result = sourcesService.downloadAllSources(projectRoot, fresh = true)

            assertTrue(result.lastRefreshFile.exists())
            val timestamp = result.lastRefreshFile.readText()
            assertNotNull(kotlin.time.Instant.parse(timestamp))
        }
    }

    @Test
    fun `downloadProjectSources respects fresh parameter`() = runTest {
        with(ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project-p")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + ":app".hashCode().toString() + "project".hashCode().toString())
            dir.resolve("sources").createDirectories()

            sourcesService.downloadProjectSources(projectRoot, ":app", fresh = false)
            coVerify(exactly = 0) { with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any()) } }

            coEvery { with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any()) } } returns mockk(relaxed = true)
            sourcesService.downloadProjectSources(projectRoot, ":app", fresh = true)
            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadProjectSources(projectRoot, ":app") } }
        }
    }

    @Test
    fun `downloadConfigurationSources respects fresh parameter`() = runTest {
        with(ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project-c")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + ":app:implementation".hashCode().toString() + "configuration".hashCode().toString())
            dir.resolve("sources").createDirectories()

            sourcesService.downloadConfigurationSources(projectRoot, ":app:implementation", fresh = false)
            coVerify(exactly = 0) { with(any<ProgressReporter>()) { depService.downloadConfigurationSources(any(), any()) } }

            coEvery { with(any<ProgressReporter>()) { depService.downloadConfigurationSources(any(), any()) } } returns mockk(relaxed = true)
            sourcesService.downloadConfigurationSources(projectRoot, ":app:implementation", fresh = true)
            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadConfigurationSources(projectRoot, ":app:implementation") } }
        }
    }

    @Test
    fun `downloadSourceSetSources respects fresh parameter`() = runTest {
        with(ProgressReporter.NONE) {
            val projectRootPath = tempDir.resolve("project-s")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            val dir = sourcesService.sourcesDir.resolve(projectRoot.projectRoot.hashCode().toString() + ":app:main".hashCode().toString() + "sourceSet".hashCode().toString())
            dir.resolve("sources").createDirectories()

            sourcesService.downloadSourceSetSources(projectRoot, ":app:main", fresh = false)
            coVerify(exactly = 0) { with(any<ProgressReporter>()) { depService.downloadSourceSetSources(any(), any()) } }

            coEvery { with(any<ProgressReporter>()) { depService.downloadSourceSetSources(any(), any()) } } returns mockk(relaxed = true)
            sourcesService.downloadSourceSetSources(projectRoot, ":app:main", fresh = true)
            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadSourceSetSources(projectRoot, ":app:main") } }
        }
    }

    @Test
    fun `downloadAllSources fails if indexing fails`() = runTest {
        val projectRootPath = tempDir.resolve("project-fail")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourcesFile = tempDir.resolve("lib-sources-fail.jar")
        java.util.zip.ZipOutputStream(sourcesFile.toFile().outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("foo.txt"))
            it.write("bar".toByteArray())
            it.closeEntry()
        }

        val mockDep = dev.rnett.gradle.mcp.dependencies.model.GradleDependency(
            id = "com.example:lib:1.0.0",
            group = "com.example",
            name = "lib",
            version = "1.0.0",
            sourcesFile = sourcesFile
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns GradleDependencyReport(
            listOf(
                dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies(
                            name = "main",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(mockDep)
                        )
                    )
                )
            )
        )

        coEvery {
            with(any<ProgressReporter>()) {
                indexService.indexFiles(any(), any<kotlinx.coroutines.flow.Flow<IndexEntry>>())
            }
        } throws RuntimeException("Indexing failed")

        assertThrows(RuntimeException::class.java) {
            runTest {
                with(ProgressReporter.NONE) {
                    sourcesService.downloadAllSources(projectRoot, index = true)
                }
            }
        }
    }
}
