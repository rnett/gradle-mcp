package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.model.SessionViewSourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultSourcesServiceDependencyFilterTest {

    @TempDir
    lateinit var tempDir: Path

    private val depService = mockk<GradleDependencyService>()
    private val storageService = mockk<SourceStorageService>()
    private val indexService = mockk<IndexService>()
    private val jdkSourceService = mockk<JdkSourceService>(relaxed = true)
    private val progress = ProgressReporter.NONE

    private lateinit var sourcesService: DefaultSourcesService

    @BeforeEach
    fun setup() {
        coEvery { indexService.invalidateAllCaches(any()) } just Runs
        sourcesService = DefaultSourcesService(depService, storageService, indexService, jdkSourceService)
    }

    @Test
    fun `empty scope with dependency filter returns empty session view`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val emptyView = mockk<SessionViewSourcesDir>(relaxed = true)
        val report = GradleProjectDependencies(
            path = ":",
            sourceSets = emptyList(),
            repositories = emptyList(),
            configurations = emptyList()
        )

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any(), any(), any(), any()) }
        } returns report
        coEvery { storageService.pruneSessionViews() } just Runs
        coEvery { storageService.createSessionView(emptyMap(), force = false) } returns emptyView

        val result = with(progress) {
            sourcesService.resolveAndProcessProjectSources(root, ":", dependency = "^org\\.example:artifact(:.*)?$")
        }

        assertSame(emptyView, result)
        coVerify(exactly = 1) {
            storageService.createSessionView(emptyMap(), force = false)
        }
        coVerify(exactly = 0) {
            with(any<ProgressReporter>()) {
                storageService.extractSources(any(), any(), any())
            }
        }
    }

    @Test
    fun `dependency filter rejects populated scope with no matches`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val dep = GradleDependency(
            id = "org.example:artifact:1.0.0",
            group = "org.example",
            name = "artifact",
            version = "1.0.0",
            sourcesFile = tempDir.resolve("artifact-sources.jar").createFile()
        )
        val report = GradleProjectDependencies(
            path = ":",
            sourceSets = emptyList(),
            repositories = emptyList(),
            configurations = listOf(
                GradleConfigurationDependencies(
                    name = "compileClasspath",
                    description = null,
                    dependencies = listOf(dep),
                    isResolvable = true
                )
            )
        )

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any(), any(), any(), any()) }
        } returns report
        coEvery { storageService.pruneSessionViews() } just Runs

        val error = assertFailsWith<IllegalArgumentException> {
            with(progress) {
                sourcesService.resolveAndProcessProjectSources(root, ":", dependency = "^com\\.other:missing(:.*)?$")
            }
        }

        val message = error.message.orEmpty()
        assertContains(message, "matched zero dependencies")
        assertContains(message, "project ':'")
    }

    @Test
    fun `dependency filter reports distinct error when matches have no sources`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val dep = GradleDependency(
            id = "org.example:artifact:1.0.0",
            group = "org.example",
            name = "artifact",
            version = "1.0.0"
        )
        val report = GradleProjectDependencies(
            path = ":",
            sourceSets = emptyList(),
            repositories = emptyList(),
            configurations = listOf(
                GradleConfigurationDependencies(
                    name = "compileClasspath",
                    description = null,
                    dependencies = listOf(dep),
                    isResolvable = true
                )
            )
        )

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any(), any(), any(), any()) }
        } returns report
        coEvery { storageService.pruneSessionViews() } just Runs

        val error = assertFailsWith<IllegalArgumentException> {
            with(progress) {
                sourcesService.resolveAndProcessProjectSources(root, ":", dependency = "^org\\.example:artifact(:.*)?$")
            }
        }

        val message = error.message.orEmpty()
        assertContains(message, "matched dependencies")
        assertContains(message, "none have sources")
        coVerify(exactly = 0) {
            with(any<ProgressReporter>()) {
                storageService.extractSources(any(), any(), any())
            }
        }
    }

    @Test
    fun `invalid dependency filter fails before resolving dependencies`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())

        val error = assertFailsWith<IllegalArgumentException> {
            with(progress) {
                sourcesService.resolveAndProcessProjectSources(root, ":", dependency = "*[")
            }
        }

        assertContains(error.message.orEmpty(), "Dangling meta character")
        coVerify(exactly = 0) {
            with(any<ProgressReporter>()) {
                depService.downloadProjectSources(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `blank dependency filter is treated as no filter`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val dep = GradleDependency(
            id = "org.example:artifact:1.0.0",
            group = "org.example",
            name = "artifact",
            version = "1.0.0",
            sourcesFile = tempDir.resolve("artifact-sources.jar").createFile()
        )
        val report = GradleProjectDependencies(
            path = ":",
            sourceSets = emptyList(),
            repositories = emptyList(),
            configurations = listOf(
                GradleConfigurationDependencies(
                    name = "compileClasspath",
                    description = null,
                    dependencies = listOf(dep),
                    isResolvable = true
                )
            )
        )
        val casDir = dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir("artifact-hash", tempDir.resolve("artifact-cas"))
        val view = mockk<SessionViewSourcesDir>(relaxed = true)

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any(), null, any(), any()) }
        } returns report
        coEvery { storageService.pruneSessionViews() } just Runs
        coEvery { storageService.calculateHash(dep) } returns "artifact-hash"
        coEvery {
            with(any<ProgressReporter>()) { storageService.extractSources(any(), any(), any()) }
        } just Runs
        coEvery { storageService.createSessionView(any(), force = false) } returns view
        every { storageService.getCASDependencySourcesDir("artifact-hash") } returns casDir

        val result = with(progress) {
            sourcesService.resolveAndProcessProjectSources(root, ":", dependency = " ")
        }

        assertSame(view, result)
        coVerify {
            with(any<ProgressReporter>()) {
                depService.downloadProjectSources(any(), any(), null, any(), any())
            }
        }
    }
}
