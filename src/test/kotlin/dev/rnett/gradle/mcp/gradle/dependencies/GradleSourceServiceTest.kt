package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.search.DefaultIndexService
import io.ktor.client.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class GradleSourceServiceTest {

    private lateinit var environment: GradleMcpEnvironment
    private lateinit var tempDir: Path
    private lateinit var gradleSourceService: GradleSourceService
    private lateinit var httpClient: HttpClient

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("gradle-mcp-test-")
        environment = GradleMcpEnvironment(tempDir.resolve("workingDir"))
        httpClient = DI.createHttpClient()
        val indexService = DefaultIndexService(environment)
        gradleSourceService = DefaultGradleSourceService(environment, indexService, httpClient)
    }

    @AfterEach
    fun cleanup() {
        httpClient.close()
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `can download, extract, and index gradle sources`() = runTest(timeout = kotlin.time.Duration.parse("5m")) {
        // Create a dummy project root to get version from
        val projectRoot = tempDir.resolve("dummy-project")
        projectRoot.createDirectories()
        val wrapperDir = projectRoot.resolve("gradle/wrapper")
        wrapperDir.createDirectories()
        val propsFile = wrapperDir.resolve("gradle-wrapper.properties")

        val version = "8.5" // Use a stable version for testing
        val props = Properties()
        props.setProperty("distributionUrl", "https://services.gradle.org/distributions/gradle-$version-bin.zip")
        propsFile.outputStream().use { props.store(it, null) }

        // Add gradlew to satisfy isGradleRootProjectDir
        projectRoot.resolve("gradlew").createFile()

        val root = GradleProjectRoot(projectRoot.toString())

        val sources = gradleSourceService.getGradleSources(root)

        assertTrue(sources.sources.exists(), "Sources directory should exist")
        assertTrue(sources.sources.resolve("subprojects").exists(), "Extracted sources should contain 'subprojects' (pre-9.0 structure)")

        val readme = sources.sources.resolve("gradlew")
        assertTrue(readme.exists(), "gradlew should exist")

        assertTrue(sources.index.exists(), "Index directory should exist")

        // Verify exclusions
        val buildLogic = sources.sources.resolve("build-logic")
        if (buildLogic.exists()) {
            val buildLogicEntries = buildLogic.listDirectoryEntries()
            assertTrue(buildLogicEntries.isEmpty(), "build-logic should be empty or not exist (deleted recursively)")
        }

        // Check for non-main sources in some subproject
        // In 8.5, subprojects are under 'subprojects/'
        val subprojects = sources.sources.resolve("subprojects")
        if (subprojects.exists()) {
            Files.walkFileTree(subprojects, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir.parent?.name == "src") {
                        assertEquals(dir.name, "main", "Source directory ${sources.sources.relativize(dir)} should be 'main'")
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }
}
