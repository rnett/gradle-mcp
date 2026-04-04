package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.model.ProjectManifest
import dev.rnett.gradle.mcp.dependencies.model.SessionViewSourcesDir
import dev.rnett.gradle.mcp.dependencies.search.Index
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
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
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.assertSame

class SourcesServiceCachingTest {

    @TempDir
    lateinit var tempDir: Path

    private val depService = mockk<GradleDependencyService>()
    private val storageService = mockk<SourceStorageService>()
    private val indexService = mockk<IndexService>()
    private val progress = ProgressReporter.NONE

    private lateinit var sourcesService: DefaultSourcesService

    @BeforeEach
    fun setup() {
        sourcesService = DefaultSourcesService(depService, storageService, indexService)
    }

    @Test
    fun `test view reuse and cache hits`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val dep = GradleDependency(
            id = "org.test:test-lib:1.0.0",
            group = "org.test",
            name = "test-lib",
            version = "1.0.0",
            fromConfiguration = "compile",
            sourcesFile = tempDir.resolve("test-lib-1.0.0-sources.jar"),
            updatesChecked = true
        )

        val casBaseDir = tempDir.resolve("cas-entry").createDirectories()
        val casDir = CASDependencySourcesDir("hash", casBaseDir)
        casDir.sources.createDirectories()
        casDir.index.createDirectories()
        casDir.completionMarker.createFile()

        val viewDir = tempDir.resolve("view").createDirectories()
        val sourcesPath = viewDir.resolve("sources").createDirectories()

        val sessionView = SessionViewSourcesDir(
            sessionId = "test-session",
            baseDir = viewDir,
            sources = sourcesPath,
            manifest = ProjectManifest("test-session", "now", emptyList()),
            casBaseDir = tempDir.resolve("cas")
        )

        val report = GradleDependencyReport(
            projects = listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "compile",
                            description = null,
                            dependencies = listOf(dep),
                            isResolvable = true
                        )
                    )
                )
            )
        )

        // Using a custom matcher for the context receiver is complex in MockK.
        // Instead, we ensure the service is called with OUR progress reporter or ANY.
        // The simplest way is to just mock it without worrying about the context receiver if possible,
        // but Kotlin context receivers are part of the signature.

        coEvery {
            val p = any<ProgressReporter>()
            with(p) { depService.downloadProjectSources(any(), any(), any(), any(), any()) }
        } returns report.projects.first()
        coEvery {
            val p = any<ProgressReporter>()
            with(p) { indexService.indexFiles(any(), any(), any()) }
        } returns Index(casDir.index)

        coEvery { storageService.calculateHash(dep) } returns "hash"
        coEvery { storageService.getCASDependencySourcesDir("hash") } returns casDir
        coEvery { storageService.createSessionView(any(), any()) } returns sessionView
        coEvery { storageService.pruneSessionViews() } just Runs
        coEvery { storageService.getLockFile(any()) } returns tempDir.resolve("lock")

        val filter = null

        // 1. First call - should populate cache
        val result1 = with(progress) {
            sourcesService.resolveAndProcessProjectSources(root, ":", dependency = filter)
        }

        assertSame(sessionView, result1)
        coVerify(exactly = 1) {
            with(any<ProgressReporter>()) {
                depService.downloadProjectSources(any(), any(), any(), any(), any())
            }
        }
        coVerify(exactly = 1) { storageService.createSessionView(any(), any()) }

        // 2. Second call - should hit cache
        val result2 = with(progress) {
            sourcesService.resolveAndProcessProjectSources(root, ":", dependency = filter)
        }

        assertSame(result1, result2)
        // Verify no new resolution or view creation happened
        coVerify(exactly = 1) {
            with(any<ProgressReporter>()) {
                depService.downloadProjectSources(any(), any(), any(), any(), any())
            }
        }
        coVerify(exactly = 1) { storageService.createSessionView(any(), any()) }

        // 3. Call with different provider - should hit cache but run indexing
        val provider = mockk<SearchProvider>()
        every { provider.name } returns "test-provider"
        every { provider.indexVersion } returns 1
        every { provider.resolveIndexDir(any()) } returns tempDir.resolve("provider-idx")

        val result3 = with(progress) {
            sourcesService.resolveAndProcessProjectSources(root, ":", dependency = filter, providerToIndex = provider)
        }

        assertSame(result1, result3)
        // Gradle build still skipped
        coVerify(exactly = 1) {
            with(any<ProgressReporter>()) {
                depService.downloadProjectSources(any(), any(), any(), any(), any())
            }
        }
        // BUT indexService was used
        coVerify(exactly = 1) {
            with(any<ProgressReporter>()) {
                indexService.indexFiles(any(), any(), any())
            }
        }
    }

    @Test
    fun `test KMP platform artifact isolation`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())

        val commonSourcesJar = tempDir.resolve("common-sources.jar").createFile()
        val commonDep = GradleDependency(
            id = "org.test:test-lib-metadata:1.0.0",
            group = "org.test",
            name = "test-lib-metadata",
            version = "1.0.0",
            commonComponentId = null,
            sourcesFile = commonSourcesJar
        )

        val jvmSourcesJar = tempDir.resolve("jvm-sources.jar").createFile()
        val platformDep = GradleDependency(
            id = "org.test:test-lib-jvm:1.0.0",
            group = "org.test",
            name = "test-lib-jvm",
            version = "1.0.0",
            commonComponentId = "org.test:test-lib-metadata:1.0.0",
            sourcesFile = jvmSourcesJar
        )

        val commonCasBase = tempDir.resolve("common-cas").createDirectories()
        val commonCas = CASDependencySourcesDir("common-hash", commonCasBase)
        commonCas.baseCompletedMarker.createFile()
        commonCas.sourceSetsFile.writeText("commonMain")

        val platformCasBase = tempDir.resolve("platform-cas").createDirectories()
        val platformCas = CASDependencySourcesDir("platform-hash", platformCasBase)

        val report = GradleProjectDependencies(
            path = ":",
            sourceSets = emptyList(),
            repositories = emptyList(),
            configurations = listOf(
                GradleConfigurationDependencies(
                    name = "compile",
                    description = null,
                    dependencies = listOf(commonDep, platformDep),
                    isResolvable = true
                )
            )
        )

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any(), any(), any(), any()) }
        } returns report

        coEvery { storageService.calculateHash(commonDep) } returns "common-hash"
        coEvery { storageService.calculateHash(platformDep) } returns "platform-hash"
        coEvery { storageService.getCASDependencySourcesDir("common-hash") } returns commonCas
        coEvery { storageService.getCASDependencySourcesDir("platform-hash") } returns platformCas

        coEvery { storageService.waitForBase(commonCas, any()) } returns true

        coEvery {
            with(any<ProgressReporter>()) { storageService.extractSources(any(), any(), any()) }
        } answers {
            val dir = it.invocation.args[1] as Path
            dir.createDirectories()
        }

        coEvery { storageService.createSessionView(any(), any()) } returns mockk()
        coEvery { storageService.pruneSessionViews() } just Runs
        coEvery { storageService.getLockFile(any()) } returns tempDir.resolve("lock")

        with(progress) {
            sourcesService.resolveAndProcessProjectSources(root, ":")
        }

        coVerify { storageService.waitForBase(commonCas, any()) }
    }
}
