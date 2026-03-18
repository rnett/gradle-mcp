package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SingleDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink

@OptIn(ExperimentalPathApi::class)
class SingleDependencySourcesTest {

    private lateinit var depService: GradleDependencyService
    private lateinit var indexService: IndexService
    private lateinit var environment: GradleMcpEnvironment
    private lateinit var sourcesService: DefaultSourcesService
    private lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("single-dep-test")
        environment = GradleMcpEnvironment(tempDir)
        depService = mockk()
        indexService = mockk()
        sourcesService = DefaultSourcesService(depService, environment, indexService)
    }

    @AfterEach
    fun cleanup() {
        tempDir.deleteRecursively()
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
    fun `downloadAllSources with single dependency uses isolated storage and symlinks`() = runTest {
        val projectRootPath = tempDir.resolve("project")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourceFile = createZip("test-sources.jar")
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

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report
        coEvery { with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), any()) } } returns null
        coEvery { with(any<ProgressReporter>()) { indexService.index(any(), any()) } } returns null

        val resultDir = with(ProgressReporter.PRINTLN) {
            sourcesService.downloadAllSources(projectRoot, dependency = "test:test:1.0", index = false)
        }

        // Verify it's marked as single dependency
        assertTrue(resultDir is SingleDependencySourcesDir)

        // Verify symlink to global storage
        val globalDir = environment.cacheDir.resolve("extracted-sources").resolve("test").resolve("test-sources")
        assertEquals(globalDir, resultDir.sources)
        assertTrue(globalDir.exists())
    }

    @Test
    fun `downloadAllSources with group filter matches multiple dependencies`() = runTest {
        val projectRootPath = tempDir.resolve("project-group")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourceFile1 = createZip("test1-sources.jar")
        val dep1 = GradleDependency(
            id = "test:test1:1.0",
            group = "test",
            name = "test1",
            version = "1.0",
            sourcesFile = sourceFile1
        )

        val sourceFile2 = createZip("test2-sources.jar")
        val dep2 = GradleDependency(
            id = "test:test2:1.0",
            group = "test",
            name = "test2",
            version = "1.0",
            sourcesFile = sourceFile2
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
                            dependencies = listOf(dep1, dep2)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report
        coEvery { with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), any()) } } returns null
        coEvery { with(any<ProgressReporter>()) { indexService.index(any(), any()) } } returns null
        coEvery { with(any<ProgressReporter>()) { indexService.mergeIndices(any(), any()) } } returns Unit

        val resultDir = with(ProgressReporter.PRINTLN) {
            sourcesService.downloadAllSources(projectRoot, dependency = "test", index = false)
        }

        // Verify it's NOT marked as single dependency because there are multiple matches
        assertTrue(resultDir is MergedSourcesDir)

        // Verify sources dir is a real directory containing symlinks to multiple deps
        assertFalse(resultDir.sources.isSymbolicLink())
        assertTrue(resultDir.sources.resolve("test").resolve("test1-sources").exists())
        assertTrue(resultDir.sources.resolve("test").resolve("test2-sources").exists())
    }

    @Test
    fun `downloadAllSources with single dependency isolates transitives`() = runTest {
        val projectRootPath = tempDir.resolve("project-transitives")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourceFileMain = createZip("main-sources.jar")
        val depMain = GradleDependency(
            id = "test:main:1.0",
            group = "test",
            name = "main",
            version = "1.0",
            sourcesFile = sourceFileMain
        )

        val sourceFileTransitive = createZip("transitive-sources.jar")
        val depTransitive = GradleDependency(
            id = "test:transitive:1.0",
            group = "test",
            name = "transitive",
            version = "1.0",
            sourcesFile = sourceFileTransitive
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
                            dependencies = listOf(depMain, depTransitive)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report
        coEvery { with(any<ProgressReporter>()) { indexService.indexFiles(any(), any(), any()) } } returns null
        coEvery { with(any<ProgressReporter>()) { indexService.index(any(), any()) } } returns null

        val resultDir = with(ProgressReporter.PRINTLN) {
            sourcesService.downloadAllSources(projectRoot, dependency = "test:main:1.0", index = false)
        }

        // Verify it's marked as single dependency
        assertTrue(resultDir is SingleDependencySourcesDir)

        // Verify symlink to global storage is JUST the main dependency
        val globalDirMain = environment.cacheDir.resolve("extracted-sources").resolve("test").resolve("main-sources")
        assertEquals(globalDirMain, resultDir.sources)
        assertTrue(globalDirMain.exists())

        // Transitive should NOT have been extracted since it was excluded
        val globalDirTransitive = environment.cacheDir.resolve("extracted-sources").resolve("test").resolve("transitive-sources")
        assertFalse(globalDirTransitive.exists())
    }

    @Test
    fun `downloadAllSources with filter matching nothing throws exception`() = runTest {
        val projectRootPath = tempDir.resolve("project-none")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val depDummy = GradleDependency(
            id = "test:dummy:1.0",
            group = "test",
            name = "dummy",
            version = "1.0",
            sourcesFile = createZip("dummy-sources.jar")
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
                            dependencies = listOf(depDummy)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            with(ProgressReporter.PRINTLN) {
                sourcesService.downloadAllSources(projectRoot, dependency = "not:existent", index = false)
            }
        }
    }

    @Test
    fun `downloadAllSources with filter matching dependency without sources throws exception`() = runTest {
        val projectRootPath = tempDir.resolve("project-no-sources")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val depNoSources = GradleDependency(
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
                            name = "implementation",
                            description = null,
                            isResolvable = true,
                            extendsFrom = emptyList(),
                            dependencies = listOf(depNoSources)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any(), any()) } } returns report

        val exception = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            with(ProgressReporter.PRINTLN) {
                sourcesService.downloadAllSources(projectRoot, dependency = "test:no-sources:1.0", index = false)
            }
        }

        assertTrue(exception.message!!.contains("sources available"))
    }
}
