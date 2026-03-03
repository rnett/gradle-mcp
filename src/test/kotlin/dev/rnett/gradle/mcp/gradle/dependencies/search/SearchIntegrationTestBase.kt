package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.gradle.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleProjectDependencies
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

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
        coEvery { dependencyService.downloadAllSources(projectRoot) } returns report
    }
}