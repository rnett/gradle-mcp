package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencyReport
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
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class SourcesServiceCachingTest {

    @TempDir
    lateinit var tempDir: Path

    private val depService = mockk<GradleDependencyService>()
    private val storageService = mockk<SourceStorageService>()
    private val indexService = mockk<IndexService>()
    private val jdkSourceService = mockk<JdkSourceService>()
    private val progress = ProgressReporter.NONE

    private lateinit var sourcesService: DefaultSourcesService

    @BeforeEach
    fun setup() {
        io.mockk.coEvery { indexService.invalidateAllCaches(any()) } just io.mockk.Runs
        sourcesService = DefaultSourcesService(depService, storageService, indexService, jdkSourceService)
    }

    private fun createSessionView(name: String = "view"): SessionViewSourcesDir {
        val viewDir = tempDir.resolve(name).createDirectories()
        return SessionViewSourcesDir(
            sessionId = "test-session-$name",
            baseDir = viewDir,
            sources = viewDir.resolve("sources").createDirectories(),
            manifest = ProjectManifest("test-session-$name", "now", emptyList()),
            casBaseDir = tempDir.resolve("cas")
        )
    }

    private fun createJdkCas(): CASDependencySourcesDir {
        val jdkCasBase = tempDir.resolve("jdk-cas-${System.nanoTime()}").createDirectories()
        val jdkCas = CASDependencySourcesDir("jdk-hash", jdkCasBase)
        jdkCas.sources.createDirectories()
        jdkCas.baseCompletedMarker.createFile()
        return jdkCas
    }

    private fun projectWithSourceSet(
        sourceSet: GradleSourceSetDependencies,
        jdkHome: String? = tempDir.resolve("jdk").toString()
    ): GradleProjectDependencies = GradleProjectDependencies(
        path = ":",
        sourceSets = listOf(sourceSet),
        repositories = emptyList(),
        configurations = sourceSet.configurations.map {
            GradleConfigurationDependencies(
                name = it,
                description = null,
                isResolvable = true,
                dependencies = emptyList()
            )
        },
        jdkHome = jdkHome,
        jdkVersion = "21"
    )

    @Test
    fun `JDK sources are added to the session view manifest as a synthetic dependency`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val jdkCas = createJdkCas()

        val report = GradleProjectDependencies(
            path = ":",
            sourceSets = listOf(GradleSourceSetDependencies("main", emptyList(), isJvm = true)),
            repositories = emptyList(),
            configurations = emptyList(),
            jdkHome = tempDir.resolve("jdk").toString(),
            jdkVersion = "21"
        )
        coEvery {
            with(any<ProgressReporter>()) { depService.downloadProjectSources(any(), any(), any(), any(), any()) }
        } returns report
        coEvery {
            with(any<ProgressReporter>()) { jdkSourceService.resolveSources(any(), any(), any(), any()) }
        } returns jdkCas
        coEvery { storageService.pruneSessionViews() } just Runs

        val viewDir = tempDir.resolve("view").createDirectories()
        val sessionView = SessionViewSourcesDir(
            sessionId = "test-session",
            baseDir = viewDir,
            sources = viewDir.resolve("sources").createDirectories(),
            manifest = ProjectManifest("test-session", "now", emptyList()),
            casBaseDir = tempDir.resolve("cas")
        )
        val viewDeps = slot<Map<GradleDependency, CASDependencySourcesDir>>()
        coEvery { storageService.createSessionView(capture(viewDeps), any()) } returns sessionView

        val serviceWithJdk = DefaultSourcesService(depService, storageService, indexService, jdkSourceService)
        with(progress) {
            serviceWithJdk.resolveAndProcessProjectSources(root, ":")
        }

        assertTrue(viewDeps.captured.keys.any { it.relativePrefix == GradleDependency.JDK_SOURCES_PREFIX })
        assertTrue(viewDeps.captured.values.any { it == jdkCas })
    }

    @Test
    fun `Java source set includes JDK sources from report metadata`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val jdkCas = createJdkCas()
        val project = projectWithSourceSet(GradleSourceSetDependencies("main", listOf("implementation"), isJvm = true))
        val sourceSetReport = GradleSourceSetDependencyReport("main", emptyList(), emptyList(), isJvm = true)

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadSourceSetSources(any(), any(), any(), any(), any()) }
        } returns sourceSetReport
        coEvery {
            with(any<ProgressReporter>()) { depService.getDependencies(any(), any(), any()) }
        } returns GradleDependencyReport(listOf(project))
        coEvery {
            with(any<ProgressReporter>()) { jdkSourceService.resolveSources(any(), any(), any(), any()) }
        } returns jdkCas
        coEvery { storageService.pruneSessionViews() } just Runs
        val viewDeps = slot<Map<GradleDependency, CASDependencySourcesDir>>()
        coEvery { storageService.createSessionView(capture(viewDeps), any()) } returns createSessionView("jvm-source-set")

        val serviceWithJdk = DefaultSourcesService(depService, storageService, indexService, jdkSourceService)
        with(progress) {
            serviceWithJdk.resolveAndProcessSourceSetSources(root, ":main")
        }

        assertTrue(viewDeps.captured.keys.any { it.relativePrefix == GradleDependency.JDK_SOURCES_PREFIX })
        coVerify(exactly = 1) {
            with(any<ProgressReporter>()) { jdkSourceService.resolveSources(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `non-JVM source set skips JDK sources despite project JDK home`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val project = projectWithSourceSet(GradleSourceSetDependencies("commonMain", listOf("commonMainImplementation"), isJvm = false))
        val sourceSetReport = GradleSourceSetDependencyReport("commonMain", emptyList(), emptyList(), isJvm = false)

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadSourceSetSources(any(), any(), any(), any(), any()) }
        } returns sourceSetReport
        coEvery {
            with(any<ProgressReporter>()) { depService.getDependencies(any(), any(), any()) }
        } returns GradleDependencyReport(listOf(project))
        coEvery { storageService.pruneSessionViews() } just Runs
        coEvery { storageService.createSessionView(any(), any()) } returns createSessionView("non-jvm-source-set")

        val serviceWithJdk = DefaultSourcesService(depService, storageService, indexService, jdkSourceService)
        with(progress) {
            serviceWithJdk.resolveAndProcessSourceSetSources(root, ":commonMain")
        }

        coVerify(exactly = 0) {
            with(any<ProgressReporter>()) { jdkSourceService.resolveSources(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `JVM configuration includes JDK sources when referenced by JVM source set`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val jdkCas = createJdkCas()
        val config = GradleConfigurationDependencies("jvmMainImplementation", null, true, dependencies = emptyList())
        val project = projectWithSourceSet(GradleSourceSetDependencies("jvmMain", listOf(config.name), isJvm = true))

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadConfigurationSources(any(), any(), any(), any(), any()) }
        } returns config
        coEvery {
            with(any<ProgressReporter>()) { depService.getDependencies(any(), any(), any()) }
        } returns GradleDependencyReport(listOf(project))
        coEvery {
            with(any<ProgressReporter>()) { jdkSourceService.resolveSources(any(), any(), any(), any()) }
        } returns jdkCas
        coEvery { storageService.pruneSessionViews() } just Runs
        val viewDeps = slot<Map<GradleDependency, CASDependencySourcesDir>>()
        coEvery { storageService.createSessionView(capture(viewDeps), any()) } returns createSessionView("jvm-config")

        val serviceWithJdk = DefaultSourcesService(depService, storageService, indexService, jdkSourceService)
        with(progress) {
            serviceWithJdk.resolveAndProcessConfigurationSources(root, ":jvmMainImplementation")
        }

        assertTrue(viewDeps.captured.keys.any { it.relativePrefix == GradleDependency.JDK_SOURCES_PREFIX })
    }

    @Test
    fun `non-JVM configuration skips JDK sources`() = runTest {
        val root = GradleProjectRoot(tempDir.resolve("project").toString())
        val config = GradleConfigurationDependencies("commonMainImplementation", null, true, dependencies = emptyList())
        val project = projectWithSourceSet(GradleSourceSetDependencies("commonMain", listOf(config.name), isJvm = false))

        coEvery {
            with(any<ProgressReporter>()) { depService.downloadConfigurationSources(any(), any(), any(), any(), any()) }
        } returns config
        coEvery {
            with(any<ProgressReporter>()) { depService.getDependencies(any(), any(), any()) }
        } returns GradleDependencyReport(listOf(project))
        coEvery { storageService.pruneSessionViews() } just Runs
        coEvery { storageService.createSessionView(any(), any()) } returns createSessionView("non-jvm-config")

        val serviceWithJdk = DefaultSourcesService(depService, storageService, indexService, jdkSourceService)
        with(progress) {
            serviceWithJdk.resolveAndProcessConfigurationSources(root, ":commonMainImplementation")
        }

        coVerify(exactly = 0) {
            with(any<ProgressReporter>()) { jdkSourceService.resolveSources(any(), any(), any(), any()) }
        }
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

        coEvery {
            with(any<ProgressReporter>()) { storageService.extractSources(any(), any(), any()) }
        } answers {
            (it.invocation.args[1] as Path).createDirectories()
        }
        coEvery { storageService.calculateHash(dep) } returns "hash"
        coEvery { storageService.calculateHash(dep) } returns "hash"
        every { storageService.getCASDependencySourcesDir("hash") } returns casDir
        coEvery { storageService.createSessionView(any(), any()) } returns sessionView
        coEvery { storageService.pruneSessionViews() } just Runs
        every { storageService.getLockFile(any()) } returns tempDir.resolve("lock")

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
        // 3. Call with different provider - should hit cache but run indexing
        // 3. Call with different provider - should hit cache but run indexing
        val provider = mockk<SearchProvider>(relaxed = true)
        every { provider.name } returns "test-provider"
        every { provider.indexVersion } returns 1
        every { provider.resolveIndexDir(any()) } returns tempDir.resolve("provider-idx")
        every { provider.invalidateCache(any()) } just Runs

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
        // normalizedDir must exist so ensureBaseReady sees !currentlyBroken = true and enters the
        // lazy-generation branch instead of triggering a full repair.
        commonCas.normalizedDir.createDirectories()
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
        every { storageService.getCASDependencySourcesDir("common-hash") } returns commonCas
        every { storageService.getCASDependencySourcesDir("platform-hash") } returns platformCas

        coEvery { storageService.waitForBase(commonCas, any()) } returns true

        coEvery {
            with(any<ProgressReporter>()) { storageService.extractSources(any(), any(), any()) }
        } answers {
            val dir = it.invocation.args[1] as Path
            dir.createDirectories()
        }

        coEvery { storageService.createSessionView(any(), any()) } returns mockk()
        coEvery { storageService.pruneSessionViews() } just Runs
        every { storageService.getLockFile(any()) } returns tempDir.resolve("lock")

        with(progress) {
            sourcesService.resolveAndProcessProjectSources(root, ":")
        }

        coVerify { storageService.waitForBase(commonCas, any()) }
    }

    // ── Lazy-generation tests (M1) ─────────────────────────────────────────────────────────────
    // NOTE: The race window between lock block 1 and lock block 3 in ensureBaseReady is not tested
    // here. That window is the gap between releasing the first lock (where needsLazyGeneration is
    // set) and re-acquiring it in block 3 — during which another coroutine could write the
    // .processed-with-common marker. The re-check inside block 3 handles it correctly, but no
    // test exercises a concurrent write landing in that window.

    /** Shared helper: two fully-processed CAS dirs plus a report containing both deps. */
    private fun setupLazyScenario(
        platformSources: (sourcesRoot: Path) -> Unit = { root ->
            // Default: jvmMain-only sources — platform-specific relative to commonMain.
            val jvmSrc = root.resolve("jvmMain").resolve("kotlin")
            jvmSrc.createDirectories()
            jvmSrc.resolve("Jvm.kt").writeText("class Jvm")
        }
    ): Triple<GradleProjectRoot, CASDependencySourcesDir, CASDependencySourcesDir> {
        val root = GradleProjectRoot(tempDir.resolve("lazy-project").toString())

        val commonDep = GradleDependency(
            id = "org.test:test-lib-metadata:1.0.0",
            group = "org.test",
            name = "test-lib-metadata",
            version = "1.0.0",
            commonComponentId = null,
            sourcesFile = tempDir.resolve("lazy-common-sources.jar")
        )
        val platformDep = GradleDependency(
            id = "org.test:test-lib-jvm:1.0.0",
            group = "org.test",
            name = "test-lib-jvm",
            version = "1.0.0",
            commonComponentId = "org.test:test-lib-metadata:1.0.0",
            sourcesFile = tempDir.resolve("lazy-jvm-sources.jar")
        )

        val commonCasBase = tempDir.resolve("lazy-common-cas").createDirectories()
        val commonCas = CASDependencySourcesDir("lazy-common-hash", commonCasBase)
        commonCas.baseCompletedMarker.createFile()
        // normalizedDir must exist so ensureBaseReady sees !currentlyBroken and enters the lazy branch
        // instead of triggering a full repair for the common dep.
        commonCas.normalizedDir.createDirectories()
        commonCas.sourceSetsFile.writeText("commonMain")

        val platformCasBase = tempDir.resolve("lazy-platform-cas").createDirectories()
        val platformCas = CASDependencySourcesDir("lazy-platform-hash", platformCasBase)
        platformCas.baseCompletedMarker.createFile()
        // normalizedDir must exist so ensureBaseReady sees !currentlyBroken for the platform dep.
        platformCas.normalizedDir.createDirectories()
        platformSources(platformCas.sources)

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

        coEvery { storageService.calculateHash(commonDep) } returns "lazy-common-hash"
        coEvery { storageService.calculateHash(platformDep) } returns "lazy-platform-hash"
        every { storageService.getCASDependencySourcesDir("lazy-common-hash") } returns commonCas
        every { storageService.getCASDependencySourcesDir("lazy-platform-hash") } returns platformCas
        coEvery { storageService.waitForBase(commonCas, any()) } returns true
        coEvery { storageService.createSessionView(any(), any()) } returns mockk()
        coEvery { storageService.pruneSessionViews() } just Runs

        return Triple(root, commonCas, platformCas)
    }

    @Test
    fun `lazy generation happy path - base complete, processedWithCommon absent, normalizedTargetDir is created`() = runTest(timeout = 10.minutes) {
        val (root, commonCas, platformCas) = setupLazyScenario()

        // processedWithCommon absent — lazy generation must run.
        val processedWithCommon = platformCas.baseDir.resolve(".processed-with-common")
        assertFalse(processedWithCommon.exists())

        with(progress) { sourcesService.resolveAndProcessProjectSources(root, ":") }

        // waitForBase was called to wait for common sibling.
        coVerify { storageService.waitForBase(commonCas, any()) }
        // normalizedTargetDir was created with the platform-specific file.
        assertTrue(platformCas.normalizedTargetDir.exists())
        val targetFiles = platformCas.normalizedTargetDir.walk().filter { it.isRegularFile() }.toList()
        assertTrue(targetFiles.any { it.extension == "kt" }, "normalizedTargetDir should contain at least one .kt file")
        // processedWithCommon marker was written.
        assertTrue(processedWithCommon.exists())
        // Lazy generation must NOT write any files into normalizedDir — outputDir=null in detectAndNormalize.
        val normalizedFiles = platformCas.normalizedDir.walk().filter { it.isRegularFile() }.toList()
        assertTrue(normalizedFiles.isEmpty(), "normalizedDir should remain empty after lazy generation")
    }

    @Test
    fun `lazy generation empty sets - platform and common sets identical, normalizedTargetDir NOT created but marker IS written`() = runTest(timeout = 10.minutes) {
        // Platform sources contain only commonMain — same set as the common dep.
        val (root, _, platformCas) = setupLazyScenario { sourcesRoot ->
            val commonMainSrc = sourcesRoot.resolve("commonMain").resolve("kotlin")
            commonMainSrc.createDirectories()
            commonMainSrc.resolve("Common.kt").writeText("class Common")
        }

        with(progress) { sourcesService.resolveAndProcessProjectSources(root, ":") }

        // normalizedTargetDir must NOT be created — platform sets are identical to common sets.
        assertFalse(platformCas.normalizedTargetDir.exists())
        // But processedWithCommon marker MUST be written so we don't repeat the check.
        assertTrue(platformCas.baseDir.resolve(".processed-with-common").exists())
    }

    @Test
    fun `lazy generation skipped - processedWithCommon marker already present`() = runTest(timeout = 10.minutes) {
        val (root, commonCas, platformCas) = setupLazyScenario()

        // Pre-write the marker — generation must be skipped entirely.
        platformCas.baseDir.resolve(".processed-with-common").writeText("already done")

        with(progress) { sourcesService.resolveAndProcessProjectSources(root, ":") }

        // waitForBase must NOT have been called — marker was already present.
        coVerify(exactly = 0) { storageService.waitForBase(commonCas, any()) }
        // No generation ran, so normalizedTargetDir must not have been created.
        assertFalse(platformCas.normalizedTargetDir.exists())
    }

    // ── M2: missing sourceSetsFile does not crash the batch (H4) ──────────────────────────────────

    @Test
    fun `calculatePlatformSpecificSets missing sourceSetsFile - H4 catches and logs warning, no exception propagates`() = runTest(timeout = 10.minutes) {
        val root = GradleProjectRoot(tempDir.resolve("m2-project").toString())

        val commonDep = GradleDependency(
            id = "org.test:test-lib-metadata:1.0.0",
            group = "org.test",
            name = "test-lib-metadata",
            version = "1.0.0",
            commonComponentId = null,
            sourcesFile = tempDir.resolve("m2-common-sources.jar")
        )
        val platformDep = GradleDependency(
            id = "org.test:test-lib-jvm:1.0.0",
            group = "org.test",
            name = "test-lib-jvm",
            version = "1.0.0",
            commonComponentId = "org.test:test-lib-metadata:1.0.0",
            sourcesFile = tempDir.resolve("m2-jvm-sources.jar")
        )

        val commonCasBase = tempDir.resolve("m2-common-cas").createDirectories()
        val commonCas = CASDependencySourcesDir("m2-common-hash", commonCasBase)
        commonCas.baseCompletedMarker.createFile()
        commonCas.normalizedDir.createDirectories()
        // sourceSetsFile intentionally NOT written — this is the M2 scenario.

        val platformCasBase = tempDir.resolve("m2-platform-cas").createDirectories()
        val platformCas = CASDependencySourcesDir("m2-platform-hash", platformCasBase)
        platformCas.baseCompletedMarker.createFile()
        platformCas.normalizedDir.createDirectories()
        platformCas.sources.resolve("jvmMain").resolve("kotlin").createDirectories()
            .resolve("Jvm.kt").writeText("class Jvm")

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
        coEvery { storageService.calculateHash(commonDep) } returns "m2-common-hash"
        coEvery { storageService.calculateHash(platformDep) } returns "m2-platform-hash"
        every { storageService.getCASDependencySourcesDir("m2-common-hash") } returns commonCas
        every { storageService.getCASDependencySourcesDir("m2-platform-hash") } returns platformCas
        coEvery { storageService.waitForBase(commonCas, any()) } returns true
        coEvery { storageService.createSessionView(any(), any()) } returns mockk()
        coEvery { storageService.pruneSessionViews() } just Runs

        // H4: IllegalStateException from calculatePlatformSpecificSets is caught and logged as a
        // warning — it must NOT propagate and crash the whole batch.
        with(progress) { sourcesService.resolveAndProcessProjectSources(root, ":") }
        // Reaching here confirms H4's catch worked.
        // normalizedTargetDir must stay absent — generation was skipped due to missing sourceSetsFile.
        assertFalse(platformCas.normalizedTargetDir.exists())
    }

    // ── M6: detectAndNormalize(outputDir=null) — only targetOutputDir receives files ──────────────

    @Test
    fun `detectAndNormalize with outputDir null - only platform-specific files appear in normalizedTargetDir`() = runTest(timeout = 10.minutes) {
        val (root, _, platformCas) = setupLazyScenario()

        with(progress) { sourcesService.resolveAndProcessProjectSources(root, ":") }

        // The lazy path calls detectAndNormalize(outputDir=null, targetOutputDir=...) so the
        // pre-existing (empty) normalizedDir must NOT have received any files from the lazy step.
        val normalizedFiles = platformCas.normalizedDir.walk().filter { it.isRegularFile() }.toList()
        assertTrue(normalizedFiles.isEmpty(), "normalizedDir should not be written by lazy generation")

        // Only platform-specific files appear in normalizedTargetDir.
        assertTrue(platformCas.normalizedTargetDir.exists())
        val targetFiles = platformCas.normalizedTargetDir.walk().filter { it.isRegularFile() }.toList()
        assertTrue(targetFiles.isNotEmpty(), "normalizedTargetDir should contain the platform-specific file")
    }

    // ── M1: DefaultSourceStorageService.createSessionView — both isDiffOnly branches ──────────────

    @Test
    fun `createSessionView - normalizedTargetDir populated and baseCompletedMarker exists - isDiffOnly=true and junction points to normalizedTargetDir`() = runTest(timeout = 10.minutes) {
        val env = GradleMcpEnvironment(tempDir.resolve("m1-env-a"))
        val realStorageService = DefaultSourceStorageService(env)

        val commonSourcesJar = tempDir.resolve("m1a-common-sources.jar").createFile()
        val commonDep = GradleDependency(
            id = "org.test:test-lib-metadata:1.0.0",
            group = "org.test",
            name = "test-lib-metadata",
            version = "1.0.0",
            sourcesFile = commonSourcesJar
        )

        val platformSourcesJar = tempDir.resolve("m1a-platform-sources.jar").createFile()
        val platformDep = GradleDependency(
            id = "org.test:test-lib-jvm:1.0.0",
            group = "org.test",
            name = "test-lib-jvm",
            version = "1.0.0",
            commonComponentId = "org.test:test-lib-metadata:1.0.0",
            sourcesFile = platformSourcesJar
        )

        val commonHash = "m1acommonhash00000000000000000000"
        val platformHash = "m1aplatformhash0000000000000000000"
        val commonCas = realStorageService.getCASDependencySourcesDir(commonHash)
        val platformCas = realStorageService.getCASDependencySourcesDir(platformHash)

        // Set up common CAS: baseCompletedMarker + normalizedDir (no normalizedTargetDir needed)
        commonCas.baseDir.createDirectories()
        commonCas.baseCompletedMarker.createFile()
        commonCas.normalizedDir.createDirectories()
        commonCas.normalizedDir.resolve("Common.kt").writeText("class Common")

        // Set up platform CAS: baseCompletedMarker + normalizedTargetDir populated (platform-specific files)
        platformCas.baseDir.createDirectories()
        platformCas.baseCompletedMarker.createFile()
        platformCas.normalizedDir.createDirectories()
        platformCas.normalizedTargetDir.createDirectories()
        platformCas.normalizedTargetDir.resolve("Jvm.kt").writeText("class Jvm")

        val deps = mapOf(commonDep to commonCas, platformDep to platformCas)
        val view = realStorageService.createSessionView(deps)

        val manifestDep = view.manifest.dependencies.find { it.id == platformDep.id }
        assertNotNull(manifestDep, "Platform dep must appear in manifest")
        assertTrue(manifestDep.isDiffOnly, "Platform dep with populated normalizedTargetDir must have isDiffOnly=true")

        // Junction for platform dep must point to normalizedTargetDir
        val junctionPath = view.sources.resolve(requireNotNull(platformDep.relativePrefix))
        assertTrue(junctionPath.exists(), "Junction for platform dep must exist")
        // The junction resolves files from normalizedTargetDir — Jvm.kt should be reachable
        assertTrue(junctionPath.resolve("Jvm.kt").exists(), "Platform-specific file must be reachable through junction")
    }

    @Test
    fun `createSessionView - normalizedTargetDir absent - platform dep is excluded from manifest and no junction is created`() = runTest(timeout = 10.minutes) {
        val env = GradleMcpEnvironment(tempDir.resolve("m1-env-b"))
        val realStorageService = DefaultSourceStorageService(env)

        val commonSourcesJar = tempDir.resolve("m1b-common-sources.jar").createFile()
        val commonDep = GradleDependency(
            id = "org.test:test-lib-metadata:2.0.0",
            group = "org.test",
            name = "test-lib-metadata",
            version = "2.0.0",
            sourcesFile = commonSourcesJar
        )

        val platformSourcesJar = tempDir.resolve("m1b-platform-sources.jar").createFile()
        val platformDep = GradleDependency(
            id = "org.test:test-lib-jvm:2.0.0",
            group = "org.test",
            name = "test-lib-jvm",
            version = "2.0.0",
            commonComponentId = "org.test:test-lib-metadata:2.0.0",
            sourcesFile = platformSourcesJar
        )

        val commonHash = "m1bcommonhash00000000000000000000"
        val platformHash = "m1bplatformhash0000000000000000000"
        val commonCas = realStorageService.getCASDependencySourcesDir(commonHash)
        val platformCas = realStorageService.getCASDependencySourcesDir(platformHash)

        // Set up common CAS: baseCompletedMarker + normalizedDir
        commonCas.baseDir.createDirectories()
        commonCas.baseCompletedMarker.createFile()
        commonCas.normalizedDir.createDirectories()
        commonCas.normalizedDir.resolve("Common.kt").writeText("class Common")

        // Set up platform CAS: baseCompletedMarker + normalizedDir, but NO normalizedTargetDir
        platformCas.baseDir.createDirectories()
        platformCas.baseCompletedMarker.createFile()
        platformCas.normalizedDir.createDirectories()
        platformCas.normalizedDir.resolve("Common.kt").writeText("class Common")
        // normalizedTargetDir intentionally absent

        val deps = mapOf(commonDep to commonCas, platformDep to platformCas)
        val view = realStorageService.createSessionView(deps)

        // Platform dep with empty normalizedTargetDir is skipped entirely (hasCommonSibling && !targetExistsAndNotEmpty)
        // so it must NOT appear in the manifest.
        val manifestDep = view.manifest.dependencies.find { it.id == platformDep.id }
        assertNull(manifestDep, "Platform dep without normalizedTargetDir must be excluded from manifest")

        // Junction for platform dep must NOT have been created
        val junctionPath = view.sources.resolve(requireNotNull(platformDep.relativePrefix))
        assertFalse(junctionPath.exists(), "Junction for platform dep without normalizedTargetDir must not be created")
    }

    // ── M2: baseReady=false dep must still reach createSessionView ────────────────────────────────

    @Test
    fun `calculatePlatformSpecificSets missing sourceSetsFile - platform dep is still passed to createSessionView`() = runTest(timeout = 10.minutes) {
        val root = GradleProjectRoot(tempDir.resolve("m2-verify-project").toString())

        val commonDep = GradleDependency(
            id = "org.test:test-lib-metadata:3.0.0",
            group = "org.test",
            name = "test-lib-metadata",
            version = "3.0.0",
            commonComponentId = null,
            sourcesFile = tempDir.resolve("m2v-common-sources.jar")
        )
        val platformDep = GradleDependency(
            id = "org.test:test-lib-jvm:3.0.0",
            group = "org.test",
            name = "test-lib-jvm",
            version = "3.0.0",
            commonComponentId = "org.test:test-lib-metadata:3.0.0",
            sourcesFile = tempDir.resolve("m2v-jvm-sources.jar")
        )

        val commonCasBase = tempDir.resolve("m2v-common-cas").createDirectories()
        val commonCas = CASDependencySourcesDir("m2v-common-hash", commonCasBase)
        commonCas.baseCompletedMarker.createFile()
        commonCas.normalizedDir.createDirectories()
        // sourceSetsFile intentionally absent — triggers MissingSourceSetsFileException

        val platformCasBase = tempDir.resolve("m2v-platform-cas").createDirectories()
        val platformCas = CASDependencySourcesDir("m2v-platform-hash", platformCasBase)
        platformCas.baseCompletedMarker.createFile()
        platformCas.normalizedDir.createDirectories()
        platformCas.sources.resolve("jvmMain").resolve("kotlin").createDirectories()
            .resolve("Jvm.kt").writeText("class Jvm")

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
        coEvery { storageService.calculateHash(commonDep) } returns "m2v-common-hash"
        coEvery { storageService.calculateHash(platformDep) } returns "m2v-platform-hash"
        every { storageService.getCASDependencySourcesDir("m2v-common-hash") } returns commonCas
        every { storageService.getCASDependencySourcesDir("m2v-platform-hash") } returns platformCas
        coEvery { storageService.waitForBase(commonCas, any()) } returns true
        coEvery { storageService.createSessionView(any(), any()) } returns mockk()
        coEvery { storageService.pruneSessionViews() } just Runs

        with(progress) { sourcesService.resolveAndProcessProjectSources(root, ":") }

        // The platform dep must still appear in the map passed to createSessionView even when
        // MissingSourceSetsFileException was caught — the dep is not pruned from the dep list.
        coVerify {
            storageService.createSessionView(
                match { map -> platformDep in map },
                any()
            )
        }
    }

    // ── M4: waitForBase returning false propagates as ISE ────────────────────────────────────────

    @Test
    fun `waitForBase returning false propagates as IllegalStateException out of resolveAndProcessProjectSources`() = runTest(timeout = 10.minutes) {
        val root = GradleProjectRoot(tempDir.resolve("m4-project").toString())

        val commonDep = GradleDependency(
            id = "org.test:test-lib-metadata:4.0.0",
            group = "org.test",
            name = "test-lib-metadata",
            version = "4.0.0",
            commonComponentId = null,
            sourcesFile = tempDir.resolve("m4-common-sources.jar")
        )
        val platformDep = GradleDependency(
            id = "org.test:test-lib-jvm:4.0.0",
            group = "org.test",
            name = "test-lib-jvm",
            version = "4.0.0",
            commonComponentId = "org.test:test-lib-metadata:4.0.0",
            sourcesFile = tempDir.resolve("m4-jvm-sources.jar")
        )

        val commonCasBase = tempDir.resolve("m4-common-cas").createDirectories()
        val commonCas = CASDependencySourcesDir("m4-common-hash", commonCasBase)
        // baseCompletedMarker absent — triggers the "wait for common sibling" path
        commonCas.normalizedDir.createDirectories()

        val platformCasBase = tempDir.resolve("m4-platform-cas").createDirectories()
        val platformCas = CASDependencySourcesDir("m4-platform-hash", platformCasBase)
        // also incomplete so full-processing triggers on platform dep as well
        platformCas.normalizedDir.createDirectories()

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
        coEvery { storageService.calculateHash(commonDep) } returns "m4-common-hash"
        coEvery { storageService.calculateHash(platformDep) } returns "m4-platform-hash"
        every { storageService.getCASDependencySourcesDir("m4-common-hash") } returns commonCas
        every { storageService.getCASDependencySourcesDir("m4-platform-hash") } returns platformCas
        // waitForBase returns false — common sibling failed to process
        coEvery { storageService.waitForBase(commonCas, any()) } returns false
        coEvery {
            with(any<ProgressReporter>()) { storageService.extractSources(any(), any(), any()) }
        } answers {
            val dir = it.invocation.args[1] as Path
            dir.createDirectories()
        }
        coEvery { storageService.pruneSessionViews() } just Runs

        assertFailsWith<IllegalStateException> {
            with(progress) { sourcesService.resolveAndProcessProjectSources(root, ":") }
        }
    }

    // ── m3: clearCasDir deletes .processed-with-common marker ────────────────────────────────────

    @Test
    fun `clearCasDir deletes and re-writes processedWithCommon marker when repair path runs`() = runTest(timeout = 10.minutes) {
        val root = GradleProjectRoot(tempDir.resolve("m3-project").toString())

        val commonDep = GradleDependency(
            id = "org.test:test-lib-metadata:5.0.0",
            group = "org.test",
            name = "test-lib-metadata",
            version = "5.0.0",
            commonComponentId = null,
            sourcesFile = tempDir.resolve("m3-common-sources.jar")
        )
        val platformDep = GradleDependency(
            id = "org.test:test-lib-jvm:5.0.0",
            group = "org.test",
            name = "test-lib-jvm",
            version = "5.0.0",
            commonComponentId = "org.test:test-lib-metadata:5.0.0",
            sourcesFile = tempDir.resolve("m3-jvm-sources.jar")
        )

        val commonCasBase = tempDir.resolve("m3-common-cas").createDirectories()
        val commonCas = CASDependencySourcesDir("m3-common-hash", commonCasBase)
        commonCas.baseCompletedMarker.createFile()
        commonCas.normalizedDir.createDirectories()
        commonCas.sourceSetsFile.writeText("commonMain")

        val platformCasBase = tempDir.resolve("m3-platform-cas").createDirectories()
        val platformCas = CASDependencySourcesDir("m3-platform-hash", platformCasBase)
        // Broken state: baseCompletedMarker absent, but .processed-with-common present
        val processedMarker = platformCas.baseDir.resolve(".processed-with-common")
        processedMarker.writeText("stale")
        // normalizedDir absent so currentlyBroken=true triggers clearCasDir in block-1
        assertTrue(processedMarker.exists(), "Precondition: .processed-with-common must exist before repair")
        assertFalse(platformCas.baseCompletedMarker.exists(), "Precondition: baseCompletedMarker must be absent (broken state)")

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
        coEvery { storageService.calculateHash(commonDep) } returns "m3-common-hash"
        coEvery { storageService.calculateHash(platformDep) } returns "m3-platform-hash"
        every { storageService.getCASDependencySourcesDir("m3-common-hash") } returns commonCas
        every { storageService.getCASDependencySourcesDir("m3-platform-hash") } returns platformCas
        coEvery { storageService.waitForBase(commonCas, any()) } returns true
        coEvery {
            with(any<ProgressReporter>()) { storageService.extractSources(any(), any(), any()) }
        } answers {
            val dir = it.invocation.args[1] as Path
            dir.createDirectories()
        }
        coEvery { storageService.createSessionView(any(), any()) } returns mockk()
        coEvery { storageService.pruneSessionViews() } just Runs

        with(progress) { sourcesService.resolveAndProcessProjectSources(root, ":") }

        // clearCasDir must have deleted .processed-with-common, but it is re-written because repair re-runs processing.
        assertTrue(processedMarker.exists(), ".processed-with-common must exist (re-written) after repair re-runs processing")
    }
}
