package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.search.DeclarationSearch
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertNotNull

@OptIn(ExperimentalPathApi::class)
class DefaultSourcesServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var depService: GradleDependencyService
    private lateinit var indexService: IndexService
    private lateinit var environment: GradleMcpEnvironment
    private lateinit var storageService: DefaultSourceStorageService
    private lateinit var sourceIndexService: DefaultSourceIndexService
    private lateinit var sourcesService: DefaultSourcesService
    private val testDispatcher = UnconfinedTestDispatcher()

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() {
        environment = GradleMcpEnvironment(tempDir)
        depService = mockk(relaxed = true)
        indexService = mockk()
        coEvery { with(any<ProgressReporter>()) { indexService.indexFiles(any<GradleDependency>(), any(), any(), any()) } } answers {
            val reporter = arg<ProgressReporter>(0)
            reporter.report(1.0, 1.0, "Indexing sources for com.example:lib:1.0.0")
            dev.rnett.gradle.mcp.dependencies.search.Index(kotlin.io.path.Path("dummy"))
        }
        coEvery { indexService.search(any(), any(), any(), any()) } returns SearchResponse(emptyList())
        coEvery { indexService.listPackageContents(any(), any()) } returns null
        coEvery { indexService.isMergeUpToDate(any(), any(), any()) } returns true
        coEvery { indexService.isIndexed(any(), any()) } returns false
        coEvery { indexService.getIndex(any(), any()) } returns null
        coEvery { with(any<ProgressReporter>()) { indexService.mergeIndices(any(), any(), any(), any()) } } returns Unit

        storageService = DefaultSourceStorageService(environment)
        sourceIndexService = DefaultSourceIndexService(indexService, storageService, testDispatcher)
        sourcesService = DefaultSourcesService(depService, storageService, sourceIndexService, testDispatcher)
    }

    @Test
    fun `resolveAndProcessAllSources reports granular progress during processing`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("project-granular")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourcesFile = tempDir.resolve("lib-sources.jar")
        java.util.zip.ZipOutputStream(sourcesFile.toFile().outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("com/example/Lib.kt"))
            it.write("class Lib".toByteArray())
            it.closeEntry()
        }

        val dep = GradleDependency(
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
                            name = "compileClasspath",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(dep)
                        )
                    )
                )
            )
        )

        val messages = mutableListOf<String>()
        val progressReporter = ProgressReporter { _, _, message ->
            if (message != null) messages.add(message)
        }

        with(progressReporter) {
            sourcesService.resolveAndProcessAllSources(projectRoot, index = true, providerToIndex = FullTextSearch)
        }

        // Use Power Assert via assert
        assertTrue(messages.any { it.contains("[DETECTING]") })
        assertTrue(messages.any { it.contains("Indexing sources for com.example:lib:1.0.0") })
    }

    @Test
    fun `resolveAndProcessAllSources respects fresh parameter`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("project-fresh")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        with(ProgressReporter.NONE) {
            // 1. Setup existing cache
            val dir = storageService.getMergedSourcesDir(projectRoot, "", "root").storagePath
            dir.createDirectories()
            dir.resolve(".dependencies.hash").writeText("any-hash")
            dir.resolve(".dependencies.json").writeText("{}")
            dir.resolve("sources").createDirectories()

            val result = sourcesService.resolveAndProcessAllSources(projectRoot, fresh = false)

            assertNotNull(result)
            coVerify(exactly = 0) { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } }
        }
    }

    @Test
    fun `resolveAndProcessAllSources performs resolution when fresh is true even if cache exists`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("project-fresh-true")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        with(ProgressReporter.NONE) {
            val dir = storageService.getMergedSourcesDir(projectRoot, "", "root").storagePath
            dir.createDirectories()
            dir.resolve(".dependencies.hash").writeText("any-hash")
            dir.resolve(".dependencies.json").writeText("{}")
            dir.resolve("sources").createDirectories()

            coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns GradleDependencyReport(emptyList())

            sourcesService.resolveAndProcessAllSources(projectRoot, fresh = true)

            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadAllSources(projectRoot) } }
        }
    }

    @Test
    fun `resolveAndProcessAllSources performs resolution when cache does not exist even if fresh is false`() = runTest(testDispatcher) {
        with(ProgressReporter.PRINTLN) {
            val projectRootPath = tempDir.resolve("project")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns GradleDependencyReport(emptyList())

            sourcesService.resolveAndProcessAllSources(projectRoot, fresh = false)

            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadAllSources(projectRoot) } }
        }
    }

    @Test
    fun `resolveAndProcessAllSources writes last refresh timestamp`() = runTest(testDispatcher) {
        with(ProgressReporter.PRINTLN) {
            val projectRootPath = tempDir.resolve("project-refresh-timestamp")
            projectRootPath.createDirectories()
            val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

            coEvery { with(any<ProgressReporter>()) { depService.downloadAllSources(any()) } } returns GradleDependencyReport(emptyList())

            val result = sourcesService.resolveAndProcessAllSources(projectRoot, fresh = true)

            assertNotNull(result.lastRefresh())
        }
    }

    @Test
    fun `resolveAndProcessProjectSources respects fresh parameter`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("project-scoped-fresh")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        with(ProgressReporter.NONE) {
            val dir = storageService.getMergedSourcesDir(projectRoot, ":app", "project").storagePath
            dir.createDirectories()
            dir.resolve(".dependencies.hash").writeText("any-hash")
            dir.resolve("sources").createDirectories()

            sourcesService.resolveAndProcessProjectSources(projectRoot, ":app", fresh = false)
            coVerify(exactly = 0) { with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any()) } }

            coEvery { with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any()) } } returns mockk(relaxed = true)
            sourcesService.resolveAndProcessProjectSources(projectRoot, ":app", fresh = true)
            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadProjectSources(projectRoot, ":app") } }
        }
    }

    @Test
    fun `resolveAndProcessConfigurationSources respects fresh parameter`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("config-scoped-fresh")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        with(ProgressReporter.NONE) {
            val dir = storageService.getMergedSourcesDir(projectRoot, ":app:implementation", "configuration").storagePath
            dir.createDirectories()
            dir.resolve(".dependencies.hash").writeText("any-hash")
            dir.resolve("sources").createDirectories()

            sourcesService.resolveAndProcessConfigurationSources(projectRoot, ":app:implementation", fresh = false)
            coVerify(exactly = 0) { with(any<ProgressReporter>()) { depService.downloadConfigurationSources(any(), any()) } }

            coEvery { with(any<ProgressReporter>()) { depService.downloadConfigurationSources(any(), any()) } } returns mockk(relaxed = true)
            sourcesService.resolveAndProcessConfigurationSources(projectRoot, ":app:implementation", fresh = true)
            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadConfigurationSources(projectRoot, ":app:implementation") } }
        }
    }

    @Test
    fun `resolveAndProcessSourceSetSources respects fresh parameter`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("sourceSet-scoped-fresh")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        with(ProgressReporter.NONE) {
            val dir = storageService.getMergedSourcesDir(projectRoot, ":app:main", "sourceSet").storagePath
            dir.createDirectories()
            dir.resolve(".dependencies.hash").writeText("any-hash")
            dir.resolve("sources").createDirectories()

            sourcesService.resolveAndProcessSourceSetSources(projectRoot, ":app:main", fresh = false)
            coVerify(exactly = 0) { with(any<ProgressReporter>()) { depService.downloadSourceSetSources(any(), any()) } }

            coEvery { with(any<ProgressReporter>()) { depService.downloadSourceSetSources(any(), any()) } } returns mockk(relaxed = true)
            sourcesService.resolveAndProcessSourceSetSources(projectRoot, ":app:main", fresh = true)
            coVerify(exactly = 1) { with(any<ProgressReporter>()) { depService.downloadSourceSetSources(projectRoot, ":app:main") } }
        }
    }

    @Test
    fun `resolveAndProcessAllSources performs fast re-merge when only provider changes`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("project-remerge")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        with(ProgressReporter.NONE) {
            val dir = storageService.getMergedSourcesDir(projectRoot, "", "root").storagePath
            dir.createDirectories()
            dir.resolve(".dependencies.hash").writeText("any-hash")
            dir.resolve(".dependencies.json").writeText("{}")
            dir.resolve("sources").createDirectories()

            // First call with one provider (already up to date)
            coEvery { indexService.isMergeUpToDate(any(), any(), any()) } returns true
            sourcesService.resolveAndProcessAllSources(projectRoot, fresh = false, providerToIndex = DeclarationSearch)
            coVerify(exactly = 0) { depService.downloadAllSources(any()) }

            // Second call with different provider (NOT up to date)
            coEvery { indexService.isMergeUpToDate(any(), any(), any()) } returns false
            sourcesService.resolveAndProcessAllSources(projectRoot, fresh = false, providerToIndex = FullTextSearch)

            // Should have triggered mergeIndices but NOT downloadAllSources
            coVerify(atLeast = 1) { with(any<ProgressReporter>()) { indexService.mergeIndices(any(), any(), any(), any()) } }
            coVerify(exactly = 0) { depService.downloadAllSources(any()) }
        }
    }

    @Test
    fun `resolveAndProcessAllSources fails if indexing fails`() = runTest(testDispatcher) {
        val projectRootPath = tempDir.resolve("project-index-fail")
        projectRootPath.createDirectories()
        val projectRoot = GradleProjectRoot(projectRootPath.absolutePathString())

        val sourcesFile = tempDir.resolve("lib-fail-sources.jar")
        java.util.zip.ZipOutputStream(sourcesFile.toFile().outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("com/example/Lib.kt"))
            it.write("class Lib".toByteArray())
            it.closeEntry()
        }

        val dep = GradleDependency(
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
                            name = "compileClasspath",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(dep)
                        )
                    )
                )
            )
        )

        coEvery { with(any<ProgressReporter>()) { indexService.indexFiles(any<GradleDependency>(), any(), any(), any()) } } throws RuntimeException("Index failed")

        org.junit.jupiter.api.assertThrows<RuntimeException> {
            with(ProgressReporter.NONE) {
                sourcesService.resolveAndProcessAllSources(projectRoot, index = true, providerToIndex = FullTextSearch)
            }
        }
    }
}

private fun assertTrue(condition: Boolean, message: String? = null) {
    kotlin.test.assertTrue(condition, message)
}
