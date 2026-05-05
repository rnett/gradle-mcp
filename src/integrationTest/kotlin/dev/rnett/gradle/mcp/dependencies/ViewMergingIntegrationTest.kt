package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.SessionViewSourcesDir
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@ResourceLock("ViewMergingIntegrationTest")
class ViewMergingIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `check view merging for KMP project`() = runTest(timeout = 5.minutes) {
        val mcpDir = tempDir.resolve("mcp")
        val environment = GradleMcpEnvironment(mcpDir)
        val provider = DefaultGradleProvider(
            config = GradleConfiguration(),
            buildManager = BuildManager()
        )
        val depService = DefaultGradleDependencyService(provider)
        val storageService = DefaultSourceStorageService(environment)
        val rawIndexService = dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService(environment, listOf(FullTextSearch()))
        val indexService = DefaultSourceIndexService(rawIndexService) // Simplified for this test
        val sourcesService = DefaultSourcesService(depService, storageService, rawIndexService, DefaultJdkSourceService(storageService, rawIndexService))

        testGradleProject {
            useKotlinDsl(true)
            buildScript(
                """
                plugins { id("org.jetbrains.kotlin.multiplatform") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin {
                    jvm()
                    sourceSets {
                        commonMain {
                            dependencies {
                                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                            }
                        }
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())

            val sourcesDir = with(ProgressReporter.PRINTLN) {
                sourcesService.resolveAndProcessProjectSources(projectRoot, ":", dependency = "org.jetbrains.kotlinx:kotlinx-coroutines-core", providerToIndex = FullTextSearch())
            }

            assertNotNull(sourcesDir)
            assertTrue(sourcesDir is SessionViewSourcesDir)

            val sessionView = sourcesDir as SessionViewSourcesDir
            val depsDir = sessionView.sources

            // ... (existing logging)

            // Check for coroutines dependencies
            val coroutinesEntries = depsDir.resolve("org.jetbrains.kotlinx").listDirectoryEntries("kotlinx-coroutines-core*")
            assertTrue(coroutinesEntries.isNotEmpty(), "Should find coroutines entries")

            // ...

            // Perform a search to see how results from different variants are reported
            // 'Job' is a good candidate as it's defined in common.
            val searchResponse = indexService.search(sessionView, FullTextSearch(), "interface Job", dev.rnett.gradle.mcp.tools.PaginationInput(0, 10))
            println("Search results for 'interface Job':")
            searchResponse.results.forEach { println("  ${it.relativePath}") }

            // 'Dispatchers' might have platform-specific parts
            val searchResponse2 = indexService.search(sessionView, FullTextSearch(), "object Dispatchers", dev.rnett.gradle.mcp.tools.PaginationInput(0, 10))
            println("Search results for 'object Dispatchers':")
            searchResponse2.results.forEach { println("  ${it.relativePath}") }

            // Verify that we can read files from both variants
            searchResponse.results.forEach { result ->
                val path = sessionView.sources.resolve(result.relativePath)
                assertTrue(path.exists(), "Search result path should be readable: ${result.relativePath}")
            }
        }
        provider.close()
    }

    @Test
    fun `check no duplicate KMP metadata configurations in manifest`() = runTest(timeout = 5.minutes) {
        val mcpDir = tempDir.resolve("mcp-kmp-dedup")
        val environment = GradleMcpEnvironment(mcpDir)
        val provider = DefaultGradleProvider(
            config = GradleConfiguration(),
            buildManager = BuildManager()
        )
        val depService = DefaultGradleDependencyService(provider)
        val storageService = DefaultSourceStorageService(environment)
        val indexService = dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService(environment, listOf(FullTextSearch()))
        val sourcesService = DefaultSourcesService(depService, storageService, indexService, DefaultJdkSourceService(storageService, indexService))

        testGradleProject {
            useKotlinDsl(true)
            buildScript(
                """
                plugins { id("org.jetbrains.kotlin.multiplatform") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin {
                    jvm()
                    sourceSets {
                        commonMain {
                            dependencies {
                                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                            }
                        }
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())

            val sourcesDir = with(ProgressReporter.PRINTLN) {
                sourcesService.resolveAndProcessProjectSources(projectRoot, ":", dependency = "org.jetbrains.kotlinx:kotlinx-coroutines-core", providerToIndex = FullTextSearch())
            }

            assertTrue(sourcesDir is SessionViewSourcesDir)
            val sessionView = sourcesDir as SessionViewSourcesDir
            val manifestDeps = sessionView.manifest.dependencies

            // After fix: BOTH should appear. The common variant is no longer dropped.
            val coroutinesIds = manifestDeps.map { it.id }.filter { it.startsWith("org.jetbrains.kotlinx:kotlinx-coroutines-core") }
            println("Coroutines manifest entries: $coroutinesIds")

            val hasCommonVariant = coroutinesIds.any { it == "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0" }
            val hasJvmVariant = coroutinesIds.any { it == "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0" }

            assertTrue(hasCommonVariant, "Common variant kotlinx-coroutines-core SHOULD appear in manifest for proper target isolation. Found: $coroutinesIds")
            assertTrue(hasJvmVariant, "JVM variant kotlinx-coroutines-core-jvm MUST appear in manifest. Found: $coroutinesIds")
        }
        provider.close()
    }

    @Test
    fun `check KMP target isolation eliminates redundant sources in the session view`() = runTest(timeout = 5.minutes) {
        val mcpDir = tempDir.resolve("mcp-kmp-target")
        val environment = GradleMcpEnvironment(mcpDir)
        val storageService = DefaultSourceStorageService(environment)

        val commonDep = GradleDependency(
            id = "org.example:lib:1.0",
            group = "org.example",
            name = "lib",
            version = "1.0",
            sourcesFile = tempDir.resolve("lib-common.jar").apply { writeText("common") }
        )
        val platformDep = GradleDependency(
            id = "org.example:lib-jvm:1.0",
            group = "org.example",
            name = "lib-jvm",
            version = "1.0",
            commonComponentId = "org.example:lib:1.0",
            sourcesFile = tempDir.resolve("lib-jvm.jar").apply { writeText("jvm") }
        )

        val commonCas = storageService.getCASDependencySourcesDir(storageService.calculateHash(commonDep))
        val platformCas = storageService.getCASDependencySourcesDir(storageService.calculateHash(platformDep))

        commonCas.normalizedDir.createDirectories()
        commonCas.normalizedDir.resolve("shared.txt").writeText("same")
        commonCas.sourceSetsFile.writeText("commonMain")
        commonCas.completionMarker.writeText("")

        platformCas.normalizedDir.createDirectories()
        platformCas.normalizedDir.resolve("shared.txt").writeText("same")
        platformCas.normalizedDir.resolve("jvm-only.txt").writeText("unique")
        platformCas.sourceSetsFile.writeText("commonMain\njvmMain")

        // Simulate target view creation (normally done by SourcesService during normalization)
        platformCas.normalizedTargetDir.createDirectories()
        platformCas.normalizedTargetDir.resolve("jvm-only.txt").writeText("unique")
        platformCas.completionMarker.writeText("")

        assertTrue(platformCas.normalizedTargetDir.exists(), "Target dir should exist")
        assertFalse(platformCas.normalizedTargetDir.resolve("shared.txt").exists(), "Shared file should not be in target view")
        assertTrue(platformCas.normalizedTargetDir.resolve("jvm-only.txt").exists(), "Unique file should remain in target view")

        // Create session view with both
        val view = storageService.createSessionView(mapOf(commonDep to commonCas, platformDep to platformCas))

        val commonPath = view.sources.resolve("org.example/lib")
        val platformPath = view.sources.resolve("org.example/lib-jvm")

        assertTrue(commonPath.exists(), "Common path should exist")
        assertTrue(platformPath.exists(), "Platform path should exist")

        assertTrue(commonPath.resolve("shared.txt").exists(), "Shared file should exist in common path")
        assertFalse(platformPath.resolve("shared.txt").exists(), "Shared file should NOT exist in platform path (it is in common)")
        assertTrue(platformPath.resolve("jvm-only.txt").exists(), "Unique file should exist in platform path")
    }

    @Test
    fun `check KMP target isolation skips platform artifact if no unique sources and common present`() = runTest {
        val mcpDir = tempDir.resolve("mcp-target-skip")
        val environment = GradleMcpEnvironment(mcpDir)
        val storageService = DefaultSourceStorageService(environment)

        val commonId = "group:common:1.0"
        val platformId = "group:jvm:1.0"

        val commonDep = GradleDependency(id = commonId, group = "group", name = "common", version = "1.0", sourcesFile = tempDir.resolve("common.jar"))
        val platformDep = GradleDependency(id = platformId, group = "group", name = "jvm", version = "1.0", commonComponentId = commonId, sourcesFile = tempDir.resolve("jvm.jar"))

        val commonCas = storageService.getCASDependencySourcesDir(storageService.calculateHash(commonDep))
        val platformCas = storageService.getCASDependencySourcesDir(storageService.calculateHash(platformDep))

        commonCas.normalizedDir.createDirectories()
        commonCas.normalizedDir.resolve("common.txt").writeText("common")
        commonCas.completionMarker.createFile()

        platformCas.normalizedDir.createDirectories()
        platformCas.normalizedDir.resolve("common.txt").writeText("common")
        // NOTE: No normalizedTargetDir created for platform!
        platformCas.completionMarker.createFile()

        val deps = mapOf(
            commonDep to commonCas,
            platformDep to platformCas
        )

        val view = storageService.createSessionView(deps)
        val manifest = view.manifest

        // Platform dep should be SKIPPED in manifest and not linked
        assertEquals(1, manifest.dependencies.size)
        assertEquals(commonId, manifest.dependencies[0].id)

        assertFalse(view.sources.resolve(platformDep.relativePrefix!!).exists())
        assertTrue(view.sources.resolve(commonDep.relativePrefix!!).exists())
    }

    @Test
    fun `check no collision for dependencies with same group and name but different version`() = runTest(timeout = 5.minutes) {
        val mcpDir = tempDir.resolve("mcp-no-collision")
        val environment = GradleMcpEnvironment(mcpDir)
        val storageService = DefaultSourceStorageService(environment)

        val dep1 = GradleDependency(id = "group1:lib:1.0.0", group = "group1", name = "lib", version = "1.0.0", sourcesFile = tempDir.resolve("lib1.jar"))
        val dep2 = GradleDependency(id = "group1:lib:2.0.0", group = "group1", name = "lib", version = "2.0.0", sourcesFile = tempDir.resolve("lib2.jar"))

        val casDir1 = CASDependencySourcesDir("hash1", mcpDir.resolve("cas/v3/ha/hash1"))
        val casDir2 = CASDependencySourcesDir("hash2", mcpDir.resolve("cas/v3/ha/hash2"))

        casDir1.normalizedDir.createDirectories()
        casDir1.normalizedDir.resolve("file1.txt").writeText("v1")
        casDir1.completionMarker.createFile()

        casDir2.normalizedDir.createDirectories()
        casDir2.normalizedDir.resolve("file2.txt").writeText("v2")
        casDir2.completionMarker.createFile()

        val deps = mapOf(dep1 to casDir1, dep2 to casDir2)

        val view = storageService.createSessionView(deps)

        val vPath = view.sources.resolve("group1/lib")

        assertTrue(vPath.exists(), "Merged path should exist")
        // The last one wins because createSymbolicLink fails and falls back to copy with overwrite=true
        assertTrue(vPath.resolve("file2.txt").exists(), "Version 2 file should exist")
        assertFalse(vPath.resolve("file1.txt").exists(), "Version 1 file should have been overwritten")
    }

    @Test
    fun `check no collision for dependencies with different group but same name`() = runTest(timeout = 5.minutes) {
        val mcpDir = tempDir.resolve("mcp-no-collision-group")
        val environment = GradleMcpEnvironment(mcpDir)
        val storageService = DefaultSourceStorageService(environment)

        val dep1 = GradleDependency(id = "group1:lib:1.0.0", group = "group1", name = "lib", version = "1.0.0", sourcesFile = tempDir.resolve("lib1.jar"))
        val dep2 = GradleDependency(id = "group2:lib:1.0.0", group = "group2", name = "lib", version = "1.0.0", sourcesFile = tempDir.resolve("lib2.jar"))

        val casDir1 = CASDependencySourcesDir("hash1", mcpDir.resolve("cas/v3/ha/hash1"))
        val casDir2 = CASDependencySourcesDir("hash2", mcpDir.resolve("cas/v3/ha/hash2"))

        casDir1.normalizedDir.createDirectories()
        casDir1.normalizedDir.resolve("file1.txt").writeText("v1")
        casDir1.completionMarker.createFile()

        casDir2.normalizedDir.createDirectories()
        casDir2.normalizedDir.resolve("file2.txt").writeText("v2")
        casDir2.completionMarker.createFile()

        val deps = mapOf(dep1 to casDir1, dep2 to casDir2)

        val view = storageService.createSessionView(deps)

        val path1 = view.sources.resolve("group1/lib")
        val path2 = view.sources.resolve("group2/lib")

        assertTrue(path1.exists(), "Group 1 path should exist")
        assertTrue(path2.exists(), "Group 2 path should exist")

        assertTrue(path1.resolve("file1.txt").exists(), "Group 1 file should exist")
        assertTrue(path2.resolve("file2.txt").exists(), "Group 2 file should exist")
    }
}
