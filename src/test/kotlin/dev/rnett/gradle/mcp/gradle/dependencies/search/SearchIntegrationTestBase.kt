package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class SearchIntegrationTestBase {

    @TempDir
    lateinit var tempDir: Path

    lateinit var environment: GradleMcpEnvironment
    lateinit var dependencyService: GradleDependencyService
    lateinit var indexService: IndexService
    lateinit var sourcesService: DefaultSourcesService
    val projectRoot: GradleProjectRoot get() = GradleProjectRoot(tempDir.resolve("project").toString())

    abstract val searchProvider: SearchProvider

    @BeforeEach
    fun setupBase() {
        tempDir.resolve("project").createDirectories()
        environment = GradleMcpEnvironment(tempDir.resolve("mcp"))
        dependencyService = mockk()
        indexService = DefaultIndexService(environment)
        sourcesService = DefaultSourcesService(dependencyService, environment, indexService)
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
    fun `test that search fails if indexing was disabled`() = kotlinx.coroutines.test.runTest {
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
            sourcesService.downloadAllSources(projectRoot, index = false)
        }

        assertFailsWith<IllegalStateException> {
            sourcesService.search(sourcesDir, searchProvider, "MyClass")
        }
    }

    @Test
    fun `test that only source files are indexed`() = kotlinx.coroutines.test.runTest {
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
            sourcesService.downloadAllSources(projectRoot, index = true)
        }

        // MyClass should be found (it's .kt)
        val resultsKt = sourcesService.search(sourcesDir, searchProvider, "MyClass")
        assertTrue(resultsKt.results.isNotEmpty(), "Kotlin file should be indexed")

        // Others should NOT be found
        val resultsXml = sourcesService.search(sourcesDir, searchProvider, "config")
        assertTrue(resultsXml.results.isEmpty(), "XML file should NOT be indexed, but found: ${resultsXml.results}")

        val resultsTxt = sourcesService.search(sourcesDir, searchProvider, "readme")
        assertTrue(resultsTxt.results.isEmpty(), "TXT file should NOT be indexed, but found: ${resultsTxt.results}")

        val resultsGradle = sourcesService.search(sourcesDir, searchProvider, "plugin")
        assertTrue(resultsGradle.results.isEmpty(), "Gradle file should NOT be indexed, but found: ${resultsGradle.results}")
    }
}
