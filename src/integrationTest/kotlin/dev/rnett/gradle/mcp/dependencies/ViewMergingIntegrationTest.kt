package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.SessionViewSourcesDir
import dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

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
        val indexService = DefaultSourceIndexService(DefaultIndexService(environment)) // Simplified for this test
        val sourcesService = DefaultSourcesService(depService, storageService, dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService(environment))

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
                sourcesService.resolveAndProcessProjectSources(projectRoot, ":", providerToIndex = FullTextSearch)
            }

            assertNotNull(sourcesDir)
            assertTrue(sourcesDir is SessionViewSourcesDir)

            val sessionView = sourcesDir as SessionViewSourcesDir
            val depsDir = sessionView.sources

            // ... (existing logging)

            // Check for coroutines dependencies
            val coroutinesEntries = depsDir.resolve("deps").listDirectoryEntries("kotlinx-coroutines-core*")
            assertTrue(coroutinesEntries.isNotEmpty(), "Should find coroutines entries")

            // ...

            // Perform a search to see how results from different variants are reported
            // 'Job' is a good candidate as it's defined in common.
            val searchResponse = indexService.search(sessionView, FullTextSearch, "interface Job", dev.rnett.gradle.mcp.tools.PaginationInput(0, 10))
            println("Search results for 'interface Job':")
            searchResponse.results.forEach { println("  ${it.relativePath}") }

            // 'Dispatchers' might have platform-specific parts
            val searchResponse2 = indexService.search(sessionView, FullTextSearch, "object Dispatchers", dev.rnett.gradle.mcp.tools.PaginationInput(0, 10))
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
    fun `check collision for dependencies with same name and version but different group`() = runTest(timeout = 5.minutes) {
        val mcpDir = tempDir.resolve("mcp-collision")
        val environment = GradleMcpEnvironment(mcpDir)
        val storageService = DefaultSourceStorageService(environment)

        val dep1 = GradleDependency(
            id = "group1:lib:1.0",
            group = "group1",
            name = "lib",
            version = "1.0",
            sourcesFile = tempDir.resolve("lib1.jar").apply { writeText("content1") }
        )
        val dep2 = GradleDependency(
            id = "group2:lib:1.0",
            group = "group2",
            name = "lib",
            version = "1.0",
            sourcesFile = tempDir.resolve("lib2.jar").apply { writeText("content2") }
        )

        val casDir1 = storageService.getCASDependencySourcesDir(storageService.calculateHash(dep1))
        val casDir2 = storageService.getCASDependencySourcesDir(storageService.calculateHash(dep2))

        // Ensure CAS directories exist
        casDir1.sources.createDirectories()
        casDir1.sources.resolve("file1.txt").writeText("source1")
        casDir2.sources.createDirectories()
        casDir2.sources.resolve("file2.txt").writeText("source2")

        val deps = mapOf(dep1 to casDir1, dep2 to casDir2)

        val view = storageService.createSessionView(deps)

        val commonPath = view.sources.resolve("deps/lib/1.0")
        assertTrue(commonPath.exists(), "Collision path should exist")

        println("Collision path: ${commonPath.toAbsolutePath()}")
        val file1Path = commonPath.resolve("file1.txt")
        val file2Path = commonPath.resolve("file2.txt")

        println("File 1 exists in view: ${file1Path.exists()}")
        println("File 2 exists in view: ${file2Path.exists()}")

        if (file2Path.exists() && !file1Path.exists()) {
            println("Collision confirmed: Second dependency overwrote the first one.")
        }

        // Verify that all indices are searchable even if sources collide
        casDir1.index.resolve("test-provider").createDirectories()
        casDir2.index.resolve("test-provider").createDirectories()

        val indices = view.resolveIndexDirs("test-provider")
        println("Indices found: ${indices.size}")
        indices.forEach { println("  Index: ${it.toAbsolutePath()}") }

        assertTrue(indices.size >= 2, "Should find at least 2 indices (might find more from other tests if not cleaned up, but here we use a fresh temp dir)")
    }
}
