package dev.rnett.gradle.mcp.fixtures.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.test.assertTrue

abstract class SearchIntegrationTestBase {

    @TempDir
    lateinit var tempDir: Path

    lateinit var environment: GradleMcpEnvironment
    lateinit var dependencyService: GradleDependencyService
    lateinit var indexService: IndexService
    lateinit var storageService: dev.rnett.gradle.mcp.dependencies.SourceStorageService
    lateinit var sourceIndexService: dev.rnett.gradle.mcp.dependencies.SourceIndexService
    lateinit var sourcesService: DefaultSourcesService
    val projectRoot: GradleProjectRoot get() = GradleProjectRoot(tempDir.resolve("project").toString())

    abstract val searchProvider: SearchProvider

    @BeforeEach
    fun setupBase() {
        tempDir.resolve("project").createDirectories()
        environment = GradleMcpEnvironment(tempDir.resolve("mcp"))
        dependencyService = mockk()
        indexService = dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService(environment)
        storageService = dev.rnett.gradle.mcp.dependencies.DefaultSourceStorageService(environment)
        sourceIndexService = dev.rnett.gradle.mcp.dependencies.DefaultSourceIndexService(indexService, storageService)
        sourcesService = dev.rnett.gradle.mcp.dependencies.DefaultSourcesService(dependencyService, storageService, sourceIndexService)
    }

    protected fun createSourceZip(name: String, content: Map<String, String>): Path {
        val zipFile = tempDir.resolve("${name}.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            for ((path, data) in content) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(data.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return zipFile
    }

    protected fun mockDependencyReport(vararg dependencies: GradleDependency) {
        val report = GradleDependencyReport(
            listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "compileClasspath",
                            description = null,
                            isResolvable = true,
                            dependencies = dependencies.toList()
                        )
                    )
                )
            )
        )
        coEvery { with(any<ProgressReporter>()) { dependencyService.downloadAllSources(projectRoot) } } returns report
    }

    @Test
    open fun `test that search fails if indexing was disabled`() = runTest {
        val zip = createSourceZip(
            "test-sources", mapOf(
                "com/example/MyClass.kt" to "class MyClass"
            )
        )

        mockDependencyReport(
            GradleDependency(
                id = "com.example:test:1.0",
                group = "com.example",
                name = "test",
                version = "1.0",
                sourcesFile = zip
            )
        )

        val sourcesDir = with(ProgressReporter.NONE) {
            sourcesService.resolveAndProcessAllSources(projectRoot, index = false)
        }

        val response = sourceIndexService.search(sourcesDir, searchProvider, "MyClass")
        assertTrue(response.error != null, "Search should have failed with an error when indexing is disabled")
        assertTrue(
            response.error!!.contains("Lucene index directory does not exist") ||
                    response.error!!.contains("Symbol index dir does not exist") ||
                    response.error!!.contains("Index for provider") ||
                    response.error!!.contains("Index not found"),
            "Error message should mention missing index: ${response.error}"
        )
    }

    @Test
    fun `test that only source files are indexed`() = runTest {
        val zip = createSourceZip(
            "test-sources", mapOf(
                "com/example/MyClass.kt" to "class MyClass",
                "com/example/config.xml" to "<config></config>",
                "com/example/readme.txt" to "This is a readme",
                "com/example/build.gradle" to "apply plugin: 'java'"
            )
        )

        mockDependencyReport(
            GradleDependency(
                id = "com.example:test:1.0",
                group = "com.example",
                name = "test",
                version = "1.0",
                sourcesFile = zip
            )
        )

        val sourcesDir = with(ProgressReporter.NONE) {
            sourcesService.resolveAndProcessAllSources(projectRoot, index = true, providerToIndex = searchProvider)
        }

        // MyClass should be found (it's .kt)
        val resultsKt = sourceIndexService.search(sourcesDir, searchProvider, "MyClass")
        assertTrue(resultsKt.results.isNotEmpty(), "Kotlin file should be indexed")

        // Others should NOT be found
        val resultsXml = sourceIndexService.search(sourcesDir, searchProvider, "config")
        assertTrue(resultsXml.results.isEmpty(), "XML file should NOT be indexed, but found: ${resultsXml.results}")

        val resultsTxt = sourceIndexService.search(sourcesDir, searchProvider, "readme")
        assertTrue(resultsTxt.results.isEmpty(), "TXT file should NOT be indexed, but found: ${resultsTxt.results}")

        val resultsGradle = sourceIndexService.search(sourcesDir, searchProvider, "plugin")
        assertTrue(resultsGradle.results.isEmpty(), "Gradle file should NOT be indexed, but found: ${resultsGradle.results}")
    }
}
